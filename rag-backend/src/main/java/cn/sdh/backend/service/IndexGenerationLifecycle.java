package cn.sdh.backend.service;

import cn.sdh.backend.config.IndexLifecycleProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Supplier;

/** Short SQL coordination only: never holds locks over an Elasticsearch request. */
@Slf4j @Service
public class IndexGenerationLifecycle {
    public record CleanupClaim(Long knowledgeId, PublishedIndexService.Generation generation, String owner) {}
    private static final String UNREFERENCED = "NOT EXISTS (SELECT 1 FROM knowledge_document_relation r WHERE r.published_generation=g.generation_key OR r.index_write_generation=g.generation_key)";
    private static final String NO_READER = "NOT EXISTS (SELECT 1 FROM knowledge_index_read_snapshot_generation ref JOIN knowledge_index_read_lease l ON l.snapshot_sha256=ref.snapshot_sha256 WHERE ref.generation_key=g.generation_key AND l.expires_at>CURRENT_TIMESTAMP(6))";
    private final JdbcTemplate jdbc;
    private final IndexLifecycleProperties policy;
    private final TransactionTemplate transactions;
    public IndexGenerationLifecycle(JdbcTemplate jdbc, PlatformTransactionManager manager, IndexLifecycleProperties policy) {
        this.jdbc=jdbc; this.policy=policy; this.transactions=new TransactionTemplate(manager);
        transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        transactions.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
    }
    <T> T readGuard(Long knowledgeId, Supplier<T> action) {
        return transactions.execute(status -> {
            // Upsert acquires an exclusive lock directly. Duplicate INSERT then SELECT FOR UPDATE
            // upgrades two shared duplicate-key locks and can deadlock competing readers/cleaners.
            jdbc.update("INSERT INTO knowledge_index_read_guard(knowledge_id) VALUES(?) ON DUPLICATE KEY UPDATE knowledge_id=knowledge_id",knowledgeId);
            return action.get();
        });
    }
    String pin(Long knowledgeId, String snapshot, List<PublishedIndexService.Generation> generations) {
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT generations_registered FROM knowledge_index_read_snapshot WHERE snapshot_sha256=?",Boolean.class,snapshot))) {
            jdbc.batchUpdate("INSERT INTO knowledge_index_read_snapshot_generation(snapshot_sha256,generation_key) VALUES(?,?)",
                    generations.stream().map(g -> new Object[]{snapshot,g.key()}).toList());
            jdbc.update("UPDATE knowledge_index_read_snapshot SET generations_registered=TRUE WHERE snapshot_sha256=?",snapshot);
        }
        String id=UUID.randomUUID().toString();
        jdbc.update("INSERT INTO knowledge_index_read_lease(lease_id,knowledge_id,snapshot_sha256,expires_at) VALUES(?,?,?,?)",id,knowledgeId,snapshot,now().plusSeconds(policy.getReadLeaseSeconds()));
        return id;
    }
    void assertActive(String lease) {
        Integer active=transactions.execute(status -> jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_index_read_lease WHERE lease_id=? AND expires_at>CURRENT_TIMESTAMP(6)",Integer.class,lease));
        if (!Integer.valueOf(1).equals(active)) {
            throw new cn.sdh.backend.common.exception.IndexReadLeaseExpiredException();
        }
    }
    void release(String lease) {
        try { transactions.executeWithoutResult(status -> jdbc.update("DELETE FROM knowledge_index_read_lease WHERE lease_id=?",lease)); }
        catch(org.springframework.dao.DataAccessException unavailable) { log.warn("释放检索读租约失败，将由到期清理回收"); }
    }
    public void reapExpiredLeases() {
        jdbc.update("DELETE FROM knowledge_index_read_lease WHERE expires_at<=CURRENT_TIMESTAMP(6) LIMIT 1000");
    }
    public List<Long> dueKnowledgeBases() {
        return jdbc.queryForList("SELECT g.knowledge_id FROM knowledge_index_generation g WHERE " + UNREFERENCED +
                " AND (g.retired_at IS NULL OR (g.next_cleanup_at<=CURRENT_TIMESTAMP(6) AND " + NO_READER + "))" +
                " AND (g.cleanup_until IS NULL OR g.cleanup_until<=CURRENT_TIMESTAMP(6)) GROUP BY g.knowledge_id ORDER BY MIN(COALESCE(g.next_cleanup_at,CURRENT_TIMESTAMP(6))),g.knowledge_id LIMIT ?",Long.class,policy.getKnowledgeBatchSize());
    }
    public List<CleanupClaim> claim(Long knowledgeId) {
        return readGuard(knowledgeId,() -> {
            LocalDateTime now=now();
            jdbc.update("DELETE FROM knowledge_index_read_lease WHERE knowledge_id=? AND expires_at<=?",knowledgeId,now);
            var retiring=jdbc.queryForList("SELECT g.generation_key FROM knowledge_index_generation g WHERE g.knowledge_id=? AND g.retired_at IS NULL AND " + UNREFERENCED + " ORDER BY g.generation_key LIMIT ?",String.class,knowledgeId,policy.getBatchSize());
            for(String key:retiring) jdbc.update("UPDATE knowledge_index_generation g SET retired_at=?,next_cleanup_at=? WHERE g.generation_key=? AND g.retired_at IS NULL AND " + UNREFERENCED,now,now.plusSeconds(policy.getRetentionSeconds()),key);
            var candidates=jdbc.query("SELECT g.* FROM knowledge_index_generation g WHERE g.knowledge_id=? AND g.retired_at IS NOT NULL AND g.next_cleanup_at<=? AND (g.cleanup_until IS NULL OR g.cleanup_until<=?) AND " + UNREFERENCED + " AND " + NO_READER + " ORDER BY g.next_cleanup_at,g.generation_key LIMIT ?",
                    (rs,n) -> new PublishedIndexService.Generation(rs.getString("generation_key"),rs.getLong("document_id"),rs.getInt("index_revision"),rs.getBoolean("legacy_vectors"),rs.getString("source_sha256")),knowledgeId,now,now,policy.getBatchSize());
            List<CleanupClaim> claims=new ArrayList<>();
            for(var generation:candidates) {
                String owner=UUID.randomUUID().toString();
                int updated=jdbc.update("UPDATE knowledge_index_generation g SET state='DELETING',cleanup_owner=?,cleanup_until=?,cleanup_attempts=cleanup_attempts+1,cleanup_error=NULL WHERE g.generation_key=? AND " + UNREFERENCED + " AND " + NO_READER,owner,now.plusSeconds(policy.getCleanupLeaseSeconds()),generation.key());
                if(updated==1) claims.add(new CleanupClaim(knowledgeId,generation,owner));
            }
            return List.copyOf(claims);
        });
    }
    public boolean finish(CleanupClaim claim, boolean success) {
        LocalDateTime now=now();
        return jdbc.update("UPDATE knowledge_index_generation SET state=?,last_cleanup_at=CASE WHEN ? THEN ? ELSE last_cleanup_at END,next_cleanup_at=?,cleanup_error=?,cleanup_owner=NULL,cleanup_until=NULL WHERE generation_key=? AND state='DELETING' AND cleanup_owner=?",
                success?"DELETED":"DELETING",success,now,now.plusSeconds(success?policy.getResweepSeconds():policy.getRetrySeconds()),success?null:"批次删除未完整确认，等待重试",claim.generation().key(),claim.owner())==1;
    }
    private LocalDateTime now() { return jdbc.queryForObject("SELECT CURRENT_TIMESTAMP(6)",LocalDateTime.class); }
}
