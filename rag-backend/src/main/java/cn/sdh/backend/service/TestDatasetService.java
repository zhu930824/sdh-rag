package cn.sdh.backend.service;

import cn.sdh.backend.dto.EvaluationQaItem;
import cn.sdh.backend.entity.TestDataset;
import cn.sdh.backend.entity.TestDatasetItem;

import java.util.List;

/**
 * 测试数据集服务接口
 */
public interface TestDatasetService {

    /**
     * 获取所有数据集列表（包含内置和自定义）
     */
    List<TestDataset> listDatasets();

    /**
     * 获取数据集详情
     */
    TestDataset getDatasetDetail(Long datasetId);

    /**
     * 获取数据集问题列表
     */
    List<TestDatasetItem> getDatasetItems(Long datasetId);

    /**
     * 创建自定义数据集
     */
    TestDataset createDataset(String name, String description);

    /**
     * 更新数据集信息
     */
    void updateDataset(Long datasetId, String name, String description);

    /**
     * 删除数据集
     */
    void deleteDataset(Long datasetId);

    /**
     * 批量添加问题到数据集
     */
    void addItems(Long datasetId, List<EvaluationQaItem> items);

    /**
     * 删除单个问题
     */
    void removeItem(Long datasetId, Long itemId);

    /**
     * 更新单个问题
     */
    void updateItem(Long datasetId, Long itemId, TestDatasetItem item);

    /**
     * 添加单个问题
     */
    void addItem(Long datasetId, TestDatasetItem item);

    /**
     * 从文件导入创建数据集
     */
    TestDataset importDataset(String name, String description, List<EvaluationQaItem> items);

    /**
     * 获取数据集的问题项（用于评估）
     * 支持内置数据集（从文件加载）和自定义数据集（从数据库加载）
     */
    List<EvaluationQaItem> getDatasetQaItems(Long datasetId);
}
