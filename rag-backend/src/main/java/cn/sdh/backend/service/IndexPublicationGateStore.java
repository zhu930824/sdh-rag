package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.job.DocumentIndexJobPayload;
import cn.sdh.backend.utils.ContentFingerprint;
import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import java.nio.charset.StandardCharsets;
import java.util.*;

/** Gate persistence participates in the publisher's transaction; it never calls a model or ES. */
@Service
public class IndexPublicationGateStore {
    public record Policy(int schemaVersion,int topK,String algorithm,String embeddingModel,Long draftId,Long knowledgeId,Long documentId,String rawSourceSha256,List<String> queries,Long reviewer,String reviewedAt) {
        public String sha256() { return ContentFingerprint.sha256(JSON.toJSONString(this)); }
    }
    private final JdbcTemplate jdbc;
    private final AgentImprovementService improvements;
    public IndexPublicationGateStore(JdbcTemplate jdbc,AgentImprovementService improvements) { this.jdbc=jdbc; this.improvements=improvements; }
    public boolean managed(Long documentId) { return jdbc.queryForObject("SELECT COUNT(*) FROM knowledge_draft WHERE published_document_id=?",Integer.class,documentId)>0; }
    Policy policy(DocumentIndexJobPayload payload) {
        var rows=jdbc.queryForList("SELECT * FROM knowledge_draft WHERE published_document_id=? FOR UPDATE",payload.documentId());
        if(rows.isEmpty()) return null;
        var d=rows.getFirst();
        if(!Objects.equals(((Number)d.get("knowledge_id")).longValue(),payload.knowledgeId()) || !Set.of("PUBLISHING","PUBLISHED","PUBLISH_FAILED","GATE_FAILED").contains(d.get("status"))) throw new BusinessException(409,"草稿不允许向此知识库发布");
        if(d.get("reviewed_by")==null) throw new BusinessException(409,"草稿缺少审核记录");
        String markdown=KnowledgePublicationContent.markdown((String)d.get("title"),(String)d.get("content"));
        byte[] source=markdown.getBytes(StandardCharsets.UTF_8);
        if(source.length>KnowledgePublicationContent.MAX_SOURCE_BYTES) throw new BusinessException(400,"发布草稿文件超过 1 MiB");
        String embedding=payload.config()==null?null:payload.config().getEmbeddingModel();
        if(embedding==null || embedding.isBlank()) embedding=jdbc.queryForObject("SELECT embedding_model FROM knowledge_base WHERE id=?",String.class,payload.knowledgeId());
        return new Policy(1,5,"CANDIDATE_VECTOR_RECALL",embedding,((Number)d.get("id")).longValue(),payload.knowledgeId(),payload.documentId(),ContentFingerprint.sha256(markdown),
                KnowledgePublicationContent.queries((String)d.get("regression_queries")),((Number)d.get("reviewed_by")).longValue(),Objects.toString(d.get("reviewed_at"),null));
    }
    Policy verifySource(DocumentIndexJobPayload payload,String rawSha) {
        var policy=policy(payload);
        if(policy!=null && !Objects.equals(policy.rawSourceSha256(),rawSha)) throw new BusinessException(409,"待索引文件与审核通过的草稿不一致");
        return policy;
    }
    void freeze(String generation,Policy policy) {
        if(policy!=null) {
            jdbc.update("INSERT INTO knowledge_index_publication_gate(generation_key,draft_id,policy_sha256,policy_json) VALUES(?,?,?,?)",generation,policy.draftId(),policy.sha256(),JSON.toJSONString(policy));
            jdbc.update("UPDATE knowledge_draft SET status='PUBLISHING',error_message=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=? AND status IN ('PUBLISH_FAILED','GATE_FAILED')",policy.draftId());
        }
    }
    public Policy frozen(String generation) {
        var rows=jdbc.queryForList("SELECT policy_json FROM knowledge_index_publication_gate WHERE generation_key=?",generation);
        return rows.isEmpty()?null:JSON.parseObject((String)rows.getFirst().get("policy_json"),Policy.class);
    }
    void record(DocumentIndexJobPayload payload,String generation,String basis,String candidate,KnowledgePublishingService.ValidationResult result) {
        var current=policy(payload);
        var rows=jdbc.queryForList("SELECT * FROM knowledge_index_publication_gate WHERE generation_key=? FOR UPDATE",generation);
        if(current==null || rows.isEmpty() || !current.sha256().equals(rows.getFirst().get("policy_sha256"))) throw new BusinessException(409,"门禁策略已变化，必须重新索引验证");
        if(!current.queries().equals(result.queries().stream().map(KnowledgePublishingService.ValidationItem::query).toList())
                || result.passed()!=result.queries().stream().allMatch(i -> i.passed() && i.rank()!=null && i.rank()>=1 && i.rank()<=current.topK())) throw new BusinessException(409,"门禁结果与冻结策略不一致");
        String json=JSON.toJSONString(result);
        var row=rows.getFirst();
        if(!"PENDING".equals(row.get("status"))) {
            if(!Objects.equals(json,row.get("result_json")) || !Objects.equals(basis,row.get("basis_sha256")) || !Objects.equals(candidate,row.get("candidate_sha256"))) throw new BusinessException(409,"门禁回执不可覆盖");
            return;
        }
        jdbc.update("UPDATE knowledge_index_publication_gate SET status=?,basis_sha256=?,candidate_sha256=?,result_json=?,checked_at=CURRENT_TIMESTAMP(6) WHERE generation_key=?",result.passed()?"PASSED":"FAILED",basis,candidate,json,generation);
        jdbc.update("INSERT INTO knowledge_publish_record(draft_id,knowledge_id,document_id,action,status,detail,operator_id,created_at) SELECT id,knowledge_id,published_document_id,'VALIDATE',?,?,created_by,CURRENT_TIMESTAMP FROM knowledge_draft WHERE id=?",result.passed()?"PASSED":"FAILED",JSON.toJSONString(Map.of("generation",generation,"basisVersion",basis,"candidateVersion",candidate,"validation",result)),current.draftId());
        if(!result.passed()) {
            jdbc.update("UPDATE knowledge_draft SET status='GATE_FAILED',validation_status='FAILED',validation_result=?,error_message='质量门禁未通过，本次索引批次未发布',updated_at=CURRENT_TIMESTAMP WHERE id=?",json,current.draftId());
            jdbc.update("UPDATE knowledge_document_relation SET process_status=3,index_write_generation=NULL,error_message='质量门禁未通过，本次索引批次未发布' WHERE id=?",payload.relationId());
            jdbc.update("UPDATE knowledge_document SET status=2 WHERE id=? AND NOT EXISTS (SELECT 1 FROM knowledge_document_relation WHERE document_id=? AND published_generation IS NOT NULL)",payload.documentId(),payload.documentId());
            updatePublishRecord(current.draftId(),"FAILED","质量门禁未通过，未推进索引发布头");
        }
    }
    void authorize(DocumentIndexJobPayload payload,String generation,java.util.function.Supplier<String> basis,boolean idempotent) {
        var policy=policy(payload);
        if(policy==null) return;
        var rows=jdbc.queryForList("SELECT * FROM knowledge_index_publication_gate WHERE generation_key=? FOR UPDATE",generation);
        if(rows.isEmpty()) throw new BusinessException(409,"草稿索引尚未执行质量门禁");
        var row=rows.getFirst();
        if(!"PASSED".equals(row.get("status")) || !policy.sha256().equals(row.get("policy_sha256")) || (!idempotent && !Objects.equals(basis.get(),row.get("basis_sha256")))) throw new BusinessException(409,"质量门禁未通过或检索基线已变化，不能发布");
    }
    void published(DocumentIndexJobPayload payload,String generation) {
        if(!managed(payload.documentId())) return;
        var receipt=jdbc.queryForMap("SELECT draft_id,result_json FROM knowledge_index_publication_gate WHERE generation_key=?",generation);
        Long draftId=((Number)receipt.get("draft_id")).longValue();
        var existing=jdbc.queryForMap("SELECT status,published_generation FROM knowledge_draft WHERE id=?",draftId);
        if("PUBLISHED".equals(existing.get("status")) && generation.equals(existing.get("published_generation"))) return;
        jdbc.update("UPDATE knowledge_draft SET status='PUBLISHED',published_generation=?,published_at=CURRENT_TIMESTAMP,validation_status='PASSED',validation_result=?,error_message=NULL,updated_at=CURRENT_TIMESTAMP WHERE id=?",generation,receipt.get("result_json"),draftId);
        jdbc.update("UPDATE knowledge_document SET status=1 WHERE id=?",payload.documentId());
        String detail="候选索引回归通过，已原子发布批次 " + generation;
        if(updatePublishRecord(draftId,"COMPLETED",detail)==0) jdbc.update("INSERT INTO knowledge_publish_record(draft_id,knowledge_id,document_id,action,status,detail,operator_id,created_at) SELECT id,knowledge_id,published_document_id,'PUBLISH','COMPLETED',?,created_by,CURRENT_TIMESTAMP FROM knowledge_draft WHERE id=?",detail,draftId);
        var draft=jdbc.queryForMap("SELECT improvement_id,created_by FROM knowledge_draft WHERE id=?",draftId);
        if(draft.get("improvement_id")!=null) improvements.markPublished(((Number)draft.get("improvement_id")).longValue(),((Number)draft.get("created_by")).longValue(),draftId);
    }
    void failed(DocumentIndexJobPayload payload,String error) {
        int changed=jdbc.update("UPDATE knowledge_draft SET status='PUBLISH_FAILED',error_message=?,updated_at=CURRENT_TIMESTAMP WHERE published_document_id=? AND knowledge_id=? AND status='PUBLISHING'",error,payload.documentId(),payload.knowledgeId());
        if(changed==1) {
            jdbc.update("UPDATE knowledge_publish_record SET status='FAILED',detail=? WHERE document_id=? AND knowledge_id=? AND action='PUBLISH' AND status='SUBMITTED'",error,payload.documentId(),payload.knowledgeId());
            jdbc.update("UPDATE knowledge_document SET status=2 WHERE id=? AND NOT EXISTS (SELECT 1 FROM knowledge_document_relation WHERE document_id=? AND published_generation IS NOT NULL)",payload.documentId(),payload.documentId());
        }
    }
    public void reconcileTerminatedJobs() {
        jdbc.update("UPDATE knowledge_draft d SET status='PUBLISH_FAILED',error_message='索引任务已取消或重试耗尽',updated_at=CURRENT_TIMESTAMP WHERE d.status='PUBLISHING' AND EXISTS (SELECT 1 FROM knowledge_document_relation r JOIN platform_job j ON j.id=r.job_id WHERE r.document_id=d.published_document_id AND r.knowledge_id=d.knowledge_id AND j.status IN ('CANCELLED','DEAD_LETTER','FAILED'))");
        jdbc.update("UPDATE knowledge_publish_record p SET status='FAILED',detail='索引任务已取消或重试耗尽' WHERE p.action='PUBLISH' AND p.status='SUBMITTED' AND EXISTS (SELECT 1 FROM knowledge_draft d WHERE d.id=p.draft_id AND d.status='PUBLISH_FAILED' AND d.error_message='索引任务已取消或重试耗尽')");
    }
    private int updatePublishRecord(Long draft,String status,String detail) {
        return jdbc.update("UPDATE knowledge_publish_record SET status=?,detail=? WHERE draft_id=? AND action='PUBLISH' AND status='SUBMITTED'",status,detail,draft);
    }
}
