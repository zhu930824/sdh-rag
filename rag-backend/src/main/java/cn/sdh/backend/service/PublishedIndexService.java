package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.dto.IndexPublicationSnapshot;
import cn.sdh.backend.job.DocumentIndexJobPayload;
import cn.sdh.backend.job.PlatformJobExecutionContext;
import cn.sdh.backend.utils.ContentFingerprint;
import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.json.JsonData;
import com.alibaba.fastjson2.JSON;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;

/** Publication authority. A staging generation is never admitted into a read view. */
@Service
public class PublishedIndexService {
    public record Generation(String key, Long documentId, int revision, boolean legacy, String sourceSha256) {}
    public record Stage(String key, boolean published, int chunkCount) {}
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ThreadLocal<Deque<ReadView>> views = ThreadLocal.withInitial(ArrayDeque::new);
    public PublishedIndexService(JdbcTemplate jdbc, PlatformTransactionManager manager) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager);
    }
    public Stage stage(DocumentIndexJobPayload payload, Long jobId, String sourceSha256, PlatformJobExecutionContext context) {
        if (sourceSha256 == null || !sourceSha256.matches("[a-f0-9]{64}")) throw new BusinessException(400, "索引内容指纹无效");
        return transactions.execute(status -> {
            checkpoint(context, jobId);
            var current = requireCurrent(payload, jobId);
            if (current.get("index_source_sha256") != null && !sourceSha256.equals(current.get("index_source_sha256"))) throw new BusinessException(409, "同一索引代际的源内容已变化，请创建新的索引任务");
            if (current.get("published_generation") != null) {
                var published = jdbc.queryForList("SELECT generation_key,index_revision,chunk_count FROM knowledge_index_generation WHERE generation_key=? FOR UPDATE", current.get("published_generation"));
                if (!published.isEmpty() && ((Number)published.getFirst().get("index_revision")).intValue() == payload.revision()) {
                    var row = published.getFirst(); return new Stage(row.get("generation_key").toString(), true, ((Number)row.get("chunk_count")).intValue());
                }
            }
            String key = "g:" + payload.relationId() + ":" + payload.revision() + ":" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
            jdbc.update("INSERT INTO knowledge_index_generation (generation_key,relation_id,knowledge_id,document_id,index_revision,source_sha256,state) VALUES (?,?,?,?,?,?,'STAGING')",
                    key, payload.relationId(), payload.knowledgeId(), payload.documentId(), payload.revision(), sourceSha256);
            jdbc.update("UPDATE knowledge_document_relation SET index_write_generation=?,index_source_sha256=? WHERE id=?", key, sourceSha256, payload.relationId());
            return new Stage(key, false, 0);
        });
    }
    public boolean publish(DocumentIndexJobPayload payload, Long jobId, String generation, int chunks, PlatformJobExecutionContext context) {
        if (chunks < 0) throw new IllegalArgumentException("Negative chunk count");
        return Boolean.TRUE.equals(transactions.execute(status -> {
            checkpoint(context, jobId);
            var current = requireCurrent(payload, jobId);
            if (!Objects.equals(current.get("index_write_generation"), generation)) throw new BusinessException(409, "索引写入批次已被接管，不能发布");
            var rows = jdbc.queryForList("SELECT state,chunk_count FROM knowledge_index_generation WHERE generation_key=? AND relation_id=? AND index_revision=? FOR UPDATE", generation, payload.relationId(), payload.revision());
            if (rows.isEmpty()) throw new BusinessException(409, "索引代际尚未登记");
            var row = rows.getFirst();
            if ("PUBLISHED".equals(row.get("state")) && ((Number)row.get("chunk_count")).intValue() != chunks) throw new BusinessException(409, "不能覆盖已发布代际");
            jdbc.update("UPDATE knowledge_index_generation SET state='PUBLISHED',chunk_count=?,published_at=COALESCE(published_at,?) WHERE generation_key=?", chunks, LocalDateTime.now(), generation);
            if (jdbc.update("UPDATE knowledge_document_relation SET published_generation=?,process_status=2,chunk_count=?,error_message=NULL,process_time=? WHERE id=? AND index_revision=? AND job_id=?",
                    generation, chunks, LocalDateTime.now(), payload.relationId(), payload.revision(), jobId) != 1) throw new BusinessException(409, "发布指针已变化");
            return true;
        }));
    }
    private void checkpoint(PlatformJobExecutionContext context, Long jobId) {
        if (!Objects.equals(jobId, context.jobId())) throw new BusinessException(409, "发布任务上下文不匹配");
        context.checkpoint(); // The heartbeat UPDATE holds the job owner fence until this transaction commits.
    }
    private Map<String,Object> requireCurrent(DocumentIndexJobPayload payload, Long jobId) {
        var rows = jdbc.queryForList("SELECT published_generation,index_write_generation,index_source_sha256 FROM knowledge_document_relation WHERE id=? AND knowledge_id=? AND document_id=? AND index_revision=? AND job_id=? FOR UPDATE",
                payload.relationId(), payload.knowledgeId(), payload.documentId(), payload.revision(), jobId);
        if (rows.isEmpty()) throw new BusinessException(409, "索引任务已过期，不能发布");
        return rows.getFirst();
    }
    public void summaryReady(String generation) {
        jdbc.update("UPDATE knowledge_index_generation SET summary_ready=TRUE WHERE generation_key=? AND state='PUBLISHED'", generation);
    }

    public ReadView open(Long knowledgeId) {
        if (knowledgeId == null || knowledgeId <= 0) throw new BusinessException(400, "知识库 ID 无效");
        for (var view : views.get()) if (view.knowledgeId.equals(knowledgeId)) return new ReadView(view);
        List<Boolean> readiness = new ArrayList<>();
        var generations = jdbc.query("SELECT g.* FROM knowledge_document_relation r JOIN knowledge_index_generation g ON r.published_generation=g.generation_key WHERE r.knowledge_id=? AND g.state='PUBLISHED' ORDER BY g.generation_key",
                (rs, n) -> {
                    readiness.add(rs.getBoolean("summary_ready"));
                    return new Generation(rs.getString("generation_key"), rs.getLong("document_id"), rs.getInt("index_revision"), rs.getBoolean("legacy_vectors"), rs.getString("source_sha256"));
                }, knowledgeId);
        var manifest = new TreeMap<String,Object>(); manifest.put("knowledgeId", knowledgeId); manifest.put("generations", generations);
        String json = JSON.toJSONString(manifest), version = ContentFingerprint.sha256(json);
        try { jdbc.update("INSERT INTO knowledge_index_read_snapshot (snapshot_sha256,knowledge_id,manifest_json,created_at) VALUES (?,?,?,?)", version, knowledgeId, json, LocalDateTime.now()); }
        catch (DuplicateKeyException duplicate) { /* The manifest is content addressed and immutable. */ }
        var view = new ReadView(knowledgeId, List.copyOf(generations), version, !generations.isEmpty() && readiness.stream().allMatch(Boolean::booleanValue));
        views.get().push(view); return view;
    }
    public ReadView current(Long knowledgeId) {
        for (var view : views.get()) if (view.knowledgeId.equals(knowledgeId)) return view;
        throw new IllegalStateException("检索必须在已发布索引读视图内执行");
    }
    public final class ReadView implements AutoCloseable {
        private final Long knowledgeId;
        private final List<Generation> generations;
        private final IndexPublicationSnapshot snapshot;
        private final boolean borrowed;
        private boolean closed;
        private ReadView(Long knowledgeId, List<Generation> generations, String version, boolean summariesComplete) {
            this.knowledgeId = knowledgeId; this.generations = generations; this.borrowed = false;
            this.snapshot = new IndexPublicationSnapshot(version, generations.size(), (int)generations.stream().filter(Generation::legacy).count(), summariesComplete);
        }
        private ReadView(ReadView parent) { this.knowledgeId = parent.knowledgeId; this.generations = parent.generations; this.snapshot = parent.snapshot; this.borrowed = true; }
        public IndexPublicationSnapshot snapshot() { return snapshot; }
        public Query filter(String documentField) {
            if (closed) throw new IllegalStateException("索引读视图已关闭");
            if (generations.isEmpty()) return Query.of(q -> q.matchNone(m -> m));
            List<Query> alternatives = new ArrayList<>();
            var keys = generations.stream().filter(g -> !g.legacy()).map(Generation::key).toList();
            for (int start = 0; start < keys.size(); start += 60000) {
                var part = keys.subList(start, Math.min(start + 60000, keys.size())).stream().map(FieldValue::of).toList();
                alternatives.add(Query.of(q -> q.terms(t -> t.field("index_generation").terms(v -> v.value(part)))));
            }
            if ("document_id".equals(documentField)) {
                Map<String,Integer> legacy = new TreeMap<>();
                generations.stream().filter(Generation::legacy).forEach(g -> legacy.put(g.documentId().toString(), g.revision()));
                if (!legacy.isEmpty()) alternatives.add(Query.of(q -> q.bool(b -> b.mustNot(m -> m.exists(e -> e.field("index_generation")))
                        .filter(f -> f.script(s -> s.script(script -> script.lang("painless").source(LEGACY_FILTER).params("legacy", JsonData.of(legacy))))))));
            }
            return alternatives.isEmpty() ? Query.of(q -> q.matchNone(m -> m)) : Query.of(q -> q.bool(b -> b.should(alternatives).minimumShouldMatch("1")));
        }
        @Override public void close() {
            if (closed) return; closed = true;
            if (!borrowed) { views.get().remove(this); if (views.get().isEmpty()) views.remove(); }
        }
    }
    private static final String LEGACY_FILTER = """
        if (!doc.containsKey('document_id') || doc['document_id'].size()==0) return false;
        def expected = params.legacy.get(doc['document_id'].value.toString());
        if (expected == null) return false;
        long revision = doc.containsKey('index_revision') && doc['index_revision'].size()!=0 ? doc['index_revision'].value : 0;
        return revision == expected.longValue();
        """;
}
