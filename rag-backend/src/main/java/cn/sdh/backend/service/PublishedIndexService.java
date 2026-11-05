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
    private final IndexGenerationLifecycle lifecycle;
    private final IndexPublicationGateStore gates;
    private final ThreadLocal<Deque<ReadView>> views = ThreadLocal.withInitial(ArrayDeque::new);
    public PublishedIndexService(JdbcTemplate jdbc, PlatformTransactionManager manager, IndexGenerationLifecycle lifecycle,IndexPublicationGateStore gates) {
        this.jdbc = jdbc; this.transactions = new TransactionTemplate(manager);
        this.lifecycle=lifecycle;
        this.gates=gates;
    }
    public Stage stage(DocumentIndexJobPayload payload, Long jobId, String sourceSha256, PlatformJobExecutionContext context) {
        return stage(payload,jobId,sourceSha256,null,context);
    }
    public boolean requiresGate(Long documentId) { return gates.managed(documentId); }
    public Stage stage(DocumentIndexJobPayload payload, Long jobId, String sourceSha256,String rawSourceSha256, PlatformJobExecutionContext context) {
        if (sourceSha256 == null || !sourceSha256.matches("[a-f0-9]{64}")) throw new BusinessException(400, "索引内容指纹无效");
        return transactions.execute(status -> {
            lifecycle.lockInTransaction(payload.knowledgeId());
            checkpoint(context, jobId);
            var current = requireCurrent(payload, jobId);
            var policy=gates.verifySource(payload,rawSourceSha256);
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
            gates.freeze(key,policy);
            return new Stage(key, false, 0);
        });
    }
    public boolean publish(DocumentIndexJobPayload payload, Long jobId, String generation, int chunks, PlatformJobExecutionContext context) {
        if (chunks < 0) throw new IllegalArgumentException("Negative chunk count");
        return Boolean.TRUE.equals(transactions.execute(status -> {
            lifecycle.lockInTransaction(payload.knowledgeId());
            checkpoint(context, jobId);
            var current = requireCurrent(payload, jobId);
            if (!Objects.equals(current.get("index_write_generation"), generation)) throw new BusinessException(409, "索引写入批次已被接管，不能发布");
            var rows = jdbc.queryForList("SELECT state,chunk_count FROM knowledge_index_generation WHERE generation_key=? AND relation_id=? AND index_revision=? FOR UPDATE", generation, payload.relationId(), payload.revision());
            if (rows.isEmpty()) throw new BusinessException(409, "索引代际尚未登记");
            var row = rows.getFirst();
            if (!Set.of("STAGING","PUBLISHED").contains(row.get("state"))) throw new BusinessException(409,"索引批次已进入回收，不能重新发布");
            if ("PUBLISHED".equals(row.get("state")) && ((Number)row.get("chunk_count")).intValue() != chunks) throw new BusinessException(409, "不能覆盖已发布代际");
            gates.authorize(payload,generation,() -> headVersion(payload.knowledgeId(),true),Objects.equals(current.get("published_generation"),generation));
            jdbc.update("UPDATE knowledge_index_generation SET state='PUBLISHED',chunk_count=?,published_at=COALESCE(published_at,?) WHERE generation_key=?", chunks, LocalDateTime.now(), generation);
            if (jdbc.update("UPDATE knowledge_document_relation SET published_generation=?,process_status=2,chunk_count=?,error_message=NULL,process_time=? WHERE id=? AND index_revision=? AND job_id=?",
                    generation, chunks, LocalDateTime.now(), payload.relationId(), payload.revision(), jobId) != 1) throw new BusinessException(409, "发布指针已变化");
            gates.published(payload,generation);
            return true;
        }));
    }
    public void saveGateEvaluation(DocumentIndexJobPayload payload,Long jobId,String generation,String basis,String candidate,KnowledgePublishingService.ValidationResult result,PlatformJobExecutionContext context) {
        transactions.executeWithoutResult(status -> {
            lifecycle.lockInTransaction(payload.knowledgeId()); checkpoint(context,jobId);
            requireWriter(payload,jobId,generation);
            gates.record(payload,generation,basis,candidate,result);
        });
    }
    public void fail(DocumentIndexJobPayload payload,Long jobId,String generation,String error,PlatformJobExecutionContext context) {
        transactions.executeWithoutResult(status -> {
            lifecycle.lockInTransaction(payload.knowledgeId()); checkpoint(context,jobId);
            var current=requireCurrent(payload,jobId);
            if(generation!=null && !Objects.equals(current.get("index_write_generation"),generation)) throw new BusinessException(409,"失败回写已被新批次接管");
            jdbc.update("UPDATE knowledge_document_relation SET process_status=3,error_message=?,process_time=CURRENT_TIMESTAMP WHERE id=?",error,payload.relationId());
            gates.failed(payload,error);
        });
    }
    private void requireWriter(DocumentIndexJobPayload payload,Long jobId,String generation) {
        var current=requireCurrent(payload,jobId);
        if(!Objects.equals(current.get("index_write_generation"),generation)) throw new BusinessException(409,"索引批次已被接管");
        if(!"STAGING".equals(jdbc.queryForObject("SELECT state FROM knowledge_index_generation WHERE generation_key=? FOR UPDATE",String.class,generation))) throw new BusinessException(409,"候选批次不在暂存状态");
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
        var view=lifecycle.readGuard(knowledgeId,() -> openUnderGuard(knowledgeId));
        views.get().push(view); return view;
    }
    public ReadView openEvaluation(Long knowledgeId) {
        if(knowledgeId==null || knowledgeId<=0 || views.get().stream().anyMatch(v -> v.knowledgeId.equals(knowledgeId))) throw new BusinessException(409,"评测必须建立独立的已发布版本作用域");
        var view=lifecycle.readGuard(knowledgeId,() -> openUnderGuard(knowledgeId,null,true));
        views.get().push(view); return view;
    }
    public ReadView openHistorical(Long knowledgeId,String version) {
        if(views.get().stream().anyMatch(v -> v.knowledgeId.equals(knowledgeId))) throw new BusinessException(409,"历史重放必须使用独立读作用域");
        var view=lifecycle.readGuard(knowledgeId,() -> {
            var historical=historical(knowledgeId,version);
            String lease=lifecycle.pin(knowledgeId,version,historical.generations(),3600);
            return new ReadView(knowledgeId,historical.generations(),version,historical.summariesComplete(),lease,version);
        });
        views.get().push(view); return view;
    }
    /** Queue reservation and task creation commit under the same KB guard as cleanup admission. */
    public <T> T reserveHistorical(Long knowledgeId,String version,String holdId,java.util.function.Supplier<T> createTask) {
        return lifecycle.readGuard(knowledgeId,() -> {
            var historical=historical(knowledgeId,version);
            lifecycle.hold(knowledgeId,version,historical.generations(),holdId);
            return createTask.get();
        });
    }
    private Heads historical(Long knowledgeId,String version) {
        if(knowledgeId==null || knowledgeId<=0 || version==null || !version.matches("[a-f0-9]{64}")) throw new BusinessException(400,"历史语料版本无效");
        var rows=jdbc.queryForList("SELECT manifest_json FROM knowledge_index_read_snapshot WHERE knowledge_id=? AND snapshot_sha256=?",knowledgeId,version);
        if(rows.isEmpty()) throw unavailableHistory();
        String json=(String)rows.getFirst().get("manifest_json");
        if(!version.equals(ContentFingerprint.sha256(json))) throw unavailableHistory();
        var manifest=JSON.parseObject(json);
        if(!knowledgeId.equals(manifest.getLong("knowledgeId"))) throw unavailableHistory();
        var generations=manifest.getList("generations",Generation.class);
        if(generations==null || generations.stream().map(Generation::key).distinct().count()!=generations.size()) throw unavailableHistory();
        boolean ready=!generations.isEmpty();
        for(var generation:generations) ready=validateHistoricalGeneration(knowledgeId,generation) && ready;
        return new Heads(List.copyOf(generations),ready);
    }
    private boolean validateHistoricalGeneration(Long knowledgeId,Generation expected) {
        if(expected.legacy() || expected.sourceSha256()==null || !expected.sourceSha256().matches("[a-f0-9]{64}")) throw unavailableHistory();
        var rows=jdbc.queryForList("SELECT g.* FROM knowledge_index_generation g JOIN knowledge_document_relation r ON r.id=g.relation_id AND r.knowledge_id=g.knowledge_id AND r.document_id=g.document_id WHERE g.generation_key=? AND g.knowledge_id=? AND g.state='PUBLISHED' AND NOT EXISTS (SELECT 1 FROM knowledge_draft d WHERE d.published_document_id=g.document_id AND (d.knowledge_id<>g.knowledge_id OR d.status='ROLLED_BACK'))",expected.key(),knowledgeId);
        if(rows.size()!=1) throw unavailableHistory();
        var row=rows.getFirst();
        if(!Objects.equals(expected.documentId(),((Number)row.get("document_id")).longValue()) || expected.revision()!=((Number)row.get("index_revision")).intValue()
                || !Objects.equals(expected.sourceSha256(),row.get("source_sha256")) || Boolean.TRUE.equals(row.get("legacy_vectors"))) throw unavailableHistory();
        return Boolean.TRUE.equals(row.get("summary_ready"));
    }
    private BusinessException unavailableHistory() { return new BusinessException(409,"历史语料已回收、撤回或缺少完整版本证据，不能重放"); }
    public ReadView openCandidate(DocumentIndexJobPayload payload,Long jobId,String generation,PlatformJobExecutionContext context) {
        if(views.get().stream().anyMatch(v -> v.knowledgeId.equals(payload.knowledgeId()))) throw new BusinessException(409,"候选验证必须使用独立读作用域");
        var view=lifecycle.readGuard(payload.knowledgeId(),() -> {
            checkpoint(context,jobId); requireWriter(payload,jobId,generation);
            if(gates.frozen(generation)==null) throw new BusinessException(409,"候选批次没有冻结门禁策略");
            var candidate=jdbc.queryForObject("SELECT * FROM knowledge_index_generation WHERE generation_key=?",(rs,n) -> new Generation(generation,rs.getLong("document_id"),rs.getInt("index_revision"),false,rs.getString("source_sha256")),generation);
            return openUnderGuard(payload.knowledgeId(),candidate);
        });
        views.get().push(view); return view;
    }
    private ReadView openUnderGuard(Long knowledgeId) {
        return openUnderGuard(knowledgeId,null);
    }
    private record Heads(List<Generation> generations,boolean summariesComplete) {}
    private Heads heads(Long knowledgeId,boolean locked) {
        List<Boolean> readiness = new ArrayList<>();
        var generations = jdbc.query("SELECT g.* FROM knowledge_document_relation r JOIN knowledge_index_generation g ON r.published_generation=g.generation_key WHERE r.knowledge_id=? AND g.state='PUBLISHED' AND NOT EXISTS (SELECT 1 FROM knowledge_draft d WHERE d.published_document_id=r.document_id AND (d.knowledge_id<>r.knowledge_id OR d.published_generation IS NULL OR d.published_generation<>g.generation_key OR d.status='ROLLED_BACK')) ORDER BY g.generation_key" + (locked?" FOR UPDATE":""),
                (rs, n) -> {
                    readiness.add(rs.getBoolean("summary_ready"));
                    return new Generation(rs.getString("generation_key"), rs.getLong("document_id"), rs.getInt("index_revision"), rs.getBoolean("legacy_vectors"), rs.getString("source_sha256"));
                }, knowledgeId);
        return new Heads(List.copyOf(generations),!generations.isEmpty() && readiness.stream().allMatch(Boolean::booleanValue));
    }
    private String headVersion(Long knowledgeId,boolean locked) { return ContentFingerprint.sha256(manifest(knowledgeId,heads(knowledgeId,locked).generations())); }
    private String manifest(Long knowledgeId,List<Generation> generations) {
        var manifest = new TreeMap<String,Object>(); manifest.put("knowledgeId", knowledgeId); manifest.put("generations", generations);
        return JSON.toJSONString(manifest);
    }
    private String saveManifest(Long knowledgeId,List<Generation> generations) {
        String json=manifest(knowledgeId,generations),version=ContentFingerprint.sha256(json);
        try { jdbc.update("INSERT INTO knowledge_index_read_snapshot (snapshot_sha256,knowledge_id,manifest_json,created_at) VALUES (?,?,?,?)", version, knowledgeId, json, LocalDateTime.now()); }
        catch (DuplicateKeyException duplicate) { /* The manifest is content addressed and immutable. */ }
        return version;
    }
    private ReadView openUnderGuard(Long knowledgeId,Generation candidate) {
        return openUnderGuard(knowledgeId,candidate,false);
    }
    private ReadView openUnderGuard(Long knowledgeId,Generation candidate,boolean evaluation) {
        var heads=heads(knowledgeId,false);
        String basis=saveManifest(knowledgeId,heads.generations());
        List<Generation> generations=heads.generations();
        if(candidate!=null) {
            var merged=new ArrayList<>(generations.stream().filter(g -> !g.documentId().equals(candidate.documentId())).toList());
            merged.add(candidate); merged.sort(Comparator.comparing(Generation::key)); generations=List.copyOf(merged);
        }
        String version=candidate==null?basis:saveManifest(knowledgeId,generations);
        String lease=evaluation?lifecycle.pin(knowledgeId,version,generations,3600):lifecycle.pin(knowledgeId,version,generations);
        return new ReadView(knowledgeId,generations,version,candidate==null && heads.summariesComplete(),lease,basis);
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
        private final String lease;
        private final ReadView root;
        private final String basisVersion;
        private boolean closed;
        private ReadView(Long knowledgeId, List<Generation> generations, String version, boolean summariesComplete,String lease,String basisVersion) {
            this.knowledgeId = knowledgeId; this.generations = generations; this.borrowed = false;
            this.lease=lease; this.root=this;
            this.basisVersion=basisVersion;
            this.snapshot = new IndexPublicationSnapshot(version, generations.size(), (int)generations.stream().filter(Generation::legacy).count(), summariesComplete);
        }
        private ReadView(ReadView parent) { this.knowledgeId = parent.knowledgeId; this.generations = parent.generations; this.snapshot = parent.snapshot; this.borrowed = true; this.lease=parent.lease; this.root=parent.root; this.basisVersion=parent.basisVersion; }
        public String basisVersion() { checkActive(); return basisVersion; }
        public void checkActive() {
            if(closed || root.closed) throw new IllegalStateException("索引读视图已关闭");
            lifecycle.assertActive(lease);
        }
        public IndexPublicationSnapshot snapshot() { checkActive(); return snapshot; }
        public List<Generation> generations() { checkActive(); return generations; }
        public Query filter(String documentField) {
            checkActive();
            return generationFilter(generations,documentField);
        }
        @Override public void close() {
            if (closed) return; closed = true;
            if (!borrowed) {
                views.get().remove(this); if (views.get().isEmpty()) views.remove();
                lifecycle.release(lease);
            }
        }
    }
    static Query generationFilter(List<Generation> generations,String documentField) {
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
    private static final String LEGACY_FILTER = """
        if (!doc.containsKey('document_id') || doc['document_id'].size()==0) return false;
        def expected = params.legacy.get(doc['document_id'].value.toString());
        if (expected == null) return false;
        long revision = doc.containsKey('index_revision') && doc['index_revision'].size()!=0 ? doc['index_revision'].value : 0;
        return revision == expected.longValue();
        """;
}
