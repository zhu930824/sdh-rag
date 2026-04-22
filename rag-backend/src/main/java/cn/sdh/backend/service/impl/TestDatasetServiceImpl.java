package cn.sdh.backend.service.impl;

import cn.sdh.backend.common.context.UserContext;
import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.entity.TestDataset;
import cn.sdh.backend.entity.TestDatasetItem;
import cn.sdh.backend.mapper.TestDatasetItemMapper;
import cn.sdh.backend.mapper.TestDatasetMapper;
import cn.sdh.backend.service.TestDatasetService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TestDatasetServiceImpl implements TestDatasetService {

    private static final String DATASETS_PATH = "datasets/";

    private final TestDatasetMapper datasetMapper;
    private final TestDatasetItemMapper itemMapper;
    private final ObjectMapper objectMapper;

    public TestDatasetServiceImpl(TestDatasetMapper datasetMapper,
                                  TestDatasetItemMapper itemMapper,
                                  ObjectMapper objectMapper) {
        this.datasetMapper = datasetMapper;
        this.itemMapper = itemMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public List<TestDataset> listDatasets() {
        List<TestDataset> datasets = datasetMapper.selectList(
                new LambdaQueryWrapper<TestDataset>()
                        .orderByDesc(TestDataset::getDatasetType)
                        .orderByDesc(TestDataset::getCreateTime)
        );

        // 同步内置数据集的最新统计（从文件重新计算）
        for (TestDataset ds : datasets) {
            if ("builtin".equals(ds.getDatasetType()) && ds.getFileName() != null) {
                syncBuiltinStats(ds);
            }
        }

        return datasets;
    }

    @Override
    public TestDataset getDatasetDetail(Long datasetId) {
        TestDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset != null && "builtin".equals(dataset.getDatasetType())) {
            syncBuiltinStats(dataset);
        }
        return dataset;
    }

    @Override
    public List<TestDatasetItem> getDatasetItems(Long datasetId) {
        TestDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            return Collections.emptyList();
        }

        // 内置数据集从文件加载
        if ("builtin".equals(dataset.getDatasetType()) && dataset.getFileName() != null) {
            return loadBuiltinItems(dataset);
        }

        // 自定义数据集从数据库加载
        return itemMapper.selectList(
                new LambdaQueryWrapper<TestDatasetItem>()
                        .eq(TestDatasetItem::getDatasetId, datasetId)
                        .orderByAsc(TestDatasetItem::getSortOrder)
                        .orderByAsc(TestDatasetItem::getId)
        );
    }

    @Override
    @Transactional
    public TestDataset createDataset(String name, String description) {
        Long userId = UserContext.getCurrentUserId();

        TestDataset dataset = new TestDataset();
        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setItemCount(0);
        dataset.setNegativeCount(0);
        dataset.setDatasetType("custom");
        dataset.setUserId(userId);
        dataset.setCreateTime(LocalDateTime.now());
        dataset.setUpdateTime(LocalDateTime.now());

        datasetMapper.insert(dataset);
        return dataset;
    }

    @Override
    @Transactional
    public void updateDataset(Long datasetId, String name, String description) {
        TestDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("数据集不存在");
        }
        if ("builtin".equals(dataset.getDatasetType())) {
            throw new RuntimeException("内置数据集不可编辑");
        }

        dataset.setName(name);
        dataset.setDescription(description);
        dataset.setUpdateTime(LocalDateTime.now());
        datasetMapper.updateById(dataset);
    }

    @Override
    @Transactional
    public void deleteDataset(Long datasetId) {
        TestDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("数据集不存在");
        }
        if ("builtin".equals(dataset.getDatasetType())) {
            throw new RuntimeException("内置数据集不可删除");
        }

        itemMapper.delete(new LambdaQueryWrapper<TestDatasetItem>()
                .eq(TestDatasetItem::getDatasetId, datasetId));
        datasetMapper.deleteById(datasetId);
    }

    @Override
    @Transactional
    public void addItems(Long datasetId, List<EvaluationQaItem> items) {
        TestDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("数据集不存在");
        }
        if ("builtin".equals(dataset.getDatasetType())) {
            throw new RuntimeException("内置数据集不可编辑");
        }

        int negativeCount = 0;
        int sortOrder = dataset.getItemCount() != null ? dataset.getItemCount() : 0;

        for (EvaluationQaItem item : items) {
            if (item.getQuestion() == null || item.getQuestion().trim().isEmpty()) {
                continue;
            }

            TestDatasetItem datasetItem = new TestDatasetItem();
            datasetItem.setDatasetId(datasetId);
            datasetItem.setQuestion(item.getQuestion().trim());
            datasetItem.setExpectedAnswer(item.getExpectedAnswer() != null ? item.getExpectedAnswer().trim() : "");
            datasetItem.setSourceChunkId(item.getSourceChunkId());
            datasetItem.setSourceDocumentId(item.getSourceDocumentId());
            datasetItem.setIsNegative(item.getIsNegative() != null ? item.getIsNegative() : false);
            datasetItem.setExternalId(item.getExternalId());
            datasetItem.setSortOrder(sortOrder++);
            datasetItem.setCreateTime(LocalDateTime.now());

            if (Boolean.TRUE.equals(datasetItem.getIsNegative())) {
                negativeCount++;
            }

            itemMapper.insert(datasetItem);
        }

        // 更新统计
        dataset.setItemCount((dataset.getItemCount() != null ? dataset.getItemCount() : 0) + items.size());
        dataset.setNegativeCount((dataset.getNegativeCount() != null ? dataset.getNegativeCount() : 0) + negativeCount);
        dataset.setUpdateTime(LocalDateTime.now());
        datasetMapper.updateById(dataset);
    }

    @Override
    @Transactional
    public void removeItem(Long datasetId, Long itemId) {
        TestDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("数据集不存在");
        }
        if ("builtin".equals(dataset.getDatasetType())) {
            throw new RuntimeException("内置数据集不可编辑");
        }

        TestDatasetItem item = itemMapper.selectById(itemId);
        if (item == null || !item.getDatasetId().equals(datasetId)) {
            throw new RuntimeException("问题不存在");
        }

        itemMapper.deleteById(itemId);

        // 更新统计
        int itemCount = Math.max(0, (dataset.getItemCount() != null ? dataset.getItemCount() : 0) - 1);
        int negativeCount = dataset.getNegativeCount() != null ? dataset.getNegativeCount() : 0;
        if (Boolean.TRUE.equals(item.getIsNegative())) {
            negativeCount = Math.max(0, negativeCount - 1);
        }

        dataset.setItemCount(itemCount);
        dataset.setNegativeCount(negativeCount);
        dataset.setUpdateTime(LocalDateTime.now());
        datasetMapper.updateById(dataset);
    }

    @Override
    @Transactional
    public void updateItem(Long datasetId, Long itemId, TestDatasetItem updatedItem) {
        TestDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("数据集不存在");
        }
        if ("builtin".equals(dataset.getDatasetType())) {
            throw new RuntimeException("内置数据集不可编辑");
        }

        TestDatasetItem item = itemMapper.selectById(itemId);
        if (item == null || !item.getDatasetId().equals(datasetId)) {
            throw new RuntimeException("问题不存在");
        }

        // 统计负样本变化
        boolean wasNegative = Boolean.TRUE.equals(item.getIsNegative());
        boolean isNegative = Boolean.TRUE.equals(updatedItem.getIsNegative());

        item.setQuestion(updatedItem.getQuestion());
        item.setExpectedAnswer(updatedItem.getExpectedAnswer());
        item.setIsNegative(updatedItem.getIsNegative());
        item.setSourceChunkId(updatedItem.getSourceChunkId());
        item.setExternalId(updatedItem.getExternalId());

        itemMapper.updateById(item);

        // 负样本数变化时更新统计
        if (wasNegative != isNegative) {
            int negativeCount = dataset.getNegativeCount() != null ? dataset.getNegativeCount() : 0;
            if (isNegative) {
                negativeCount++;
            } else {
                negativeCount = Math.max(0, negativeCount - 1);
            }
            dataset.setNegativeCount(negativeCount);
            dataset.setUpdateTime(LocalDateTime.now());
            datasetMapper.updateById(dataset);
        }
    }

    @Override
    @Transactional
    public void addItem(Long datasetId, TestDatasetItem item) {
        TestDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            throw new RuntimeException("数据集不存在");
        }
        if ("builtin".equals(dataset.getDatasetType())) {
            throw new RuntimeException("内置数据集不可编辑");
        }

        item.setDatasetId(datasetId);
        item.setSortOrder(dataset.getItemCount() != null ? dataset.getItemCount() : 0);
        item.setCreateTime(LocalDateTime.now());
        if (item.getIsNegative() == null) {
            item.setIsNegative(false);
        }

        itemMapper.insert(item);

        // 更新统计
        dataset.setItemCount((dataset.getItemCount() != null ? dataset.getItemCount() : 0) + 1);
        if (Boolean.TRUE.equals(item.getIsNegative())) {
            dataset.setNegativeCount((dataset.getNegativeCount() != null ? dataset.getNegativeCount() : 0) + 1);
        }
        dataset.setUpdateTime(LocalDateTime.now());
        datasetMapper.updateById(dataset);
    }

    @Override
    @Transactional
    public TestDataset importDataset(String name, String description, List<EvaluationQaItem> items) {
        TestDataset dataset = createDataset(name, description);
        if (items != null && !items.isEmpty()) {
            addItems(dataset.getId(), items);
        }
        return datasetMapper.selectById(dataset.getId());
    }

    @Override
    public List<EvaluationQaItem> getDatasetQaItems(Long datasetId) {
        TestDataset dataset = datasetMapper.selectById(datasetId);
        if (dataset == null) {
            return Collections.emptyList();
        }

        // 内置数据集从文件加载
        if ("builtin".equals(dataset.getDatasetType()) && dataset.getFileName() != null) {
            return loadBuiltinQaItems(dataset.getFileName());
        }

        // 自定义数据集从数据库加载
        List<TestDatasetItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<TestDatasetItem>()
                        .eq(TestDatasetItem::getDatasetId, datasetId)
                        .orderByAsc(TestDatasetItem::getSortOrder)
                        .orderByAsc(TestDatasetItem::getId)
        );

        List<EvaluationQaItem> result = new ArrayList<>();
        for (TestDatasetItem item : items) {
            EvaluationQaItem qaItem = new EvaluationQaItem();
            qaItem.setQuestion(item.getQuestion());
            qaItem.setExpectedAnswer(item.getExpectedAnswer());
            qaItem.setSourceChunkId(item.getSourceChunkId());
            qaItem.setSourceDocumentId(item.getSourceDocumentId());
            qaItem.setIsNegative(item.getIsNegative());
            qaItem.setExternalId(item.getExternalId());
            result.add(qaItem);
        }

        return result;
    }

    /**
     * 从 classpath 加载内置数据集的QA项
     */
    private List<EvaluationQaItem> loadBuiltinQaItems(String fileName) {
        try {
            Resource resource = new ClassPathResource(DATASETS_PATH + fileName);
            if (!resource.exists()) {
                return Collections.emptyList();
            }

            try (InputStream is = resource.getInputStream()) {
                Map<String, Object> dataset = objectMapper.readValue(is, new TypeReference<Map<String, Object>>() {});
                List<Map<String, Object>> items = (List<Map<String, Object>>) dataset.get("items");
                if (items == null) {
                    return Collections.emptyList();
                }

                List<EvaluationQaItem> result = new ArrayList<>();
                for (Map<String, Object> item : items) {
                    EvaluationQaItem qaItem = new EvaluationQaItem();
                    qaItem.setQuestion((String) item.get("question"));
                    qaItem.setExpectedAnswer((String) item.get("expectedAnswer"));
                    qaItem.setIsNegative(Boolean.TRUE.equals(item.get("isNegative")));
                    if (item.get("sourceChunkId") != null) {
                        qaItem.setSourceChunkId((String) item.get("sourceChunkId"));
                    }
                    if (item.get("externalId") != null) {
                        qaItem.setExternalId((String) item.get("externalId"));
                    }
                    result.add(qaItem);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("加载内置数据集失败: {}", fileName, e);
            return Collections.emptyList();
        }
    }

    /**
     * 从 classpath 加载内置数据集的 TestDatasetItem 列表（用于前端展示）
     */
    private List<TestDatasetItem> loadBuiltinItems(TestDataset dataset) {
        List<EvaluationQaItem> qaItems = loadBuiltinQaItems(dataset.getFileName());
        List<TestDatasetItem> result = new ArrayList<>();

        int sortOrder = 0;
        for (EvaluationQaItem qa : qaItems) {
            TestDatasetItem item = new TestDatasetItem();
            item.setDatasetId(dataset.getId());
            item.setQuestion(qa.getQuestion());
            item.setExpectedAnswer(qa.getExpectedAnswer());
            item.setSourceChunkId(qa.getSourceChunkId());
            item.setSourceDocumentId(qa.getSourceDocumentId());
            item.setIsNegative(qa.getIsNegative());
            item.setExternalId(qa.getExternalId());
            item.setSortOrder(sortOrder++);
            result.add(item);
        }

        return result;
    }

    /**
     * 同步内置数据集的统计信息
     */
    private void syncBuiltinStats(TestDataset dataset) {
        try {
            List<EvaluationQaItem> items = loadBuiltinQaItems(dataset.getFileName());
            dataset.setItemCount(items.size());
            dataset.setNegativeCount((int) items.stream()
                    .filter(i -> Boolean.TRUE.equals(i.getIsNegative()))
                    .count());
        } catch (Exception e) {
            log.warn("同步内置数据集统计失败: {}", dataset.getFileName(), e);
        }
    }
}
