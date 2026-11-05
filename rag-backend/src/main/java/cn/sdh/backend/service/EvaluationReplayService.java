package cn.sdh.backend.service;

import cn.sdh.backend.common.exception.BusinessException;
import cn.sdh.backend.common.exception.EvaluationIntegrityException;
import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.entity.*;
import cn.sdh.backend.job.*;
import cn.sdh.backend.mapper.EvaluationTaskMapper;
import cn.sdh.backend.utils.ContentFingerprint;
import com.alibaba.fastjson2.JSON;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import java.util.*;

/** Historical corpus replay uses current model credentials; it never silently substitutes latest data. */
@Service @RequiredArgsConstructor
public class EvaluationReplayService {
    private final JdbcTemplate jdbc;
    private final KnowledgeAccessService access;
    private final PublishedIndexService publications;
    private final EvaluationTaskMapper tasks;
    private final PlatformJobService jobs;
    public record Command(String requestKey,String judgeModel) {}
    public record Spec(String sourceExecution,String version,String holdId,String inputSha256,KnowledgeBase base) {}

    public EvaluationTask create(String execution,Long actor,Command command) {
        if(actor==null) throw new BusinessException(401,"请先登录");
        if(command==null || command.requestKey()==null || !command.requestKey().matches("[A-Za-z0-9_-]{16,64}")) throw new BusinessException(400,"请提供 16—64 位请求幂等键");
        var source=source(execution,actor);
        Long knowledge=((Number)source.get("knowledge_id")).longValue();
        access.requireEdit(knowledge,actor);
        String judge=command.judgeModel()==null?(String)source.get("judge_model"):command.judgeModel().trim();
        if(judge!=null && (judge.isBlank() || judge.length()>200)) throw new BusinessException(400,"评审模型选择无效");
        String requestSha=ContentFingerprint.sha256(JSON.toJSONString(Arrays.asList(execution,judge)));
        var existing=existing(actor,command.requestKey(),requestSha);
        if(existing!=null) return existing;
        String hold=UUID.randomUUID().toString().replace("-","");
        return publications.reserveHistorical(knowledge,(String)source.get("publication_sha256"),hold,() -> {
            // The KB guard serializes concurrent identical requests and protects their corpus from cleanup.
            var winner=existing(actor,command.requestKey(),requestSha);
            if(winner!=null) {
                jdbc.update("DELETE FROM knowledge_index_snapshot_hold WHERE hold_id=?",hold); return winner;
            }
            access.requireEdit(knowledge,actor);
            var task=new EvaluationTask(); task.setKnowledgeId(knowledge); task.setUserId(actor);
            task.setTaskName("历史重放 · #"+source.get("task_id")+" · "+execution.substring(0,8));
            task.setEvaluationMode((String)source.get("evaluation_mode")); task.setTopK(((Number)source.get("top_k")).intValue());
            task.setJudgeModel(judge); task.setBaselineTaskId(((Number)source.get("task_id")).longValue());
            task.setQaCount(JSON.parseArray((String)source.get("input_json")).size()); task.setDatasetType("historical"); task.setStatus(0);
            task.setConfigSnapshot((String)source.get("initial_snapshot_json")); tasks.insert(task);
            jdbc.update("INSERT INTO evaluation_replay_request(task_id,user_id,request_key,request_sha256,source_execution_id,publication_sha256,hold_id,configuration_json,configuration_sha256,input_json,input_sha256,expires_at) VALUES(?,?,?,?,?,?,?,?,?,?,?,TIMESTAMPADD(HOUR,24,CURRENT_TIMESTAMP(6)))",
                    task.getId(),actor,command.requestKey(),requestSha,execution,source.get("publication_sha256"),hold,source.get("initial_snapshot_json"),source.get("configuration_sha256"),source.get("input_json"),source.get("input_sha256"));
            var payload=new EvaluationJobPayload(task.getId(),EvaluationJobPayload.REPLAY,List.of());
            var job=jobs.enqueue(new PlatformJobService.EnqueueCommand("evaluation:"+task.getId(),EvaluationJobHandler.TYPE,"EVALUATION",task.getId(),JSON.toJSONString(payload),0,3,actor));
            task.setJobId(job.getId()); tasks.updateById(task); return task;
        });
    }
    private Map<String,Object> source(String execution,Long actor) {
        var rows=jdbc.queryForList("SELECT a.*,t.knowledge_id,t.evaluation_mode,t.top_k,t.judge_model FROM evaluation_execution_attempt a JOIN evaluation_task t ON t.id=a.task_id AND t.current_execution_id=a.id WHERE a.id=? AND a.user_id=? AND t.user_id=? AND a.status='COMPLETED' AND t.status=2",execution,actor,actor);
        if(rows.size()!=1) throw new BusinessException(403,"只能重放本人当前已完成的评测批次");
        var row=rows.getFirst();
        verifyJson((String)row.get("input_json"),(String)row.get("input_sha256"));
        verifyJson((String)row.get("initial_snapshot_json"),(String)row.get("configuration_sha256"));
        verifyJson((String)row.get("final_snapshot_json"),(String)row.get("final_snapshot_sha256"));
        var input=JSON.parseArray((String)row.get("input_json"));
        var initial=JSON.parseObject((String)row.get("initial_snapshot_json"));
        if(input.isEmpty() || input.size()!=((Number)row.get("result_count")).intValue()
                || !Objects.equals(initial.getJSONObject("publication").getString("version"),row.get("publication_sha256"))) throw new EvaluationIntegrityException();
        return row;
    }
    private EvaluationTask existing(Long actor,String key,String sha) {
        var rows=jdbc.queryForList("SELECT task_id,request_sha256 FROM evaluation_replay_request WHERE user_id=? AND request_key=?",actor,key);
        if(rows.isEmpty()) return null;
        if(!Objects.equals(sha,rows.getFirst().get("request_sha256"))) throw new BusinessException(409,"幂等键已用于其他重放请求");
        return tasks.selectById(((Number)rows.getFirst().get("task_id")).longValue());
    }
    public Spec spec(EvaluationTask task) {
        var rows=jdbc.queryForList("SELECT *,expires_at>CURRENT_TIMESTAMP(6) AS available FROM evaluation_replay_request WHERE task_id=?",task.getId());
        if(rows.isEmpty()) return null;
        access.requireEdit(task.getKnowledgeId(),task.getUserId());
        var row=rows.getFirst();
        if(!flag(row.get("available")) || !Objects.equals(task.getUserId(),((Number)row.get("user_id")).longValue())) throw new BusinessException(409,"重放保留窗口已过期，请重新创建重放任务");
        verifyJson((String)row.get("configuration_json"),(String)row.get("configuration_sha256"));
        verifyJson((String)row.get("input_json"),(String)row.get("input_sha256"));
        var configuration=JSON.parseObject((String)row.get("configuration_json"));
        var base=configuration.getObject("knowledgeBase",KnowledgeBase.class);
        if(base==null || !Objects.equals(base.getId(),task.getKnowledgeId()) || !Objects.equals(configuration.getInteger("topK"),task.getTopK())
                || !Objects.equals(configuration.getString("evaluationMode"),task.getEvaluationMode())) throw new EvaluationIntegrityException();
        return new Spec((String)row.get("source_execution_id"),(String)row.get("publication_sha256"),(String)row.get("hold_id"),(String)row.get("input_sha256"),base);
    }
    public List<EvaluationQaItem> inputs(Long taskId,PlatformJobExecutionContext context) {
        context.checkpoint(); var task=tasks.selectById(taskId);
        if(task==null || !Objects.equals(task.getJobId(),context.jobId()) || spec(task)==null) throw new EvaluationIntegrityException();
        String input=jdbc.queryForObject("SELECT input_json FROM evaluation_replay_request WHERE task_id=?",String.class,taskId);
        return JSON.parseArray(input,EvaluationQaItem.class);
    }
    public void release(Long taskId) {
        jdbc.update("DELETE FROM knowledge_index_snapshot_hold WHERE hold_id IN (SELECT hold_id FROM evaluation_replay_request WHERE task_id=?)",taskId);
    }
    private void verifyJson(String json,String sha) { if(json==null || sha==null || !sha.equals(ContentFingerprint.sha256(json))) throw new EvaluationIntegrityException(); }
    private boolean flag(Object value) { return Boolean.TRUE.equals(value) || value instanceof Number number && number.intValue()==1; }
}
