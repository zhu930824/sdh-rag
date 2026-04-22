package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.DatasetInfo;
import cn.sdh.backend.dto.EvaluationExportOverview;
import cn.sdh.backend.dto.EvaluationExportQaDetail;
import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.entity.EvaluationQa;
import cn.sdh.backend.entity.EvaluationTask;
import com.baomidou.mybatisplus.core.metadata.IPage;
import cn.sdh.backend.service.EvaluationService;
import cn.sdh.backend.utils.EvaluationFileParser;
import com.alibaba.excel.EasyExcel;
import com.alibaba.excel.write.builder.ExcelWriterBuilder;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;

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
     * 运行数据集评估（按数据集ID）
     */
    @PostMapping("/dataset/{datasetId}")
    public Result<EvaluationTask> runDatasetEvaluation(
            @PathVariable Long datasetId,
            @RequestBody Map<String, Object> params) {

        Long knowledgeId = Long.valueOf(params.get("knowledgeId").toString());

        try {
            EvaluationTask task = evaluationService.runDatasetEvaluation(datasetId, knowledgeId);
            return Result.success(task);
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
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
     * 获取评估任务列表（分页）
     */
    @GetMapping("/list")
    public Result<Map<String, Object>> list(
            @RequestParam(required = false) Long knowledgeId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {
        IPage<EvaluationTask> paged = evaluationService.listPaged(knowledgeId, page, pageSize);
        Map<String, Object> result = new HashMap<>();
        result.put("records", paged.getRecords());
        result.put("total", paged.getTotal());
        return Result.success(result);
    }

    /**
     * 删除评估任务
     */
    @PostMapping("/task/{id}/delete")
    public Result<Void> deleteTask(@PathVariable Long id) {
        evaluationService.deleteTask(id);
        return Result.success();
    }

    /**
     * 导出评估报告为 Excel
     */
    @GetMapping("/task/{id}/export")
    public void exportReport(@PathVariable Long id, HttpServletResponse response) {
        EvaluationTask task = evaluationService.getTaskDetail(id);
        if (task == null) {
            response.setStatus(404);
            return;
        }

        List<EvaluationQa> qaList = evaluationService.getTaskQaList(id);

        try {
            String fileName = URLEncoder.encode((task.getTaskName() != null ? task.getTaskName() : "评估报告") + ".xlsx", StandardCharsets.UTF_8);
            response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
            response.setHeader("Content-Disposition", "attachment; filename=" + fileName);

            // Sheet 1: 评估概览
            List<EvaluationExportOverview> overviewList = buildOverviewData(task);

            // Sheet 2: QA详情
            List<EvaluationExportQaDetail> qaDetailList = buildQaDetailData(qaList);

            com.alibaba.excel.ExcelWriter excelWriter = EasyExcel.write(response.getOutputStream()).build();
            try {
                com.alibaba.excel.write.metadata.WriteSheet sheet1 = EasyExcel.writerSheet(0, "评估概览")
                        .head(EvaluationExportOverview.class).build();
                excelWriter.write(overviewList, sheet1);

                com.alibaba.excel.write.metadata.WriteSheet sheet2 = EasyExcel.writerSheet(1, "QA详情")
                        .head(EvaluationExportQaDetail.class).build();
                excelWriter.write(qaDetailList, sheet2);
            } finally {
                excelWriter.finish();
            }

        } catch (Exception e) {
            log.error("导出评估报告失败", e);
        }
    }

    private List<EvaluationExportOverview> buildOverviewData(EvaluationTask task) {
        List<EvaluationExportOverview> list = new ArrayList<>();
        list.add(new EvaluationExportOverview("任务名称", task.getTaskName() != null ? task.getTaskName() : "-"));
        list.add(new EvaluationExportOverview("知识库", task.getKnowledgeName() != null ? task.getKnowledgeName() : "-"));
        list.add(new EvaluationExportOverview("创建时间", task.getCreateTime() != null ? task.getCreateTime().toString() : "-"));
        list.add(new EvaluationExportOverview("数据集类型", task.getDatasetType() != null ? task.getDatasetType() : "-"));
        list.add(new EvaluationExportOverview("QA数量", String.valueOf(task.getQaCount() != null ? task.getQaCount() : 0)));
        list.add(new EvaluationExportOverview("分块命中率", task.getHitRate() != null ? (task.getHitRate().doubleValue() * 100) + "%" : "-"));
        list.add(new EvaluationExportOverview("文档命中率", task.getDocHitRate() != null ? (task.getDocHitRate().doubleValue() * 100) + "%" : "-"));
        list.add(new EvaluationExportOverview("MRR", task.getMrr() != null ? task.getMrr().toPlainString() : "-"));
        list.add(new EvaluationExportOverview("平均命中排名", task.getAvgHitRank() != null ? task.getAvgHitRank().toPlainString() : "-"));
        list.add(new EvaluationExportOverview("负样本数量", String.valueOf(task.getNegativeCount() != null ? task.getNegativeCount() : 0)));
        list.add(new EvaluationExportOverview("负样本错误命中率", task.getNegativeHitRate() != null ? (task.getNegativeHitRate().doubleValue() * 100) + "%" : "-"));
        list.add(new EvaluationExportOverview("状态", formatStatus(task.getStatus())));
        return list;
    }

    private List<EvaluationExportQaDetail> buildQaDetailData(List<EvaluationQa> qaList) {
        List<EvaluationExportQaDetail> list = new ArrayList<>();
        int index = 1;
        for (EvaluationQa qa : qaList) {
            EvaluationExportQaDetail detail = new EvaluationExportQaDetail();
            detail.setIndex(index++);
            detail.setQuestion(qa.getQuestion());
            detail.setExpectedAnswer(qa.getExpectedAnswer());
            detail.setSampleType(Boolean.TRUE.equals(qa.getIsNegative()) ? "负样本" : "正样本");

            if (Boolean.TRUE.equals(qa.getIsNegative())) {
                String retrieved = qa.getRetrievedChunkIds();
                boolean hasRetrieved = retrieved != null && !retrieved.equals("[]") && !retrieved.isEmpty();
                detail.setHitStatus(hasRetrieved ? "错误命中" : "正确未命中");
                detail.setDocHitStatus(hasRetrieved ? "错误命中" : "正确未命中");
            } else {
                detail.setHitStatus(Boolean.TRUE.equals(qa.getHit()) ? "命中" : "未命中");
                detail.setDocHitStatus(Boolean.TRUE.equals(qa.getDocHit()) ? "命中" : "未命中");
            }

            detail.setHitRank(qa.getHitRank() != null ? String.valueOf(qa.getHitRank()) : "-");
            detail.setDocHitRank(qa.getDocHitRank() != null ? String.valueOf(qa.getDocHitRank()) : "-");
            detail.setRetrievedChunkIds(qa.getRetrievedChunkIds());

            list.add(detail);
        }
        return list;
    }

    private String formatStatus(Integer status) {
        if (status == null) return "未知";
        return switch (status) {
            case 0 -> "待运行";
            case 1 -> "运行中";
            case 2 -> "完成";
            case 3 -> "失败";
            default -> "未知";
        };
    }
}
