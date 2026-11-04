package cn.sdh.backend.agent.evaluation;

import cn.sdh.backend.agent.RunStateService;
import cn.sdh.backend.mapper.ChatHistoryMapper;
import cn.sdh.backend.entity.AgentRun;
import cn.sdh.backend.common.exception.BusinessException;
import com.alibaba.fastjson2.JSON;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

import java.util.*;
import java.time.LocalDateTime;
import static cn.sdh.backend.agent.evaluation.AgentEvaluationData.*;
import static cn.sdh.backend.agent.evaluation.AgentEvaluationValidation.*;

@Service
public class AgentEvaluationService {
    private final JdbcTemplate jdbc;
    private final RunStateService runs;
    private final ChatHistoryMapper histories;
    private final cn.sdh.backend.agent.AgentExecutionAttemptService attempts;
    public AgentEvaluationService(JdbcTemplate jdbc, RunStateService runs, ChatHistoryMapper histories,
                                  cn.sdh.backend.agent.AgentExecutionAttemptService attempts) {
        this.jdbc = jdbc; this.runs = runs; this.histories = histories; this.attempts = attempts;
    }
    private static final RowMapper<Dataset> DATASET = (rs, n) -> new Dataset(rs.getString("id"), rs.getString("name"),
            rs.getInt("version"), rs.getString("parent_id"), rs.getString("sha256"),
            JSON.parseArray(rs.getString("scenarios_json"), Scenario.class), rs.getTimestamp("created_at").toLocalDateTime());
    private static final RowMapper<EvaluationRecord> RECORD = (rs, n) -> new EvaluationRecord(rs.getString("id"),
            rs.getString("dataset_id"), rs.getString("scenario_id"), rs.getString("run_key"), rs.getString("status"),
            rs.getString("dataset_sha256"), rs.getString("snapshot_sha256"), rs.getString("scenario_json"),
            rs.getString("run_snapshot_json"), rs.getTimestamp("created_at").toLocalDateTime());

    @Transactional(rollbackFor = Exception.class)
    public Dataset importDataset(ImportRequest request, Long actor) {
        actor(actor);
        if (request == null) throw new BusinessException(400, "缺少导入内容");
        String name = text(request.name(), 120, "名称");
        var scenarios = scenarios(request.scenarios());
        Dataset parent = request.parentId() == null ? null : dataset(request.parentId(), actor);
        String payload = boundedJson(scenarios, 1024 * 1024);
        String id = UUID.randomUUID().toString().replace("-", "");
        int version = parent == null ? 1 : Math.addExact(parent.version(), 1);
        LocalDateTime now = LocalDateTime.now();
        try {
            jdbc.update("INSERT INTO agent_evaluation_dataset (id,user_id,name,version,parent_id,sha256,scenario_count,scenarios_json,created_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    id, actor, name, version, request.parentId(), sha256(payload), scenarios.size(), payload, now);
        } catch (DuplicateKeyException conflict) { throw new BusinessException(409, "该版本已有后继版本，请刷新后基于最新版本导入"); }
        return new Dataset(id, name, version, request.parentId(), sha256(payload), scenarios, now);
    }
    public Dataset dataset(String id, Long actor) {
        actor(actor);
        var rows = jdbc.query("SELECT * FROM agent_evaluation_dataset WHERE id=? AND user_id=?", DATASET, id, actor);
        if (rows.isEmpty()) throw new BusinessException(404, "场景版本不存在");
        return rows.getFirst();
    }
    public List<DatasetSummary> datasets(Long actor) {
        actor(actor);
        return jdbc.query("SELECT id,name,version,parent_id,sha256,scenario_count,created_at FROM agent_evaluation_dataset WHERE user_id=? ORDER BY created_at DESC,id DESC LIMIT 100",
                (rs, n) -> new DatasetSummary(rs.getString("id"), rs.getString("name"), rs.getInt("version"), rs.getString("parent_id"),
                        rs.getString("sha256"), rs.getInt("scenario_count"), rs.getTimestamp("created_at").toLocalDateTime()), actor);
    }
    @Transactional(rollbackFor = Exception.class, isolation = Isolation.REPEATABLE_READ)
    public EvaluationRecord record(RecordRequest request, Long actor) {
        actor(actor);
        if (request == null) throw new BusinessException(400, "缺少运行关联信息");
        Dataset dataset = dataset(request.datasetId(), actor);
        Scenario scenario = dataset.scenarios().stream().filter(s -> s.id().equals(request.scenarioId())).findFirst()
                .orElseThrow(() -> new BusinessException(404, "场景不存在"));
        String runKey = text(request.runKey(), 64, "运行标识");
        var existing = findRecord(dataset.id(), scenario.id(), runKey, actor, false);
        if (!existing.isEmpty()) return existing.getFirst();
        AgentRun run = runs.getByRunKey(runKey);
        if (run == null || !actor.equals(run.getUserId())) throw new BusinessException(404, "运行不存在");
        if (!Set.of("COMPLETED", "FAILED", "CANCELLED", "REJECTED").contains(run.getStatus())) throw new BusinessException(409, "只能归档已结束的运行");
        var history = run.getHistoryId() == null ? null : histories.selectById(run.getHistoryId());
        if (run.getHistoryId() != null && (history == null || !actor.equals(history.getUserId()) || !runKey.equals(history.getAgentRunKey()))) {
            throw new BusinessException(409, "运行回答关联不一致，无法归档");
        }
        LocalDateTime now = LocalDateTime.now();
        String snapshot = AgentEvaluationSnapshot.capture(run, history, runs.stepsOf(run.getId()), attempts.list(run.getId(), actor), now);
        String scenarioJson = boundedJson(scenario, 1024 * 1024);
        String id = UUID.randomUUID().toString().replace("-", "");
        try {
            jdbc.update("INSERT INTO agent_evaluation_record (id,user_id,dataset_id,scenario_id,run_key,status,dataset_sha256,snapshot_sha256,scenario_json,run_snapshot_json,created_at) VALUES (?,?,?,?,?,'RECORDED',?,?,?,?,?)",
                    id, actor, dataset.id(), scenario.id(), runKey, dataset.sha256(), sha256(snapshot), scenarioJson, snapshot, now);
        } catch (DuplicateKeyException conflict) {
            // A current read must see the winner even under an older REPEATABLE READ snapshot.
            var winner = findRecord(dataset.id(), scenario.id(), runKey, actor, true);
            if (winner.isEmpty()) throw conflict;
            return winner.getFirst();
        }
        return new EvaluationRecord(id, dataset.id(), scenario.id(), runKey, "RECORDED", dataset.sha256(), sha256(snapshot), scenarioJson, snapshot, now);
    }
    private List<EvaluationRecord> findRecord(String datasetId, String scenario, String run, Long actor, boolean currentRead) {
        return jdbc.query("SELECT * FROM agent_evaluation_record WHERE dataset_id=? AND scenario_id=? AND run_key=? AND user_id=?"
                + (currentRead ? " FOR UPDATE" : ""), RECORD, datasetId, scenario, run, actor);
    }
    public EvaluationRecord detail(String id, Long actor) {
        actor(actor);
        var rows = jdbc.query("SELECT * FROM agent_evaluation_record WHERE id=? AND user_id=?", RECORD, id, actor);
        if (rows.isEmpty()) throw new BusinessException(404, "评测记录不存在");
        return rows.getFirst();
    }
    public List<RecordSummary> records(String datasetId, Long actor) {
        dataset(datasetId, actor);
        return jdbc.query("SELECT id,dataset_id,scenario_id,run_key,status,created_at FROM agent_evaluation_record WHERE dataset_id=? AND user_id=? ORDER BY created_at DESC,id DESC LIMIT 100",
                (rs, n) -> new RecordSummary(rs.getString("id"), rs.getString("dataset_id"), rs.getString("scenario_id"),
                        rs.getString("run_key"), rs.getString("status"), rs.getTimestamp("created_at").toLocalDateTime()), datasetId, actor);
    }
}
