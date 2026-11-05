package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.*;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.job.PlatformJobExecutionContext;
import cn.sdh.backend.mapper.*;
import cn.sdh.backend.utils.ContentFingerprint;
import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Owns one fixed corpus/configuration and immutable result archive per reliable-job attempt. */
@Service
public class EvaluationExecutionService {
    private static final String RESET_METRICS="UPDATE evaluation_task SET hit_rate=NULL,doc_hit_rate=NULL,mrr=NULL,avg_hit_rank=NULL,top_k_hits=NULL,negative_count=0,negative_hit_rate=NULL,labeled_count=0,judged_count=0,recall_at_k=NULL,precision_at_k=NULL,ndcg_at_k=NULL,map_score=NULL,context_precision=NULL,context_recall=NULL,faithfulness=NULL,answer_relevancy=NULL,completeness=NULL,fluency=NULL,overall_score=NULL,regression_status=NULL WHERE id=?";
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transactions;
    private final ModelConfigMapper models;
    private final KnowledgeAccessService access;
    private final PublishedIndexService publications;
    private final EvaluationQaMapper qaMapper;
    private final EvaluationTaskMapper tasks;
    private final EvaluationReplayService replays;
    public EvaluationExecutionService(JdbcTemplate jdbc,PlatformTransactionManager manager,ModelConfigMapper models,KnowledgeAccessService access,PublishedIndexService publications,EvaluationQaMapper qaMapper,EvaluationTaskMapper tasks,EvaluationReplayService replays) {
        this.jdbc=jdbc; this.transactions=new TransactionTemplate(manager); this.models=models; this.access=access; this.publications=publications; this.qaMapper=qaMapper; this.tasks=tasks; this.replays=replays;
    }
    public Session start(EvaluationTask task,PlatformJobExecutionContext context,Map<String,String> prompts) {
        if(context==null || !Objects.equals(task.getJobId(),context.jobId())) throw new BusinessException(409,"评测必须由当前可靠任务执行");
        var original=access.requireRead(task.getKnowledgeId(),task.getUserId());
        if(original==null) throw new BusinessException(404,"知识库不存在");
        var replay=replays.spec(task);
        var base=replay==null?JSON.parseObject(JSON.toJSONString(original),KnowledgeBase.class):replay.base();
        var catalog=models.selectList(null).stream().map(ModelExecutionScope::copy).toList();
        var view=replay==null?publications.openEvaluation(task.getKnowledgeId()):publications.openHistorical(task.getKnowledgeId(),replay.version());
        ModelExecutionScope scope=null;
        try {
            scope=new ModelExecutionScope(catalog);
            String id=UUID.randomUUID().toString().replace("-","");
            var initial=new TreeMap<String,Object>(); initial.put("schemaVersion",1); initial.put("knowledgeBase",base);
            initial.put("publication",view.snapshot()); initial.put("generations",view.generations()); initial.put("evaluationMode",task.getEvaluationMode()); initial.put("topK",task.getTopK());
            initial.put("judgeModel",task.getJudgeModel()); initial.put("promptVersions",Map.copyOf(prompts));
            initial.put("modelCatalogSha256",scope.catalogSha256());
            if(replay!=null) initial.put("replaySourceExecutionId",replay.sourceExecution());
            initial.put("runtimeFingerprints",runtimeFingerprints());
            String publication=view.snapshot().version();
            String snapshot=JSON.toJSONString(initial),configuration=ContentFingerprint.sha256(snapshot);
            transactions.executeWithoutResult(status -> {
                fence(task,context,null);
                if(jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_task WHERE id=? AND status=2",Integer.class,task.getId())>0) throw new EvaluationIntegrityException();
                jdbc.update("UPDATE evaluation_execution_attempt SET status='INTERRUPTED',finished_at=CURRENT_TIMESTAMP(6) WHERE task_id=? AND status='RUNNING'",task.getId());
                int number=jdbc.queryForObject("SELECT COALESCE(MAX(attempt_number),0)+1 FROM evaluation_execution_attempt WHERE task_id=?",Integer.class,task.getId());
                jdbc.update("INSERT INTO evaluation_execution_attempt(id,task_id,job_id,user_id,attempt_number,status,publication_sha256,configuration_sha256,initial_snapshot_json,started_at) VALUES(?,?,?,?,?,'RUNNING',?,?,?,CURRENT_TIMESTAMP(6))",id,task.getId(),context.jobId(),task.getUserId(),number,publication,configuration,snapshot);
                jdbc.update("UPDATE evaluation_task SET current_execution_id=?,status=1,error_message=NULL,update_time=CURRENT_TIMESTAMP WHERE id=?",id,task.getId());
                jdbc.update(RESET_METRICS,task.getId());
                qaMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<EvaluationQa>().eq(EvaluationQa::getTaskId,task.getId()));
            });
            resetMetrics(task);
            task.setStatus(1); task.setErrorMessage(null); task.setCurrentExecutionId(id);
            return new Session(id,task,context,base,view,scope,initial,replay);
        } catch(RuntimeException error) { if(scope!=null) scope.close(); view.close(); throw error; }
    }
    public List<cn.sdh.backend.dto.EvaluationQaItem> frozenReplayInputs(Long taskId,PlatformJobExecutionContext context) {
        return replays.inputs(taskId,context);
    }
    public boolean alreadyCompleted(EvaluationTask task,PlatformJobExecutionContext context) {
        if(context==null || !Objects.equals(task.getJobId(),context.jobId())) return false;
        context.checkpoint();
        return jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_execution_attempt a JOIN evaluation_task t ON t.current_execution_id=a.id WHERE t.id=? AND t.job_id=? AND t.status=2 AND a.status='COMPLETED'",Integer.class,task.getId(),context.jobId())==1;
    }
    public void failTask(EvaluationTask task,PlatformJobExecutionContext context) {
        if(context==null || !Objects.equals(task.getJobId(),context.jobId())) return;
        try { transactions.executeWithoutResult(status -> {
            fence(task,context,task.getCurrentExecutionId());
            jdbc.update("UPDATE evaluation_task SET status=3,error_message='评测执行未完整完成',update_time=CURRENT_TIMESTAMP WHERE id=? AND status<>2",task.getId());
        }); } catch(RuntimeException lostOwner) { /* Never update a successor's task. */ }
    }
    private static void resetMetrics(EvaluationTask task) {
        task.setHitRate(null); task.setDocHitRate(null); task.setMrr(null); task.setAvgHitRank(null); task.setTopKHits(null);
        task.setNegativeCount(0); task.setNegativeHitRate(null); task.setLabeledCount(0); task.setJudgedCount(0);
        task.setRecallAtK(null); task.setPrecisionAtK(null); task.setNdcgAtK(null); task.setMapScore(null);
        task.setContextPrecision(null); task.setContextRecall(null); task.setFaithfulness(null); task.setAnswerRelevancy(null);
        task.setCompleteness(null); task.setFluency(null); task.setOverallScore(null); task.setRegressionStatus(null);
    }
    private void fence(EvaluationTask task,PlatformJobExecutionContext context,String execution) {
        context.checkpoint();
        var row=jdbc.queryForList("SELECT job_id,current_execution_id,user_id FROM evaluation_task WHERE id=? FOR UPDATE",task.getId());
        if(row.isEmpty() || !Objects.equals(((Number)row.getFirst().get("job_id")).longValue(),context.jobId()) || !Objects.equals(((Number)row.getFirst().get("user_id")).longValue(),task.getUserId()) || (execution!=null && !execution.equals(row.getFirst().get("current_execution_id")))) throw new EvaluationIntegrityException();
    }
    public final class Session implements AutoCloseable {
        private final String id;
        private final EvaluationTask task;
        private final PlatformJobExecutionContext context;
        private final KnowledgeBase base;
        private final PublishedIndexService.ReadView view;
        private final ModelExecutionScope scope;
        private final Map<String,Object> initial;
        private int count;
        private boolean terminal;
        private int expectedCount;
        private final EvaluationReplayService.Spec replay;
        Session(String id,EvaluationTask task,PlatformJobExecutionContext context,KnowledgeBase base,PublishedIndexService.ReadView view,ModelExecutionScope scope,Map<String,Object> initial,EvaluationReplayService.Spec replay) {
            this.id=id; this.task=task; this.context=context; this.base=base; this.view=view; this.scope=scope; this.initial=initial; this.replay=replay;
        }
        public KnowledgeBase base() { return JSON.parseObject(JSON.toJSONString(base),KnowledgeBase.class); }
        public long seed() { return Long.parseUnsignedLong(((cn.sdh.backend.dto.IndexPublicationSnapshot)initial.get("publication")).version().substring(0,16),16); }
        public void check() { context.checkpoint(); view.checkActive(); scope.check(); }
        public void inputs(Object values) {
            check();
            if(!(values instanceof Collection<?> entries) || entries.isEmpty()) throw new EvaluationIntegrityException();
            expectedCount=entries.size();
            String json=JSON.toJSONString(values);
            if(replay!=null && !replay.inputSha256().equals(ContentFingerprint.sha256(json))) throw new EvaluationIntegrityException();
            if(json.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>4*1024*1024) throw new EvaluationIntegrityException();
            transactions.executeWithoutResult(status -> {
                fence(task,context,id);
                if(jdbc.update("UPDATE evaluation_execution_attempt SET input_json=?,input_sha256=? WHERE id=? AND input_json IS NULL",json,ContentFingerprint.sha256(json),id)!=1) throw new EvaluationIntegrityException();
            });
        }
        public void record(EvaluationQa qa) {
            check();
            if(qa==null || !Objects.equals(task.getId(),qa.getTaskId()) || qa.getQuestion()==null || qa.getQuestion().isBlank()) throw new EvaluationIntegrityException();
            var snapshot=JSON.parseObject(JSON.toJSONString(qa));
            snapshot.remove("retrievedContext"); snapshot.put("retrievedContextSha256",ContentFingerprint.sha256(qa.getRetrievedContext()));
            String json=JSON.toJSONString(snapshot); int ordinal=count+1;
            if(expectedCount==0 || ordinal>expectedCount || terminal) throw new EvaluationIntegrityException();
            transactions.executeWithoutResult(status -> {
                fence(task,context,id); qaMapper.insert(qa);
                jdbc.update("INSERT INTO evaluation_execution_result(execution_id,ordinal,snapshot_json,snapshot_sha256) VALUES(?,?,?,?)",id,ordinal,json,ContentFingerprint.sha256(json));
                jdbc.update("UPDATE evaluation_execution_attempt SET result_count=? WHERE id=? AND status='RUNNING'",ordinal,id);
            });
            count=ordinal;
        }
        public void complete() {
            check();
            transactions.executeWithoutResult(status -> {
                fence(task,context,id);
                if(terminal || jdbc.queryForObject("SELECT input_sha256 FROM evaluation_execution_attempt WHERE id=?",String.class,id)==null || count==0 || count!=expectedCount) throw new EvaluationIntegrityException();
                String json=manifest();
                if(jdbc.update("UPDATE evaluation_execution_attempt SET status='COMPLETED',final_snapshot_json=?,final_snapshot_sha256=?,finished_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='RUNNING'",json,ContentFingerprint.sha256(json),id)!=1) throw new EvaluationIntegrityException();
                jdbc.update(RESET_METRICS,task.getId());
                task.setStatus(2); task.setQaCount(count); task.setUpdateTime(java.time.LocalDateTime.now());
                if(tasks.updateById(task)!=1) throw new EvaluationIntegrityException();
                replays.release(task.getId());
            }); terminal=true;
        }
        private String manifest() {
            var manifest=new TreeMap<String,Object>(initial);
            manifest.put("models",scope.bindings()); manifest.put("modelsSha256",scope.bindingsSha256()); manifest.put("resultCount",count);
            manifest.put("inputSha256",jdbc.queryForObject("SELECT input_sha256 FROM evaluation_execution_attempt WHERE id=?",String.class,id));
            manifest.put("resultDigests",jdbc.queryForList("SELECT snapshot_sha256 FROM evaluation_execution_result WHERE execution_id=? ORDER BY ordinal",String.class,id));
            return JSON.toJSONString(manifest);
        }
        @Override public void close() {
            try {
                if(!terminal) {
                    try {
                        transactions.executeWithoutResult(status -> {
                            fence(task,context,id);
                            String json=manifest();
                            jdbc.update("UPDATE evaluation_execution_attempt SET status='FAILED',final_snapshot_json=?,final_snapshot_sha256=?,finished_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='RUNNING'",json,ContentFingerprint.sha256(json),id);
                            jdbc.update("UPDATE evaluation_task SET status=3,error_message='评测执行未完整完成',update_time=CURRENT_TIMESTAMP WHERE id=?",task.getId());
                        });
                    } catch(RuntimeException lost) { /* A new owner will mark this attempt interrupted; never overwrite its task. */ }
                }
            } finally { scope.close(); view.close(); }
        }
    }
    private static Map<String,String> runtimeFingerprints() {
        var hashes=new TreeMap<String,String>();
        for(var type:List.of(cn.sdh.backend.service.impl.EvaluationServiceImpl.class,cn.sdh.backend.service.impl.RagSearchServiceImpl.class,
                cn.sdh.backend.service.impl.VectorStoreServiceImpl.class,ModelFactory.class,ModelExecutionScope.class))
            hashes.put(type.getName(),ContentFingerprint.classSha256(type));
        return hashes;
    }
    private void authorize(Long taskId,Long actor) {
        if(actor==null) throw new BusinessException(401,"请先登录");
        var rows=jdbc.queryForList("SELECT knowledge_id FROM evaluation_task WHERE id=? AND user_id=?",taskId,actor);
        if(rows.isEmpty()) throw new BusinessException(403,"无权访问该评测");
        access.requireRead(((Number)rows.getFirst().get("knowledge_id")).longValue(),actor);
    }
    public List<Map<String,Object>> attempts(Long taskId,Long actor) {
        authorize(taskId,actor);
        return jdbc.queryForList("SELECT id,task_id,attempt_number,status,publication_sha256,configuration_sha256,input_sha256,initial_snapshot_json,final_snapshot_json,final_snapshot_sha256,result_count,started_at,finished_at FROM evaluation_execution_attempt WHERE task_id=? ORDER BY attempt_number",taskId);
    }
    public List<Map<String,Object>> results(String execution,Long actor,int offset,int limit) {
        if(actor==null) throw new BusinessException(401,"请先登录");
        if(offset<0 || limit<1 || limit>1000) throw new BusinessException(400,"分页参数无效");
        var rows=jdbc.queryForList("SELECT task_id FROM evaluation_execution_attempt WHERE id=? AND user_id=?",execution,actor);
        if(rows.isEmpty()) throw new BusinessException(403,"无权访问该评测");
        authorize(((Number)rows.getFirst().get("task_id")).longValue(),actor);
        return jdbc.queryForList("SELECT ordinal,snapshot_json,snapshot_sha256 FROM evaluation_execution_result WHERE execution_id=? ORDER BY ordinal LIMIT ? OFFSET ?",execution,limit,offset);
    }
    /** Configuration differences are intentional for A/B, but corpus and questions must match. */
    public boolean comparable(EvaluationTask current,EvaluationTask baseline) {
        if(current.getCurrentExecutionId()==null || baseline==null || baseline.getCurrentExecutionId()==null
                || !Integer.valueOf(2).equals(baseline.getStatus()) || !Objects.equals(current.getUserId(),baseline.getUserId())
                || !Objects.equals(current.getKnowledgeId(),baseline.getKnowledgeId())
                || !Objects.equals(current.getTopK(),baseline.getTopK()) || !Objects.equals(current.getEvaluationMode(),baseline.getEvaluationMode())) return false;
        return jdbc.queryForObject("SELECT COUNT(*) FROM evaluation_execution_attempt a JOIN evaluation_execution_attempt b ON a.publication_sha256=b.publication_sha256 AND a.input_sha256=b.input_sha256 WHERE a.id=? AND b.id=? AND a.status IN ('RUNNING','COMPLETED') AND b.status='COMPLETED'",Integer.class,current.getCurrentExecutionId(),baseline.getCurrentExecutionId())==1;
    }
    @org.springframework.scheduling.annotation.Scheduled(fixedDelayString="${evaluation.execution-recovery-delay-ms:30000}")
    public void reconcileTerminatedJobs() {
        var candidates=jdbc.queryForList("SELECT DISTINCT a.task_id,a.job_id FROM evaluation_execution_attempt a JOIN platform_job j ON j.id=a.job_id WHERE a.status='RUNNING' AND j.status IN ('CANCELLED','DEAD_LETTER','FAILED') LIMIT 100");
        for(var candidate:candidates) transactions.executeWithoutResult(status -> {
            var jobs=jdbc.queryForList("SELECT status FROM platform_job WHERE id=? FOR UPDATE",candidate.get("job_id"));
            if(jobs.isEmpty() || !Set.of("CANCELLED","DEAD_LETTER","FAILED").contains(jobs.getFirst().get("status"))) return;
            var task=jdbc.queryForList("SELECT current_execution_id,job_id FROM evaluation_task WHERE id=? FOR UPDATE",candidate.get("task_id"));
            String outcome="CANCELLED".equals(jobs.getFirst().get("status"))?"CANCELLED":"INTERRUPTED";
            jdbc.update("UPDATE evaluation_execution_attempt SET status=?,finished_at=CURRENT_TIMESTAMP(6) WHERE task_id=? AND job_id=? AND status='RUNNING'",outcome,candidate.get("task_id"),candidate.get("job_id"));
            if(!task.isEmpty() && Objects.equals(task.getFirst().get("job_id"),candidate.get("job_id")))
                jdbc.update("UPDATE evaluation_task SET status=?,error_message='评测任务已取消或重试耗尽',update_time=CURRENT_TIMESTAMP WHERE id=? AND status<>2","CANCELLED".equals(outcome)?4:3,candidate.get("task_id"));
            if(!task.isEmpty() && Objects.equals(task.getFirst().get("job_id"),candidate.get("job_id"))) replays.release(((Number)candidate.get("task_id")).longValue());
        });
    }
}
