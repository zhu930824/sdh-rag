package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.DatasetInfo;
import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.entity.EvaluationQa;
import cn.sdh.backend.entity.EvaluationTask;
import cn.sdh.backend.service.EvaluationService;
import cn.sdh.backend.utils.EvaluationFileParser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/evaluation")
public class EvaluationController {

    private final EvaluationService evaluationService;

    public EvaluationController(EvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    /**
     * 生成测试集并运行评估
     */
    @PostMapping("/generate-testset")
    public Result<EvaluationTask> generateTestset(@RequestBody Map<String, Object> params) {
        Long knowledgeId = Long.valueOf(params.get("knowledgeId").toString());
        int qaCount = params.containsKey("qaCount") ? Integer.parseInt(params.get("qaCount").toString()) : 10;
        String taskName = params.containsKey("taskName") ? params.get("taskName").toString() : null;

        EvaluationTask task = evaluationService.generateAndRun(knowledgeId, qaCount, taskName);
        return Result.success(task);
    }

    /**
     * 导入外部测试集并运行评估
     */
    @PostMapping("/import")
    public Result<EvaluationTask> importTestset(
            @RequestParam("file") MultipartFile file,
            @RequestParam("knowledgeId") Long knowledgeId,
            @RequestParam(value = "taskName", required = false) String taskName) {

        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return Result.error("文件名不能为空");
        }

        try {
            List<EvaluationQaItem> items;

            String lowerName = originalFilename.toLowerCase();
            if (lowerName.endsWith(".json")) {
                items = EvaluationFileParser.parseJson(file.getInputStream());
            } else if (lowerName.endsWith(".xlsx") || lowerName.endsWith(".xls")) {
                items = EvaluationFileParser.parseExcel(file.getInputStream());
            } else {
                return Result.error("不支持的文件格式，请上传 JSON 或 Excel 文件");
            }

            if (items.isEmpty()) {
                return Result.error("文件中没有有效的测试数据");
            }

            if (taskName == null || taskName.trim().isEmpty()) {
                taskName = "导入测试-" + originalFilename;
            }

            EvaluationTask task = evaluationService.importAndRun(knowledgeId, items, taskName);
            return Result.success(task);

        } catch (RuntimeException e) {
            log.error("导入测试集失败", e);
            return Result.error("文件解析失败: " + e.getMessage());
        } catch (IOException e) {
            log.error("读取文件失败", e);
            return Result.error("读取文件失败");
        }
    }

    /**
     * 下载导入模板
     */
    @GetMapping("/template")
    public void downloadTemplate(HttpServletResponse response) {
        String jsonTemplate = """
                [
                  {
                    "question": "示例问题1",
                    "expectedAnswer": "期望答案1",
                    "sourceChunkId": "",
                    "isNegative": false,
                    "externalId": ""
                  },
                  {
                    "question": "示例问题2（答案不在知识库中）",
                    "expectedAnswer": "",
                    "sourceChunkId": "",
                    "isNegative": true,
                    "externalId": ""
                  }
                ]
                """;

        response.setContentType("application/json");
        response.setHeader("Content-Disposition", "attachment; filename=evaluation_template.json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());

        try (OutputStream os = response.getOutputStream()) {
            os.write(jsonTemplate.getBytes(StandardCharsets.UTF_8));
            os.flush();
        } catch (IOException e) {
            log.error("下载模板失败", e);
        }
    }

    /**
     * 运行内置数据集评估
     */
    @PostMapping("/builtin")
    public Result<EvaluationTask> runBuiltin(@RequestBody Map<String, Object> params) {
        String datasetName = (String) params.get("datasetName");
        Long knowledgeId = Long.valueOf(params.get("knowledgeId").toString());

        if (datasetName == null || datasetName.trim().isEmpty()) {
            return Result.error("请选择数据集");
        }

        EvaluationTask task = evaluationService.runBuiltinDataset(datasetName, knowledgeId);
        return Result.success(task);
    }

    /**
     * 获取内置数据集列表
     */
    @GetMapping("/builtin/list")
    public Result<List<DatasetInfo>> listBuiltinDatasets() {
        List<DatasetInfo> datasets = evaluationService.listBuiltinDatasets();
        return Result.success(datasets);
    }

    /**
     * 获取评估任务详情
     */
    @GetMapping("/task/{id}")
    public Result<EvaluationTask> getTaskDetail(@PathVariable Long id) {
        EvaluationTask task = evaluationService.getTaskDetail(id);
        if (task == null) {
            return Result.error("任务不存在");
        }
        return Result.success(task);
    }

    /**
     * 获取任务的QA列表
     */
    @GetMapping("/task/{id}/qa-list")
    public Result<List<EvaluationQa>> getTaskQaList(@PathVariable Long id) {
        List<EvaluationQa> qaList = evaluationService.getTaskQaList(id);
        return Result.success(qaList);
    }

    /**
     * 获取评估任务列表
     */
    @GetMapping("/list")
    public Result<List<EvaluationTask>> list(@RequestParam(required = false) Long knowledgeId) {
        List<EvaluationTask> tasks;
        if (knowledgeId != null) {
            tasks = evaluationService.listByKnowledgeId(knowledgeId);
        } else {
            tasks = evaluationService.listAll();
        }
        return Result.success(tasks);
    }

    /**
     * 删除评估任务
     */
    @PostMapping("/task/{id}/delete")
    public Result<Void> deleteTask(@PathVariable Long id) {
        evaluationService.deleteTask(id);
        return Result.success();
    }
}
