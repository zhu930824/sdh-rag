package cn.sdh.backend.controller;

import cn.sdh.backend.common.result.Result;
import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.entity.TestDataset;
import cn.sdh.backend.entity.TestDatasetItem;
import cn.sdh.backend.service.TestDatasetService;
import cn.sdh.backend.utils.EvaluationFileParser;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/dataset")
public class TestDatasetController {

    private final TestDatasetService datasetService;

    public TestDatasetController(TestDatasetService datasetService) {
        this.datasetService = datasetService;
    }

    /**
     * 获取所有数据集列表
     */
    @GetMapping("/list")
    public Result<List<TestDataset>> listDatasets() {
        List<TestDataset> datasets = datasetService.listDatasets();
        return Result.success(datasets);
    }

    /**
     * 获取数据集详情
     */
    @GetMapping("/{id}")
    public Result<TestDataset> getDatasetDetail(@PathVariable Long id) {
        TestDataset dataset = datasetService.getDatasetDetail(id);
        if (dataset == null) {
            return Result.error("数据集不存在");
        }
        return Result.success(dataset);
    }

    /**
     * 获取数据集问题列表
     */
    @GetMapping("/{id}/items")
    public Result<List<TestDatasetItem>> getDatasetItems(@PathVariable Long id) {
        List<TestDatasetItem> items = datasetService.getDatasetItems(id);
        return Result.success(items);
    }

    /**
     * 创建数据集
     */
    @PostMapping("/create")
    public Result<TestDataset> createDataset(@RequestBody Map<String, String> params) {
        String name = params.get("name");
        String description = params.get("description");

        if (name == null || name.trim().isEmpty()) {
            return Result.error("数据集名称不能为空");
        }

        try {
            TestDataset dataset = datasetService.createDataset(name.trim(), description);
            return Result.success(dataset);
        } catch (Exception e) {
            log.error("创建数据集失败", e);
            return Result.error("创建失败: " + e.getMessage());
        }
    }

    /**
     * 更新数据集信息
     */
    @PutMapping("/{id}")
    public Result<Void> updateDataset(@PathVariable Long id, @RequestBody Map<String, String> params) {
        String name = params.get("name");
        String description = params.get("description");

        try {
            datasetService.updateDataset(id, name, description);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除数据集
     */
    @DeleteMapping("/{id}")
    public Result<Void> deleteDataset(@PathVariable Long id) {
        try {
            datasetService.deleteDataset(id);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 批量添加问题
     */
    @PostMapping("/{id}/items")
    public Result<Void> addItems(@PathVariable Long id, @RequestBody List<EvaluationQaItem> items) {
        if (items == null || items.isEmpty()) {
            return Result.error("问题列表不能为空");
        }

        try {
            datasetService.addItems(id, items);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 添加单个问题
     */
    @PostMapping("/{id}/item")
    public Result<Void> addItem(@PathVariable Long id, @RequestBody TestDatasetItem item) {
        if (item.getQuestion() == null || item.getQuestion().trim().isEmpty()) {
            return Result.error("问题内容不能为空");
        }

        try {
            datasetService.addItem(id, item);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 更新单个问题
     */
    @PutMapping("/{id}/items/{itemId}")
    public Result<Void> updateItem(@PathVariable Long id, @PathVariable Long itemId, @RequestBody TestDatasetItem item) {
        try {
            datasetService.updateItem(id, itemId, item);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 删除单个问题
     */
    @DeleteMapping("/{id}/items/{itemId}")
    public Result<Void> removeItem(@PathVariable Long id, @PathVariable Long itemId) {
        try {
            datasetService.removeItem(id, itemId);
            return Result.success();
        } catch (RuntimeException e) {
            return Result.error(e.getMessage());
        }
    }

    /**
     * 从文件导入创建数据集
     */
    @PostMapping("/import")
    public Result<TestDataset> importDataset(
            @RequestParam("file") MultipartFile file,
            @RequestParam("name") String name,
            @RequestParam(value = "description", required = false) String description) {

        if (file.isEmpty()) {
            return Result.error("文件不能为空");
        }

        if (name == null || name.trim().isEmpty()) {
            return Result.error("数据集名称不能为空");
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

            TestDataset dataset = datasetService.importDataset(name.trim(), description, items);
            return Result.success(dataset);

        } catch (RuntimeException e) {
            log.error("导入数据集失败", e);
            return Result.error("文件解析失败: " + e.getMessage());
        } catch (IOException e) {
            log.error("读取文件失败", e);
            return Result.error("读取文件失败");
        }
    }

    /**
     * 导出数据集为JSON文件
     */
    @GetMapping("/{id}/export")
    public void exportDataset(@PathVariable Long id, HttpServletResponse response) {
        TestDataset dataset = datasetService.getDatasetDetail(id);
        if (dataset == null) {
            response.setStatus(404);
            return;
        }

        List<EvaluationQaItem> items = datasetService.getDatasetQaItems(id);

        response.setContentType("application/json");
        response.setHeader("Content-Disposition", "attachment; filename=" + dataset.getName() + ".json");
        response.setCharacterEncoding("UTF-8");

        try {
            String json = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(items);
            response.getWriter().write(json);
            response.getWriter().flush();
        } catch (IOException e) {
            log.error("导出数据集失败", e);
        }
    }
}