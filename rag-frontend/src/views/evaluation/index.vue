<template>
  <div class="evaluation-page">
    <!-- Tab 切换 -->
    <a-card style="margin-bottom: 16px">
      <a-alert v-if="activeTab !== 'agent'" type="info" show-icon class="evaluation-guide-tip" message="推荐顺序：先跑检索评测，再跑生成/完整评测；Full 模式会调用评审模型，成本和耗时更高。" />
      <div v-if="activeTab !== 'agent'" class="evaluation-config">
        <a-form layout="inline">
          <a-form-item label="评测层级">
            <a-select v-model:value="runConfig.evaluationMode" style="width: 150px">
              <a-select-option value="RETRIEVAL">检索评测</a-select-option>
              <a-select-option value="GENERATION">生成评测</a-select-option>
              <a-select-option value="FULL">完整评测</a-select-option>
            </a-select>
          </a-form-item>
          <a-form-item label="Top K"><a-input-number v-model:value="runConfig.topK" :min="1" :max="20" /></a-form-item>
          <a-form-item v-if="runConfig.evaluationMode !== 'RETRIEVAL'" label="评审模型">
            <a-input v-model:value="runConfig.judgeModel" placeholder="留空使用默认模型" style="width: 180px" />
          </a-form-item>
          <a-form-item label="对比基线">
            <a-select v-model:value="runConfig.baselineTaskId" allow-clear placeholder="不对比" style="width: 220px">
              <a-select-option v-for="task in baselineOptions" :key="task.id" :value="task.id">#{{ task.id }} {{ task.taskName }}</a-select-option>
            </a-select>
          </a-form-item>
        </a-form>
      </div>
      <a-tabs v-model:activeKey="activeTab">
        <a-tab-pane key="agent" tab="Agent 场景与结果">
          <AgentEvaluationPanel />
        </a-tab-pane>
        <a-tab-pane key="generate" tab="生成测试集">
          <a-form layout="inline" :model="generateForm">
            <a-form-item label="知识库">
              <a-select
                v-model:value="generateForm.knowledgeId"
                style="width: 240px"
                placeholder="请选择知识库"
                :loading="loadingKbList"
              >
                <a-select-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
                  {{ kb.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="QA数量">
              <a-input-number v-model:value="generateForm.qaCount" :min="3" :max="50" style="width: 120px" />
            </a-form-item>
            <a-form-item label="任务名称">
              <a-input v-model:value="generateForm.taskName" placeholder="可选" style="width: 200px" />
            </a-form-item>
            <a-form-item>
              <a-button type="primary" :loading="generating" :disabled="!generateForm.knowledgeId" @click="handleGenerate">
                生成并评估
              </a-button>
            </a-form-item>
          </a-form>
        </a-tab-pane>

        <a-tab-pane key="import" tab="导入测试集">
          <a-form layout="inline">
            <a-form-item label="知识库">
              <a-select
                v-model:value="importForm.knowledgeId"
                style="width: 240px"
                placeholder="请选择知识库"
                :loading="loadingKbList"
              >
                <a-select-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
                  {{ kb.name }}
                </a-select-option>
              </a-select>
            </a-form-item>
            <a-form-item label="任务名称">
              <a-input v-model:value="importForm.taskName" placeholder="可选" style="width: 200px" />
            </a-form-item>
            <a-form-item>
              <a-button type="link" @click="handleDownloadTemplate">
                <DownloadOutlined /> 下载模板
              </a-button>
            </a-form-item>
          </a-form>
          <a-upload-dragger
            v-model:file-list="importFileList"
            :before-upload="beforeImportUpload"
            :max-count="1"
            accept=".json,.xlsx,.xls"
            style="margin-top: 16px; max-height: 160px; overflow: hidden;"
          >
            <p class="ant-upload-drag-icon"><InboxOutlined /></p>
            <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p class="ant-upload-hint">支持 JSON 或 Excel 格式的测试集文件</p>
          </a-upload-dragger>
          <a-button
            type="primary"
            :loading="importing"
            :disabled="!importForm.knowledgeId || importFileList.length === 0"
            style="margin-top: 16px"
            @click="handleImport"
          >
            导入并评估
          </a-button>
        </a-tab-pane>

        <a-tab-pane key="dataset" tab="测试数据集">
          <div style="margin-bottom: 16px; display: flex; justify-content: space-between; align-items: center;">
            <a-select
              v-model:value="datasetForm.knowledgeId"
              style="width: 240px"
              placeholder="选择知识库（用于运行评估）"
              :loading="loadingKbList"
            >
              <a-select-option v-for="kb in knowledgeBases" :key="kb.id" :value="kb.id">
                {{ kb.name }}
              </a-select-option>
            </a-select>
            <a-space>
              <a-button type="primary" @click="showCreateDatasetModal">
                <PlusOutlined /> 新建数据集
              </a-button>
              <a-button @click="showImportDatasetModal">
                <ImportOutlined /> 导入数据集
              </a-button>
            </a-space>
          </div>
          <a-spin :spinning="loadingDatasets">
            <div class="dataset-cards">
              <div v-for="ds in datasets" :key="ds.id" class="dataset-card">
                <div class="dataset-header">
                  <DatabaseOutlined class="dataset-icon" :style="{ color: ds.datasetType === 'builtin' ? '#1890ff' : '#52c41a' }" />
                  <span class="dataset-name">{{ ds.name }}</span>
                  <a-tag :color="ds.datasetType === 'builtin' ? 'blue' : 'green'" style="margin-left: 8px;">
                    {{ ds.datasetType === 'builtin' ? '内置' : '自定义' }}
                  </a-tag>
                </div>
                <div class="dataset-desc">{{ ds.description || '暂无描述' }}</div>
                <div class="dataset-stats">
                  <a-tag color="blue">{{ ds.itemCount }} 条问题</a-tag>
                  <a-tag color="purple">v{{ ds.version || 1 }}</a-tag>
                  <a-tag v-if="ds.negativeCount > 0" color="orange">{{ ds.negativeCount }} 条负样本</a-tag>
                </div>
                <div class="dataset-actions">
                  <a-button
                    type="primary"
                    size="small"
                    :loading="runningDataset === ds.id"
                    :disabled="!datasetForm.knowledgeId"
                    @click="handleRunDataset(ds.id)"
                  >
                    运行评估
                  </a-button>
                  <a-button size="small" @click="handlePreviewDataset(ds)">预览</a-button>
                  <a-button size="small" @click="handleCloneDataset(ds)" v-if="ds.datasetType === 'builtin'">复制并标注</a-button>
                  <a-button size="small" @click="handleEditDataset(ds)" v-if="ds.datasetType !== 'builtin'">编辑</a-button>
                  <a-popconfirm title="确定删除此数据集？" @confirm="handleDeleteDataset(ds)" v-if="ds.datasetType !== 'builtin'">
                    <a-button size="small" danger>删除</a-button>
                  </a-popconfirm>
                </div>
              </div>
              <a-empty v-if="datasets.length === 0 && !loadingDatasets" description="暂无数据集" />
            </div>
          </a-spin>
        </a-tab-pane>
      </a-tabs>
    </a-card>

    <!-- 评估任务列表 -->
    <a-card v-if="activeTab !== 'agent'" title="评估记录">
      <a-table
        :columns="taskColumns"
        :data-source="tasks"
        :loading="loadingTasks"
        row-key="id"
        :pagination="taskPagination"
        @change="handleTaskTableChange"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'status'">
            <a-tag :color="statusColor(record.status, record.jobStatus)">{{ statusText(record.status, record.jobStatus) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'datasetType'">
            <a-tag :color="datasetTypeColor(record.datasetType)">{{ datasetTypeText(record.datasetType) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'hitRate'">
            <span v-if="record.recallAtK != null">{{ (record.recallAtK * 100).toFixed(1) }}%</span>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'faithfulness'">
            <span v-if="record.faithfulness != null">{{ (record.faithfulness * 100).toFixed(1) }}%</span><span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'regressionStatus'">
            <a-tag :color="record.regressionStatus === 'PASSED' ? 'green' : record.regressionStatus === 'FAILED' ? 'red' : 'default'">{{ regressionText(record.regressionStatus) }}</a-tag>
          </template>
          <template v-else-if="column.key === 'negativeHitRate'">
            <span v-if="record.negativeCount > 0 && record.negativeHitRate !== null">
              {{ (record.negativeHitRate * 100).toFixed(1) }}%
            </span>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'mrr'">
            <span v-if="record.mrr !== null">{{ record.mrr.toFixed(3) }}</span>
            <span v-else>-</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-space>
              <a-button type="link" size="small" @click="handleViewDetail(record)">详情</a-button>
              <a-button type="link" size="small" @click="handleExportReport(record)">导出</a-button>
              <a-popconfirm v-if="record.jobId && ['PENDING', 'RUNNING', 'RETRY_WAIT'].includes(record.jobStatus || '')" title="确定取消该评测任务？" @confirm="handleCancel(record)">
                <a-button type="link" size="small" danger>取消</a-button>
              </a-popconfirm>
              <a-button v-if="record.jobId && ['FAILED', 'DEAD_LETTER', 'CANCELLED'].includes(record.jobStatus || '')" type="link" size="small" @click="handleReplay(record)">重新执行</a-button>
              <a-popconfirm v-if="record.canDelete && record.status !== 0 && record.status !== 1" title="确定删除？" @confirm="handleDelete(record)">
                <a-button type="link" size="small" danger>删除</a-button>
              </a-popconfirm>
            </a-space>
          </template>
        </template>
      </a-table>
    </a-card>

    <!-- 评估详情抽屉 -->
    <a-drawer v-model:open="detailVisible" title="评估详情" width="960" :destroy-on-close="true" root-class-name="evaluation-detail-drawer">
      <template v-if="currentTask">
        <!-- 任务概览横幅 -->
        <div class="task-banner">
          <div class="banner-content">
            <div class="banner-left">
              <div class="banner-header">
                <div class="task-icon"><FileTextOutlined /></div>
                <div class="task-info">
                  <h2 class="task-name">{{ currentTask.taskName || '未命名任务' }}</h2>
                  <div class="task-meta">
                    <span class="meta-dot"><DatabaseOutlined /> {{ currentTask.knowledgeName || '未知知识库' }}</span>
                    <span v-if="currentTask.datasetId" class="meta-dot">数据集 #{{ currentTask.datasetId }} · v{{ currentTask.datasetVersion || 1 }}</span>
                    <span class="meta-separator">|</span>
                    <span class="meta-dot"><ClockCircleOutlined /> {{ currentTask.createTime }}</span>
                    <span class="meta-separator">|</span>
                    <a-tag :color="datasetTypeColor(currentTask.datasetType)" style="margin-left: 4px">
                      {{ datasetTypeText(currentTask.datasetType) }}
                    </a-tag>
                  </div>
                </div>
              </div>
            </div>
            <div class="banner-right">
              <div class="status-badge" :class="'status-' + currentTask.status">
                <span class="status-dot"></span>
                {{ statusText(currentTask.status) }}
              </div>
              <a-button type="primary" size="small" style="margin-left: 12px;" @click="handleExportReport(currentTask)">
                <DownloadOutlined /> 导出报告
              </a-button>
            </div>
          </div>
        </div>

        <ExecutionHistory :key="currentTask.id" :task-id="currentTask.id" @created="onHistoricalReplayCreated" />

        <div class="layered-metrics-panel">
          <div class="panel-title">检索层指标（Top {{ currentTask.topK || 5 }}，已标注 {{ currentTask.labeledCount || 0 }} 条）</div>
          <a-alert v-if="!currentTask.labeledCount" type="warning" show-icon message="当前数据集没有相关文档标注，经典检索指标不会参与统计。" style="margin-bottom: 12px" />
          <a-row :gutter="12">
            <a-col v-for="metric in retrievalMetrics" :key="metric.label" :span="6"><a-statistic :title="metric.label" :value="metric.value" :precision="3" /></a-col>
          </a-row>
        </div>

        <div v-if="currentTask.evaluationMode === 'GENERATION' || currentTask.evaluationMode === 'FULL'" class="layered-metrics-panel">
          <div class="panel-title">生成层指标（LLM-as-Judge，已评审 {{ currentTask.judgedCount || 0 }} 条）</div>
          <a-row :gutter="12">
            <a-col v-for="metric in generationMetrics" :key="metric.label" :span="6"><a-statistic :title="metric.label" :value="metric.value * 100" suffix="%" :precision="1" /></a-col>
          </a-row>
        </div>

        <!-- 核心指标 -->
        <div class="metrics-panel">
          <div class="panel-title">核心指标</div>
          <div class="metrics-row">
            <div class="metric-item metric-hit">
              <div class="metric-visual">
                <svg viewBox="0 0 36 36" class="metric-ring">
                  <path class="ring-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                  <path class="ring-fill" :stroke-dasharray="`${(currentTask.hitRate || 0) * 100}, 100`" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                </svg>
                <div class="metric-ring-value">{{ currentTask.hitRate ? (currentTask.hitRate * 100).toFixed(0) : 0 }}<span class="percent">%</span></div>
              </div>
              <div class="metric-info">
                <div class="metric-name">分块命中率</div>
                <div class="metric-desc">精确匹配</div>
              </div>
            </div>
            <div class="metric-divider"></div>
            <div class="metric-item metric-doc-hit">
              <div class="metric-visual">
                <svg viewBox="0 0 36 36" class="metric-ring">
                  <path class="ring-bg" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                  <path class="ring-fill ring-fill-orange" :stroke-dasharray="`${(currentTask.docHitRate || 0) * 100}, 100`" d="M18 2.0845 a 15.9155 15.9155 0 0 1 0 31.831 a 15.9155 15.9155 0 0 1 0 -31.831" />
                </svg>
                <div class="metric-ring-value metric-ring-value-orange">{{ currentTask.docHitRate ? (currentTask.docHitRate * 100).toFixed(0) : 0 }}<span class="percent">%</span></div>
              </div>
              <div class="metric-info">
                <div class="metric-name">文档命中率</div>
                <div class="metric-desc">同文档任意分块</div>
              </div>
            </div>
            <div class="metric-divider"></div>
            <div class="metric-item metric-mrr">
              <div class="metric-visual">
                <div class="metric-big-value">{{ currentTask.mrr ? currentTask.mrr.toFixed(3) : '-' }}</div>
              </div>
              <div class="metric-info">
                <div class="metric-name">MRR</div>
                <div class="metric-desc">平均倒数排名</div>
              </div>
            </div>
            <div class="metric-divider"></div>
            <div class="metric-item metric-rank">
              <div class="metric-visual">
                <div class="metric-big-value highlight">{{ currentTask.avgHitRank ? currentTask.avgHitRank.toFixed(1) : '-' }}</div>
              </div>
              <div class="metric-info">
                <div class="metric-name">平均排名</div>
                <div class="metric-desc">命中位置</div>
              </div>
            </div>
            <div class="metric-divider"></div>
            <div class="metric-item metric-count">
              <div class="metric-visual">
                <div class="metric-big-value purple">{{ currentTask.qaCount || 0 }}</div>
              </div>
              <div class="metric-info">
                <div class="metric-name">测试问题</div>
                <div class="metric-desc">QA 数量</div>
              </div>
            </div>
          </div>
        </div>

        <!-- 负样本指标 -->
        <div class="negative-panel" v-if="currentTask.negativeCount > 0">
          <div class="panel-title">负样本指标</div>
          <div class="negative-content">
            <div class="negative-stat">
              <span class="negative-label">负样本数量</span>
              <span class="negative-value">{{ currentTask.negativeCount }}</span>
            </div>
            <div class="negative-stat">
              <span class="negative-label">错误命中率</span>
              <a-tooltip title="负样本被错误命中的比例，应越低越好">
                <span class="negative-value" :class="{ 'value-good': currentTask.negativeHitRate < 0.3, 'value-bad': currentTask.negativeHitRate > 0.5 }">
                  {{ currentTask.negativeHitRate ? (currentTask.negativeHitRate * 100).toFixed(1) : 0 }}%
                </span>
              </a-tooltip>
            </div>
            <div class="negative-desc">
              <InfoCircleOutlined /> 负样本问题答案不在知识库中，理想情况下不应检索到任何结果
            </div>
          </div>
        </div>

        <!-- 命中统计 -->
        <div class="hit-stats" v-if="qaList.length > 0">
          <div class="stats-bar">
            <div class="stats-segment stats-hit" :style="{ width: (hitCount / qaList.length * 100) + '%' }">
              <CheckCircleOutlined />
              <span class="segment-num">{{ hitCount }}</span>
              <span class="segment-label">分块命中</span>
            </div>
            <div class="stats-segment stats-doc-hit" :style="{ width: ((docHitOnlyCount) / qaList.length * 100) + '%' }">
              <FileTextOutlined />
              <span class="segment-num">{{ docHitOnlyCount }}</span>
              <span class="segment-label">文档命中</span>
            </div>
            <div class="stats-segment stats-miss" :style="{ width: (missCount / qaList.length * 100) + '%' }">
              <CloseCircleOutlined />
              <span class="segment-num">{{ missCount }}</span>
              <span class="segment-label">未命中</span>
            </div>
          </div>
        </div>

        <!-- QA 详情列表 -->
        <div class="qa-section">
          <div class="section-header">
            <h3 class="section-title">QA 评估详情</h3>
            <a-input-search v-model:value="qaSearchText" placeholder="搜索问题..." style="width: 240px" allow-clear />
          </div>
          <a-table :columns="qaColumns" :data-source="filteredQaList" :loading="loadingQa" row-key="id" :pagination="{ pageSize: 10 }" size="small">
            <template #bodyCell="{ column, record }">
              <template v-if="column.key === 'isNegative'">
                <a-tag :color="record.isNegative ? 'warning' : 'default'">
                  {{ record.isNegative ? '负样本' : '正样本' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'hit'">
                <a-tag v-if="record.isNegative" :color="!record.retrievedChunkIds || record.retrievedChunkIds === '[]' ? 'success' : 'error'">
                  {{ !record.retrievedChunkIds || record.retrievedChunkIds === '[]' ? '正确未命中' : '错误命中' }}
                </a-tag>
                <a-tag v-else :color="record.hit ? 'success' : 'default'">
                  {{ record.hit ? '命中' : '-' }}
                </a-tag>
              </template>
              <template v-else-if="column.key === 'hitRank'">
                <a-badge v-if="record.hitRank" :count="record.hitRank" :number-style="{ backgroundColor: record.hitRank <= 3 ? '#52c41a' : '#1890ff' }" />
                <span v-else-if="record.docHitRank" class="text-muted">D{{ record.docHitRank }}</span>
                <span v-else class="text-muted">-</span>
              </template>
              <template v-else-if="column.key === 'recallAtK'"><span v-if="record.recallAtK != null">{{ (record.recallAtK * 100).toFixed(0) }}%</span><span v-else>-</span></template>
              <template v-else-if="column.key === 'faithfulness'"><span v-if="record.faithfulness != null">{{ (record.faithfulness * 100).toFixed(0) }}%</span><span v-else>-</span></template>
              <template v-else-if="column.key === 'question'">
                <a-tooltip :title="record.question">
                  <span class="text-ellipsis">{{ record.question }}</span>
                </a-tooltip>
              </template>
              <template v-else-if="column.key === 'action'">
                <a-button type="link" size="small" @click="showQaDetail(record)">详情</a-button>
              </template>
            </template>
            <template #expandedRowRender="{ record }">
              <div class="qa-expand-content">
                <div class="expand-section">
                  <div class="expand-label">期望答案</div>
                  <div class="expand-value">{{ record.expectedAnswer || '-' }}</div>
                </div>
                <div class="expand-section" v-if="record.sourceChunkContent">
                  <div class="expand-label">源分块内容</div>
                  <div class="expand-value chunk-content">{{ record.sourceChunkContent }}</div>
                </div>
              </div>
            </template>
          </a-table>
        </div>
      </template>
    </a-drawer>

    <!-- QA 详情模态框 -->
    <a-modal v-model:open="qaDetailVisible" :title="'QA 详情'" width="800" :footer="null">
      <template v-if="selectedQa">
        <a-descriptions :column="2" bordered size="small">
          <a-descriptions-item label="问题" :span="2">{{ selectedQa.question }}</a-descriptions-item>
          <a-descriptions-item label="样本类型">
            <a-tag :color="selectedQa.isNegative ? 'warning' : 'default'">{{ selectedQa.isNegative ? '负样本' : '正样本' }}</a-tag>
          </a-descriptions-item>
          <a-descriptions-item label="命中状态">
            <template v-if="selectedQa.isNegative">
              <a-tag :color="!selectedQa.retrievedChunkIds || selectedQa.retrievedChunkIds === '[]' ? 'success' : 'error'">
                {{ !selectedQa.retrievedChunkIds || selectedQa.retrievedChunkIds === '[]' ? '正确未命中' : '错误命中' }}
              </a-tag>
            </template>
            <template v-else>
              <a-tag :color="selectedQa.hit ? 'success' : 'error'">{{ selectedQa.hit ? '命中' : '未命中' }}</a-tag>
              <span v-if="selectedQa.hitRank"> (第 {{ selectedQa.hitRank }} 位)</span>
            </template>
          </a-descriptions-item>
          <a-descriptions-item label="期望答案" :span="2">{{ selectedQa.expectedAnswer || '-' }}</a-descriptions-item>
          <a-descriptions-item label="源分块ID" :span="2">
            <a-typography-text copyable>{{ selectedQa.sourceChunkId || '-' }}</a-typography-text>
          </a-descriptions-item>
          <a-descriptions-item label="检索到的分块IDs" :span="2">
            <a-typography-text copyable>{{ selectedQa.retrievedChunkIds || '-' }}</a-typography-text>
          </a-descriptions-item>
          <a-descriptions-item label="相关文档标注" :span="2">{{ selectedQa.relevanceJudgments || selectedQa.expectedDocumentIds || '-' }}</a-descriptions-item>
          <a-descriptions-item label="实际答案" :span="2">{{ selectedQa.actualAnswer || '-' }}</a-descriptions-item>
          <a-descriptions-item label="忠实度 / 相关性">{{ scoreText(selectedQa.faithfulness) }} / {{ scoreText(selectedQa.answerRelevancy) }}</a-descriptions-item>
          <a-descriptions-item label="完整性 / 流畅性">{{ scoreText(selectedQa.completeness) }} / {{ scoreText(selectedQa.fluency) }}</a-descriptions-item>
          <a-descriptions-item label="评审理由" :span="2">{{ selectedQa.judgeReason || '-' }}</a-descriptions-item>
          <a-descriptions-item label="失败归因">{{ failureText(selectedQa.failureCategory) }}</a-descriptions-item>
          <a-descriptions-item label="改进建议">{{ selectedQa.failureSuggestion || '-' }}</a-descriptions-item>
        </a-descriptions>
      </template>
    </a-modal>

    <!-- 创建数据集模态框 -->
    <a-modal v-model:open="createDatasetVisible" title="新建数据集" @ok="handleCreateDataset" :confirm-loading="creatingDataset">
      <a-form layout="vertical">
        <a-form-item label="数据集名称" required>
          <a-input v-model:value="newDatasetForm.name" placeholder="请输入数据集名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="newDatasetForm.description" placeholder="可选，描述数据集的用途和内容" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 编辑数据集模态框 -->
    <a-modal v-model:open="editDatasetVisible" title="编辑数据集" @ok="handleUpdateDataset" :confirm-loading="updatingDataset">
      <a-form layout="vertical">
        <a-form-item label="数据集名称" required>
          <a-input v-model:value="editDatasetForm.name" placeholder="请输入数据集名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="editDatasetForm.description" placeholder="可选，描述数据集的用途和内容" :rows="3" />
        </a-form-item>
      </a-form>
    </a-modal>

    <!-- 预览/管理数据集问题 模态框 -->
    <a-modal v-model:open="datasetDetailVisible" :title="currentDatasetName" width="900" :footer="null">
      <div v-if="editingDataset && editingDataset.datasetType !== 'builtin'" style="margin-bottom: 16px;">
        <a-button type="primary" size="small" @click="showAddItemModal">
          <PlusOutlined /> 添加问题
        </a-button>
        <a-upload
          :before-upload="beforeItemUpload"
          :show-upload-list="false"
          accept=".json,.xlsx,.xls"
          style="display: inline-block; margin-left: 8px;"
        >
          <a-button size="small"><ImportOutlined /> 批量导入</a-button>
        </a-upload>
      </div>
      <a-table
        :columns="datasetItemColumns"
        :data-source="currentDatasetItems"
        :loading="loadingDatasetItems"
        row-key="id"
        size="small"
        :pagination="{ pageSize: 10 }"
      >
        <template #bodyCell="{ column, record }">
          <template v-if="column.key === 'isNegative'">
            <a-tag :color="record.isNegative ? 'warning' : 'default'">
              {{ record.isNegative ? '负样本' : '正样本' }}
            </a-tag>
          </template>
          <template v-else-if="column.key === 'question'">
            <a-tooltip :title="record.question">
              <span style="max-width: 350px; overflow: hidden; text-overflow: ellipsis; white-space: nowrap; display: inline-block;">
                {{ record.question }}
              </span>
            </a-tooltip>
          </template>
          <template v-else-if="column.key === 'labels'">
            <a-tag v-if="hasLabels(record)" color="blue">已标注</a-tag>
            <span v-else class="text-muted">未标注</span>
          </template>
          <template v-else-if="column.key === 'action'">
            <a-button v-if="editingDataset && editingDataset.datasetType !== 'builtin'" type="link" size="small" @click="showEditItemModal(record)">标注</a-button>
            <a-popconfirm v-if="editingDataset && editingDataset.datasetType !== 'builtin'" title="确定删除此问题？" @confirm="handleRemoveItem(record)">
              <a-button type="link" size="small" danger>删除</a-button>
            </a-popconfirm>
          </template>
        </template>
      </a-table>
    </a-modal>

    <!-- 添加单个问题 模态框 -->
    <a-modal v-model:open="addItemVisible" :title="editingDatasetItem ? '编辑问题与标注' : '添加问题'" @ok="handleAddItem" :confirm-loading="addingItem">
      <a-form layout="vertical">
        <a-form-item label="问题" required>
          <a-textarea v-model:value="newItemForm.question" placeholder="请输入问题内容" :rows="2" />
        </a-form-item>
        <a-form-item label="期望答案">
          <a-textarea v-model:value="newItemForm.expectedAnswer" placeholder="可选" :rows="2" />
        </a-form-item>
        <a-form-item label="是否负样本">
          <a-switch v-model:checked="newItemForm.isNegative" />
          <span style="margin-left: 8px; color: #999;">负样本的答案不在知识库中</span>
        </a-form-item>
        <a-alert v-if="!datasetForm.knowledgeId" type="warning" show-icon message="请先在测试数据集页选择目标知识库，才能可视化选择文档和分块。" style="margin-bottom:12px" />
        <a-form-item label="相关文档" extra="支持多选；选择后为每篇文档设置 1-3 级相关性。">
          <a-select v-model:value="newItemForm.expectedDocumentIds" mode="multiple" show-search option-filter-prop="label" :options="labelDocumentOptions" :loading="loadingLabelOptions" :disabled="!datasetForm.knowledgeId" placeholder="搜索并选择知识库文档" @change="syncRelevanceGrades" />
        </a-form-item>
        <a-form-item v-if="newItemForm.expectedDocumentIds.length" label="相关性等级">
          <div class="relevance-list"><div v-for="documentId in newItemForm.expectedDocumentIds" :key="documentId" class="relevance-row"><span>{{ documentTitle(documentId) }}</span><a-input-number v-model:value="newItemForm.relevanceJudgments[String(documentId)]" :min="1" :max="3" /></div></div>
        </a-form-item>
        <a-form-item label="标准来源分块" extra="可选，用于精确分块命中评测。">
          <a-select v-model:value="newItemForm.sourceChunkId" allow-clear show-search option-filter-prop="label" :options="labelChunkOptions" :loading="loadingLabelOptions" :disabled="!datasetForm.knowledgeId" placeholder="选择最能直接回答问题的分块" />
        </a-form-item>
        <a-form-item label="场景 / 难度"><a-input-group compact><a-input v-model:value="newItemForm.category" placeholder="semantic / multi_hop" style="width:65%" /><a-select v-model:value="newItemForm.difficulty" style="width:35%"><a-select-option value="EASY">简单</a-select-option><a-select-option value="MEDIUM">中等</a-select-option><a-select-option value="HARD">困难</a-select-option></a-select></a-input-group></a-form-item>
        <a-form-item label="业务线 / 用户角色" extra="用于评测实验室中的业务切片统计。"><a-input-group compact><a-input v-model:value="newItemForm.businessLine" placeholder="例如：售后服务" style="width:50%" /><a-input v-model:value="newItemForm.userRole" placeholder="例如：一线客服" style="width:50%" /></a-input-group></a-form-item>
      </a-form>
    </a-modal>

    <!-- 导入数据集 模态框 -->
    <a-modal v-model:open="importDatasetVisible" title="导入数据集" @ok="handleImportDataset" :confirm-loading="importingDataset">
      <a-form layout="vertical">
        <a-form-item label="数据集名称" required>
          <a-input v-model:value="importDatasetForm.name" placeholder="请输入数据集名称" />
        </a-form-item>
        <a-form-item label="描述">
          <a-textarea v-model:value="importDatasetForm.description" placeholder="可选" :rows="2" />
        </a-form-item>
        <a-form-item label="文件">
          <a-upload-dragger
            v-model:file-list="importDatasetFileList"
            :before-upload="() => false"
            :max-count="1"
            accept=".json,.xlsx,.xls"
          >
            <p class="ant-upload-drag-icon"><InboxOutlined /></p>
            <p class="ant-upload-text">点击或拖拽文件到此区域上传</p>
            <p class="ant-upload-hint">支持 JSON 或 Excel 格式</p>
          </a-upload-dragger>
        </a-form-item>
      </a-form>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import AgentEvaluationPanel from './components/AgentEvaluationPanel.vue'
import ExecutionHistory from './components/ExecutionHistory.vue'
import { ref, reactive, computed, onMounted, onUnmounted, watch } from 'vue'
import { message } from 'ant-design-vue'
import {
  DatabaseOutlined, ClockCircleOutlined, FileTextOutlined, CheckCircleOutlined, CloseCircleOutlined,
  InboxOutlined, DownloadOutlined, InfoCircleOutlined, PlusOutlined, ImportOutlined,
} from '@ant-design/icons-vue'
import type { EvaluationTask, EvaluationQa } from '@/api/evaluation'
import {
  generateTestset, getEvaluationList, getEvaluationQaList, deleteEvaluationTask,
  cancelEvaluationTask, replayEvaluationTask,
  importTestset, downloadTemplate, exportEvaluationReport,
} from '@/api/evaluation'
import { getKnowledgeBaseList, getKnowledgeBaseDocuments, getKnowledgeBaseChunks, type KnowledgeDocumentVO, type KnowledgeChunk } from '@/api/knowledgeBase'
import type { TestDataset, TestDatasetItem } from '@/api/dataset'
import {
  getDatasetList, getDatasetItems, createDataset, updateDataset, deleteDataset, cloneDataset,
  addDatasetItem, addDatasetItems, updateDatasetItem, removeDatasetItem, importDataset, runDatasetEvaluation,
} from '@/api/dataset'

const activeTab = ref('generate')
const runConfig = reactive({ evaluationMode: 'RETRIEVAL' as 'RETRIEVAL'|'GENERATION'|'FULL', topK: 5, judgeModel: '', baselineTaskId: undefined as number|undefined })
const knowledgeBases = ref<any[]>([])
const loadingKbList = ref(false)
const generating = ref(false)
const importing = ref(false)
const loadingTasks = ref(false)
const tasks = ref<EvaluationTask[]>([])
const taskPagination = reactive({ current: 1, pageSize: 10, total: 0, showSizeChanger: true, showTotal: (total: number) => `共 ${total} 条` })
const detailVisible = ref(false)
const currentTask = ref<EvaluationTask | null>(null)
const qaList = ref<EvaluationQa[]>([])
const loadingQa = ref(false)
const qaSearchText = ref('')
const qaDetailVisible = ref(false)
const selectedQa = ref<EvaluationQa | null>(null)

// 数据集
const datasets = ref<TestDataset[]>([])
const loadingDatasets = ref(false)
const runningDataset = ref<number | null>(null)
const datasetForm = reactive({ knowledgeId: null as number | null })

// 创建数据集
const createDatasetVisible = ref(false)
const creatingDataset = ref(false)
const newDatasetForm = reactive({ name: '', description: '' })

// 编辑数据集
const editDatasetVisible = ref(false)
const updatingDataset = ref(false)
const editDatasetForm = reactive({ id: 0, name: '', description: '' })

// 预览数据集
const datasetDetailVisible = ref(false)
const currentDatasetName = ref('')
const currentDatasetItems = ref<TestDatasetItem[]>([])
const loadingDatasetItems = ref(false)
const editingDataset = ref<TestDataset | null>(null)
const editingDatasetItem = ref<TestDatasetItem | null>(null)

// 添加问题
const addItemVisible = ref(false)
const addingItem = ref(false)
const newItemForm = reactive({ question: '', expectedAnswer: '', isNegative: false, expectedDocumentIds: [] as number[], relevanceJudgments: {} as Record<string, number>, sourceChunkId: undefined as string|undefined, category: 'semantic', difficulty: 'MEDIUM', businessLine: '', userRole: '' })
const labelDocuments = ref<KnowledgeDocumentVO[]>([])
const labelChunks = ref<KnowledgeChunk[]>([])
const loadingLabelOptions = ref(false)

// 导入数据集
const importDatasetVisible = ref(false)
const importingDataset = ref(false)
const importDatasetForm = reactive({ name: '', description: '' })
const importDatasetFileList = ref<any[]>([])

// 导入
const importFileList = ref<any[]>([])

const generateForm = reactive({ knowledgeId: null as number | null, qaCount: 10, taskName: '' })
const importForm = reactive({ knowledgeId: null as number | null, taskName: '' })

const taskColumns = [
  { title: '任务名称', dataIndex: 'taskName', key: 'taskName' },
  { title: '知识库', dataIndex: 'knowledgeName', key: 'knowledgeName', width: 120 },
  { title: '类型', key: 'datasetType', width: 90 },
  { title: 'QA数量', dataIndex: 'qaCount', key: 'qaCount', width: 80 },
  { title: 'Recall@K', key: 'hitRate', width: 100 },
  { title: '负样本错误率', key: 'negativeHitRate', width: 110 },
  { title: 'MRR', key: 'mrr', width: 80 },
  { title: '忠实度', key: 'faithfulness', width: 90 },
  { title: '基线', key: 'regressionStatus', width: 90 },
  { title: '状态', key: 'status', width: 80 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 170 },
  { title: '操作', key: 'action', width: 250 },
]

const qaColumns = [
  { title: '问题', dataIndex: 'question', key: 'question', ellipsis: true, width: 260 },
  { title: '类型', key: 'isNegative', width: 80 },
  { title: '命中', key: 'hit', width: 100 },
  { title: '排名', key: 'hitRank', width: 80 },
  { title: 'Recall', key: 'recallAtK', width: 80 },
  { title: '忠实度', key: 'faithfulness', width: 80 },
  { title: '操作', key: 'action', width: 80 },
]

const datasetItemColumns = [
  { title: '问题', dataIndex: 'question', key: 'question', ellipsis: true, width: 300 },
  { title: '场景', dataIndex: 'category', key: 'category', width: 110 },
  { title: '难度', dataIndex: 'difficulty', key: 'difficulty', width: 80 },
  { title: '业务线', dataIndex: 'businessLine', key: 'businessLine', width: 110 },
  { title: '角色', dataIndex: 'userRole', key: 'userRole', width: 100 },
  { title: '相关文档', key: 'labels', width: 100 },
  { title: '类型', key: 'isNegative', width: 80 },
  { title: '操作', key: 'action', width: 120 },
]

const hitCount = computed(() => qaList.value.filter(q => !q.isNegative && q.hit).length)
const docHitOnlyCount = computed(() => qaList.value.filter(q => !q.isNegative && q.docHit && !q.hit).length)
const missCount = computed(() => qaList.value.filter(q => !q.isNegative && !q.docHit).length + qaList.value.filter(q => q.isNegative).length)
const filteredQaList = computed(() => {
  if (!qaSearchText.value) return qaList.value
  const search = qaSearchText.value.toLowerCase()
  return qaList.value.filter(q => q.question.toLowerCase().includes(search))
})
const selectedKnowledgeId = computed(() => activeTab.value === 'generate'
  ? generateForm.knowledgeId
  : activeTab.value === 'import' ? importForm.knowledgeId : datasetForm.knowledgeId)
const baselineOptions = computed(() => tasks.value.filter(task => task.status === 2
  && (!selectedKnowledgeId.value || task.knowledgeId === selectedKnowledgeId.value)
  && (task.topK || 5) === runConfig.topK
  && (task.evaluationMode || 'RETRIEVAL') === runConfig.evaluationMode))
const retrievalMetrics = computed(() => [
  { label: `Recall@${currentTask.value?.topK || 5}`, value: currentTask.value?.recallAtK || 0 },
  { label: `Precision@${currentTask.value?.topK || 5}`, value: currentTask.value?.precisionAtK || 0 },
  { label: `NDCG@${currentTask.value?.topK || 5}`, value: currentTask.value?.ndcgAtK || 0 },
  { label: 'MAP', value: currentTask.value?.mapScore || 0 },
])
const generationMetrics = computed(() => [
  { label: 'Context Precision', value: currentTask.value?.contextPrecision || 0 },
  { label: 'Context Recall', value: currentTask.value?.contextRecall || 0 },
  { label: 'Faithfulness', value: currentTask.value?.faithfulness || 0 },
  { label: 'Answer Relevancy', value: currentTask.value?.answerRelevancy || 0 },
  { label: 'Completeness', value: currentTask.value?.completeness || 0 },
  { label: 'Fluency', value: currentTask.value?.fluency || 0 },
  { label: 'Overall', value: currentTask.value?.overallScore || 0 },
])
const labelDocumentOptions = computed(() => labelDocuments.value.map(doc => ({ value: doc.id, label: `#${doc.id} ${doc.title}` })))
const labelChunkOptions = computed(() => labelChunks.value.filter(chunk => chunk.vectorId).map(chunk => ({ value: chunk.vectorId, label: `${chunk.documentTitle} · 分块 ${chunk.chunkIndex + 1} · ${chunk.contentPreview || chunk.content.slice(0, 60)}` })))

watch([selectedKnowledgeId, () => runConfig.topK, () => runConfig.evaluationMode, tasks], () => {
  if (runConfig.baselineTaskId && !baselineOptions.value.some(task => task.id === runConfig.baselineTaskId)) {
    runConfig.baselineTaskId = undefined
  }
})
watch(() => datasetForm.knowledgeId, () => { if (addItemVisible.value) loadLabelOptions() })

function statusText(status: number, jobStatus?: string | null) {
  if (jobStatus === 'RETRY_WAIT') return '等待重试'
  if (jobStatus === 'DEAD_LETTER') return '死信'
  if (jobStatus === 'CANCEL_REQUESTED') return '取消中'
  const map: Record<number, string> = { 0: '排队中', 1: '运行中', 2: '完成', 3: '失败', 4: '已取消' }
  return map[status] || '未知'
}

function statusColor(status: number, jobStatus?: string | null) {
  if (jobStatus === 'RETRY_WAIT') return 'orange'
  if (jobStatus === 'DEAD_LETTER') return 'red'
  if (jobStatus === 'CANCEL_REQUESTED') return 'warning'
  const map: Record<number, string> = { 0: 'default', 1: 'processing', 2: 'success', 3: 'error', 4: 'warning' }
  return map[status] || 'default'
}

function datasetTypeText(type: string | null) {
  const map: Record<string, string> = { generated: '生成', imported: '导入', builtin: '内置', custom: '自定义', historical: '历史重放' }
  return map[type || 'generated'] || '生成'
}

function datasetTypeColor(type: string | null) {
  const map: Record<string, string> = { generated: 'blue', imported: 'green', builtin: 'purple', custom: 'green', historical: 'cyan' }
  return map[type || 'generated'] || 'blue'
}

function regressionText(value: string | null) { return ({PASSED:'通过',FAILED:'退化',NOT_COMPARED:'未对比',INCOMPARABLE:'不可比'} as Record<string,string>)[value || 'NOT_COMPARED'] || '未对比' }
function scoreText(value: number | null) { return value === null || value === undefined ? '-' : `${(value * 100).toFixed(1)}%` }
function failureText(value: string | null) { return ({UNLABELED:'缺少标注',RETRIEVAL_MISS:'检索遗漏',CONTEXT_NOISE:'上下文噪声',HALLUCINATION:'生成幻觉',INCOMPLETE:'回答不完整',NEGATIVE_FALSE_POSITIVE:'拒答失败'} as Record<string,string>)[value || ''] || value || '无明显失败' }

async function loadKnowledgeBases() {
  loadingKbList.value = true
  try {
    const res = await getKnowledgeBaseList({ page: 1, pageSize: 100 })
    if (res.code === 200) {
      knowledgeBases.value = (res.data.records || []).filter(kb => kb.accessRole === 'owner' || kb.accessRole === 'editor')
    }
  } catch (error) {
    console.error('加载知识库列表失败', error)
  } finally {
    loadingKbList.value = false
  }
}

async function loadTasks() {
  loadingTasks.value = true
  try {
    const res = await getEvaluationList({
      page: taskPagination.current,
      pageSize: taskPagination.pageSize,
    })
    if (res.code === 200) {
      tasks.value = res.data?.records || []
      taskPagination.total = res.data?.total || 0
    }
  } catch (error) {
    console.error('加载评估任务失败', error)
  } finally {
    loadingTasks.value = false
  }
}

function handleTaskTableChange(pagination: any) {
  taskPagination.current = pagination.current
  taskPagination.pageSize = pagination.pageSize
  loadTasks()
}

async function loadDatasets() {
  loadingDatasets.value = true
  try {
    const res = await getDatasetList()
    if (res.code === 200) datasets.value = res.data || []
  } catch (error) {
    console.error('加载数据集失败', error)
  } finally {
    loadingDatasets.value = false
  }
}

async function handleGenerate() {
  if (!generateForm.knowledgeId) { message.warning('请选择知识库'); return }
  generating.value = true
  try {
    const res = await generateTestset({ knowledgeId: generateForm.knowledgeId!, qaCount: generateForm.qaCount, taskName: generateForm.taskName || undefined, ...evaluationOptions() })
    if (res.code === 200) {
      message.success('评估任务已创建')
      generateForm.taskName = ''
      loadTasks()
      startPolling()
    } else {
      message.error(res.message || '创建失败')
    }
  } catch (error: any) {
    message.error(error.message || '创建失败')
  } finally {
    generating.value = false
  }
}

const beforeImportUpload = () => false

async function handleDownloadTemplate() {
  try {
    const res = await downloadTemplate()
    const url = window.URL.createObjectURL(new Blob([res as any]))
    const link = document.createElement('a')
    link.href = url
    link.setAttribute('download', 'evaluation_template.json')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    message.error('下载模板失败')
  }
}

async function handleImport() {
  if (!importForm.knowledgeId) { message.warning('请选择知识库'); return }
  if (importFileList.value.length === 0) { message.warning('请选择文件'); return }

  importing.value = true
  try {
    const file = importFileList.value[0].originFileObj || importFileList.value[0]
    const formData = new FormData()
    formData.append('file', file)
    formData.append('knowledgeId', String(importForm.knowledgeId))
    if (importForm.taskName) formData.append('taskName', importForm.taskName)
    formData.append('evaluationMode', runConfig.evaluationMode)
    formData.append('topK', String(runConfig.topK))
    if (runConfig.judgeModel) formData.append('judgeModel', runConfig.judgeModel)
    if (runConfig.baselineTaskId) formData.append('baselineTaskId', String(runConfig.baselineTaskId))

    const res = await importTestset(formData)
    if (res.code === 200) {
      message.success('导入成功，正在评估')
      importFileList.value = []
      importForm.taskName = ''
      loadTasks()
      startPolling()
    } else {
      message.error(res.message || '导入失败')
    }
  } catch (error: any) {
    message.error(error.message || '导入失败')
  } finally {
    importing.value = false
  }
}

// 数据集管理
function showCreateDatasetModal() {
  newDatasetForm.name = ''
  newDatasetForm.description = ''
  createDatasetVisible.value = true
}

async function handleCreateDataset() {
  if (!newDatasetForm.name.trim()) { message.warning('请输入数据集名称'); return }
  creatingDataset.value = true
  try {
    const res = await createDataset({ name: newDatasetForm.name, description: newDatasetForm.description })
    if (res.code === 200) {
      message.success('数据集创建成功')
      createDatasetVisible.value = false
      loadDatasets()
    } else {
      message.error(res.message || '创建失败')
    }
  } catch (error: any) {
    message.error(error.message || '创建失败')
  } finally {
    creatingDataset.value = false
  }
}

function handleEditDataset(ds: TestDataset) {
  editDatasetForm.id = ds.id
  editDatasetForm.name = ds.name
  editDatasetForm.description = ds.description || ''
  editDatasetVisible.value = true
}

async function handleUpdateDataset() {
  if (!editDatasetForm.name.trim()) { message.warning('请输入数据集名称'); return }
  updatingDataset.value = true
  try {
    const res = await updateDataset(editDatasetForm.id, { name: editDatasetForm.name, description: editDatasetForm.description })
    if (res.code === 200) {
      message.success('更新成功')
      editDatasetVisible.value = false
      loadDatasets()
    } else {
      message.error(res.message || '更新失败')
    }
  } catch (error: any) {
    message.error(error.message || '更新失败')
  } finally {
    updatingDataset.value = false
  }
}

async function handleDeleteDataset(ds: TestDataset) {
  try {
    const res = await deleteDataset(ds.id)
    if (res.code === 200) {
      message.success('删除成功')
      loadDatasets()
    } else {
      message.error(res.message || '删除失败')
    }
  } catch (error: any) {
    message.error(error.message || '删除失败')
  }
}

async function handleCloneDataset(ds: TestDataset) {
  try {
    const res = await cloneDataset(ds.id)
    if (res.code === 200) {
      message.success('已复制为可编辑的业务数据集，请在问题列表中绑定相关文档')
      await loadDatasets()
      if (res.data) handlePreviewDataset(res.data)
    } else {
      message.error(res.message || '复制失败')
    }
  } catch (error: any) {
    message.error(error.message || '复制失败')
  }
}

async function handlePreviewDataset(ds: TestDataset) {
  editingDataset.value = ds
  currentDatasetName.value = ds.name + ' - 问题列表'
  datasetDetailVisible.value = true
  loadingDatasetItems.value = true
  try {
    const res = await getDatasetItems(ds.id)
    if (res.code === 200) currentDatasetItems.value = res.data || []
  } catch (error) {
    console.error('加载数据集问题失败', error)
  } finally {
    loadingDatasetItems.value = false
  }
}

async function handleRunDataset(datasetId: number) {
  if (!datasetForm.knowledgeId) { message.warning('请先选择知识库'); return }
  runningDataset.value = datasetId
  try {
    const res = await runDatasetEvaluation(datasetId, datasetForm.knowledgeId!, evaluationOptions())
    if (res.code === 200) {
      message.success('评估任务已创建')
      loadTasks()
      startPolling()
    } else {
      message.error(res.message || '创建失败')
    }
  } catch (error: any) {
    message.error(error.message || '创建失败')
  } finally {
    runningDataset.value = null
  }
}

function showAddItemModal() {
  editingDatasetItem.value = null
  newItemForm.question = ''
  newItemForm.expectedAnswer = ''
  newItemForm.isNegative = false
  newItemForm.expectedDocumentIds = []
  newItemForm.relevanceJudgments = {}
  newItemForm.sourceChunkId = undefined
  newItemForm.category = 'semantic'
  newItemForm.difficulty = 'MEDIUM'
  newItemForm.businessLine = ''
  newItemForm.userRole = ''
  addItemVisible.value = true
  loadLabelOptions()
}

function showEditItemModal(item: TestDatasetItem) {
  editingDatasetItem.value = item
  newItemForm.question = item.question
  newItemForm.expectedAnswer = item.expectedAnswer || ''
  newItemForm.isNegative = Boolean(item.isNegative)
  newItemForm.expectedDocumentIds = parseDocumentIds(item.expectedDocumentIds)
  newItemForm.relevanceJudgments = parseRelevance(item.relevanceJudgments)
  newItemForm.sourceChunkId = item.sourceChunkId || undefined
  newItemForm.category = item.category || 'semantic'
  newItemForm.difficulty = item.difficulty || 'MEDIUM'
  newItemForm.businessLine = item.businessLine || ''
  newItemForm.userRole = item.userRole || ''
  addItemVisible.value = true
  loadLabelOptions()
}

function parseDocumentIds(value: TestDatasetItem['expectedDocumentIds']): number[] {
  if (!value) return []
  if (Array.isArray(value)) return value
  try {
    const parsed = JSON.parse(value)
    return Array.isArray(parsed) ? parsed.map(Number).filter(Boolean) : []
  } catch { return [] }
}

function parseRelevance(value: TestDatasetItem['relevanceJudgments']): Record<string, number> {
  if (!value) return {}
  if (typeof value !== 'string') return { ...value }
  try { return JSON.parse(value) } catch { return {} }
}

function syncRelevanceGrades() {
  const selected = new Set(newItemForm.expectedDocumentIds.map(String))
  Object.keys(newItemForm.relevanceJudgments).forEach(key => { if (!selected.has(key)) delete newItemForm.relevanceJudgments[key] })
  newItemForm.expectedDocumentIds.forEach(id => { if (!newItemForm.relevanceJudgments[String(id)]) newItemForm.relevanceJudgments[String(id)] = 3 })
}

function documentTitle(id: number) { return labelDocuments.value.find(doc => doc.id === id)?.title || `文档 #${id}` }

async function loadLabelOptions() {
  if (!datasetForm.knowledgeId) { labelDocuments.value = []; labelChunks.value = []; return }
  loadingLabelOptions.value = true
  try {
    const [documents, chunks] = await Promise.all([
      getKnowledgeBaseDocuments(datasetForm.knowledgeId, { page: 1, pageSize: 200 }),
      getKnowledgeBaseChunks(datasetForm.knowledgeId, { page: 1, pageSize: 200 }),
    ])
    labelDocuments.value = documents.data?.records || []
    labelChunks.value = chunks.data?.records || []
  } finally { loadingLabelOptions.value = false }
}

function hasLabels(item: TestDatasetItem) {
  const values = [item.expectedDocumentIds, item.relevanceJudgments]
  return values.some(value => {
    if (!value) return false
    if (typeof value !== 'string') return Object.keys(value).length > 0
    try {
      const parsed = JSON.parse(value)
      return Array.isArray(parsed) ? parsed.length > 0 : parsed && Object.keys(parsed).length > 0
    } catch { return false }
  })
}

async function handleAddItem() {
  if (!newItemForm.question.trim()) { message.warning('请输入问题内容'); return }
  if (!editingDataset.value) return

  syncRelevanceGrades()

  addingItem.value = true
  try {
    const payload = {
      ...(editingDatasetItem.value || {}),
      question: newItemForm.question,
      expectedAnswer: newItemForm.expectedAnswer,
      isNegative: newItemForm.isNegative,
      sourceDocumentId: newItemForm.expectedDocumentIds[0] || null,
      sourceChunkId: newItemForm.sourceChunkId || null,
      expectedDocumentIds: JSON.stringify(newItemForm.expectedDocumentIds),
      relevanceJudgments: Object.keys(newItemForm.relevanceJudgments).length ? JSON.stringify(newItemForm.relevanceJudgments) : null,
      category: newItemForm.category,
      difficulty: newItemForm.difficulty,
      businessLine: newItemForm.businessLine || null,
      userRole: newItemForm.userRole || null,
    } as TestDatasetItem
    const res = editingDatasetItem.value?.id
      ? await updateDatasetItem(editingDataset.value.id, editingDatasetItem.value.id, payload)
      : await addDatasetItem(editingDataset.value.id, payload)
    if (res.code === 200) {
      message.success(editingDatasetItem.value ? '标注已更新' : '添加成功')
      addItemVisible.value = false
      // 刷新问题列表
      handlePreviewDataset(editingDataset.value)
      loadDatasets()
    } else {
      message.error(res.message || '添加失败')
    }
  } catch (error: any) {
    message.error(error.message || '添加失败')
  } finally {
    addingItem.value = false
  }
}

function evaluationOptions() {
  return { evaluationMode: runConfig.evaluationMode, topK: runConfig.topK, judgeModel: runConfig.judgeModel || undefined, baselineTaskId: runConfig.baselineTaskId }
}

async function handleRemoveItem(item: TestDatasetItem) {
  if (!editingDataset.value || !item.id) return
  try {
    const res = await removeDatasetItem(editingDataset.value.id, item.id)
    if (res.code === 200) {
      message.success('删除成功')
      handlePreviewDataset(editingDataset.value)
      loadDatasets()
    }
  } catch (error) {
    console.error('删除问题失败', error)
  }
}

const beforeItemUpload = (file: File) => {
  handleBatchImportItems(file)
  return false
}

async function handleBatchImportItems(file: File) {
  if (!editingDataset.value) return

  try {
    const reader = new FileReader()
    reader.onload = async (e) => {
      try {
        const text = e.target?.result as string
        let items: TestDatasetItem[] = []

        if (file.name.endsWith('.json')) {
          const parsed = JSON.parse(text)
          items = Array.isArray(parsed) ? parsed : parsed.items
        } else if (file.name.endsWith('.xlsx') || file.name.endsWith('.xls')) {
          message.warning('暂不支持Excel格式，请使用JSON格式')
          return
        }

        if (items.length === 0) {
          message.warning('文件中没有有效的问题')
          return
        }

        const res: any = await addDatasetItems(editingDataset.value!.id, items)
        if (res.code === 200) {
          message.success(`成功导入 ${items.length} 条问题`)
          handlePreviewDataset(editingDataset.value!)
          loadDatasets()
        } else {
          message.error(res.message || '导入失败')
        }
      } catch (err) {
        message.error('文件解析失败')
      }
    }
    reader.readAsText(file)
  } catch (error) {
    message.error('导入失败')
  }
}

// 导入数据集
function showImportDatasetModal() {
  importDatasetForm.name = ''
  importDatasetForm.description = ''
  importDatasetFileList.value = []
  importDatasetVisible.value = true
}

async function handleImportDataset() {
  if (!importDatasetForm.name.trim()) { message.warning('请输入数据集名称'); return }
  if (importDatasetFileList.value.length === 0) { message.warning('请选择文件'); return }

  importingDataset.value = true
  try {
    const file = importDatasetFileList.value[0].originFileObj || importDatasetFileList.value[0]
    const formData = new FormData()
    formData.append('file', file)
    formData.append('name', importDatasetForm.name)
    if (importDatasetForm.description) formData.append('description', importDatasetForm.description)

    const res = await importDataset(formData)
    if (res.code === 200) {
      message.success('数据集导入成功')
      importDatasetVisible.value = false
      loadDatasets()
    } else {
      message.error(res.message || '导入失败')
    }
  } catch (error: any) {
    message.error(error.message || '导入失败')
  } finally {
    importingDataset.value = false
  }
}

// 导出评估报告
async function handleExportReport(record: EvaluationTask) {
  try {
    const res = await exportEvaluationReport(record.id)
    const url = window.URL.createObjectURL(new Blob([res as any]))
    const link = document.createElement('a')
    link.href = url
    const taskName = record.taskName || '评估报告'
    link.setAttribute('download', taskName + '.xlsx')
    document.body.appendChild(link)
    link.click()
    link.remove()
    window.URL.revokeObjectURL(url)
  } catch (error) {
    message.error('导出报告失败')
  }
}

let pollingTimer: ReturnType<typeof setInterval> | null = null

function startPolling() {
  if (pollingTimer) clearInterval(pollingTimer)
  pollingTimer = setInterval(() => {
    const hasRunning = tasks.value.some(t => t.status === 0 || t.status === 1)
    if (hasRunning) {
      loadTasks()
    } else {
      if (pollingTimer) clearInterval(pollingTimer)
      pollingTimer = null
    }
  }, 5000)
}

async function handleViewDetail(record: EvaluationTask) {
  currentTask.value = record
  qaSearchText.value = ''
  detailVisible.value = true
  loadingQa.value = true
  try {
    const res = await getEvaluationQaList(record.id)
    if (res.code === 200) qaList.value = res.data || []
  } catch (error) {
    console.error('加载QA列表失败', error)
  } finally {
    loadingQa.value = false
  }
}
function onHistoricalReplayCreated() { loadTasks(); startPolling() }

function showQaDetail(record: EvaluationQa) {
  selectedQa.value = record
  qaDetailVisible.value = true
}

async function handleDelete(record: EvaluationTask) {
  try {
    const res = await deleteEvaluationTask(record.id)
    if (res.code === 200) {
      message.success('删除成功')
      loadTasks()
    }
  } catch (error) {
    console.error('删除失败', error)
  }
}

async function handleCancel(record: any) {
  try {
    const res = await cancelEvaluationTask(record.id)
    if (res.code === 200) {
      message.success('取消请求已提交')
      await loadTasks()
    }
  } catch (error: any) {
    message.error(error.message || '取消失败')
  }
}

async function handleReplay(record: any) {
  try {
    const res = await replayEvaluationTask(record.id)
    if (res.code === 200) {
      message.success('任务已重新进入可靠队列')
      await loadTasks()
      startPolling()
    }
  } catch (error: any) {
    message.error(error.message || '重新执行失败')
  }
}

onMounted(() => {
  loadKnowledgeBases()
  loadTasks()
  loadDatasets()
})

onUnmounted(() => {
  if (pollingTimer) clearInterval(pollingTimer)
})
</script>

<style scoped lang="scss">
.evaluation-page {
  .evaluation-guide-tip { margin-bottom: 14px; }
  .evaluation-config {
    padding: 12px 14px 0;
    margin-bottom: 4px;
    border: 1px solid #e6f4ff;
    border-radius: 8px;
    background: #f7fbff;
  }
  .text-ellipsis {
    display: inline-block;
    max-width: 260px;
    overflow: hidden;
    text-overflow: ellipsis;
    white-space: nowrap;
    vertical-align: bottom;
  }
  .text-muted { color: #bbb; }

  .dataset-cards {
    display: flex;
    flex-wrap: wrap;
    gap: 16px;
  }

  .dataset-card {
    width: 300px;
    padding: 16px;
    border: 1px solid #f0f0f0;
    border-radius: 8px;
    background: #fafafa;
  }

  .dataset-header {
    display: flex;
    align-items: center;
    gap: 8px;
    margin-bottom: 8px;
  }

  .dataset-icon { font-size: 18px; }
  .dataset-name { font-weight: 600; font-size: 15px; }
  .dataset-desc { font-size: 13px; color: #666; margin-bottom: 12px; line-height: 1.5; }
  .dataset-stats { margin-bottom: 12px; }
  .dataset-actions { display: flex; gap: 8px; flex-wrap: wrap; }
  .relevance-list { width: 100%; display: flex; flex-direction: column; gap: 8px; }
  .relevance-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 8px 10px; background: #fafafa; border-radius: 6px; }
}
</style>

<style lang="scss">
.evaluation-detail-drawer {
  .ant-drawer-content-wrapper { background: #f8fafc; }
  .ant-drawer-body { padding: 0 !important; background: #f8fafc; overflow-y: auto; }
  .ant-drawer-header { background: #fff; border-bottom: 1px solid #f0f0f0; padding: 16px 24px; }
  .ant-drawer-title { font-weight: 600; font-size: 16px; color: #1f1f1f; }

  .task-banner {
    background: linear-gradient(135deg, #1e1b4b 0%, #312e81 40%, #4338ca 100%);
    padding: 24px;
    margin-bottom: 20px;

    .banner-content { display: flex; align-items: center; justify-content: space-between; }
    .banner-header { display: flex; align-items: center; gap: 14px; }

    .task-icon {
      width: 44px; height: 44px;
      border-radius: 12px;
      background: rgba(255, 255, 255, 0.12);
      display: flex; align-items: center; justify-content: center;
      font-size: 20px; color: rgba(255, 255, 255, 0.9);
    }

    .task-info {
      .task-name { margin: 0; font-size: 18px; font-weight: 600; color: #fff; line-height: 1.3; }
      .task-meta {
        display: flex; align-items: center; gap: 6px;
        margin-top: 5px; font-size: 13px; color: rgba(255, 255, 255, 0.6);
        .meta-dot { display: inline-flex; align-items: center; gap: 4px; }
        .meta-separator { opacity: 0.3; }
        .anticon { font-size: 12px; }
      }
    }

    .banner-right { display: flex; align-items: center; }

    .status-badge {
      display: flex; align-items: center; gap: 6px;
      padding: 5px 14px; border-radius: 20px;
      font-size: 13px; font-weight: 500;
      background: rgba(255, 255, 255, 0.1);
      color: rgba(255, 255, 255, 0.85);
      .status-dot { width: 7px; height: 7px; border-radius: 50%; }
      &.status-0 .status-dot { background: #d9d9d9; }
      &.status-1 .status-dot { background: #69b1ff; animation: pulse 1.5s infinite; }
      &.status-2 .status-dot { background: #73d13d; }
      &.status-3 .status-dot { background: #ff7875; }
    }
  }

  .layered-metrics-panel {
    background: #fff;
    border: 1px solid #e8e8e8;
    border-radius: 12px;
    padding: 18px 20px;
    margin: 0 24px 20px;

    .panel-title { margin-bottom: 14px; font-size: 13px; font-weight: 600; color: #595959; }
    .ant-statistic { padding: 10px 12px; border-radius: 8px; background: #fafafa; }
    .ant-statistic-title { min-height: 22px; }
  }

  @keyframes pulse { 0%, 100% { opacity: 1; } 50% { opacity: 0.4; } }

  .metrics-panel {
    background: #fff;
    border: 1px solid #f0f0f0;
    border-radius: 12px;
    padding: 20px;
    margin: 0 24px 20px;

    .panel-title { font-size: 13px; font-weight: 500; color: #8c8c8c; text-transform: uppercase; letter-spacing: 1px; margin-bottom: 18px; }
    .metrics-row { display: flex; align-items: center; }
    .metric-divider { width: 1px; height: 56px; background: #f0f0f0; flex-shrink: 0; margin: 0 4px; }
    .metric-item { flex: 1; display: flex; align-items: center; gap: 14px; padding: 0 12px; }
    .metric-visual { flex-shrink: 0; position: relative; }
    .metric-info {
      .metric-name { font-size: 14px; font-weight: 600; color: #262626; }
      .metric-desc { font-size: 12px; color: #bfbfbf; margin-top: 2px; }
    }
  }

  .metric-ring {
    display: block; width: 60px; height: 60px; transform: rotate(-90deg);
    path { fill: none; }
    .ring-bg { stroke: #f0f0f0; stroke-width: 3; }
    .ring-fill { stroke: #1890ff; stroke-width: 3; stroke-linecap: round; transition: stroke-dasharray 0.6s ease; }
  }

  .metric-ring-value {
    position: absolute; top: 0; left: 0; right: 0; bottom: 0;
    display: flex; align-items: center; justify-content: center;
    font-size: 14px; font-weight: 700; color: #1890ff;
    .percent { font-size: 10px; font-weight: 500; }
  }

  .metric-hit .metric-name { color: #0958d9; }
  .metric-doc-hit .metric-name { color: #d46b08; }
  .metric-mrr .metric-name { color: #237804; }
  .metric-rank .metric-name { color: #531dab; }
  .metric-count .metric-name { color: #531dab; }
  .metric-ring-value-orange { color: #d46b08 !important; }
  .ring-fill-orange { stroke: #fa8c16 !important; }

  .metric-big-value {
    width: 60px; text-align: center;
    font-size: 22px; font-weight: 700; color: #389e0d; line-height: 60px;
    &.highlight { color: #531dab; }
    &.purple { color: #531dab; }
  }

  .negative-panel {
    background: #fffbe6;
    border: 1px solid #ffe58f;
    border-radius: 12px;
    padding: 16px 20px;
    margin: 0 24px 20px;

    .panel-title { font-size: 13px; font-weight: 500; color: #d48806; margin-bottom: 12px; }
    .negative-content { display: flex; align-items: center; gap: 24px; }
    .negative-stat { display: flex; align-items: center; gap: 8px; }
    .negative-label { font-size: 13px; color: #666; }
    .negative-value { font-size: 18px; font-weight: 600; color: #d48806; }
    .negative-value.value-good { color: #52c41a; }
    .negative-value.value-bad { color: #ff4d4f; }
    .negative-desc { font-size: 12px; color: #999; margin-left: auto; }
  }

  .hit-stats {
    margin: 0 24px 24px;
    .stats-bar {
      display: flex; border-radius: 10px; overflow: hidden; height: 42px; font-size: 14px;
    }
    .stats-segment {
      display: flex; align-items: center; justify-content: center; gap: 6px;
      transition: width 0.5s ease; min-width: 80px;
      .segment-num { font-weight: 700; font-size: 16px; }
      .segment-label { opacity: 0.8; }
    }
    .stats-hit { background: #f6ffed; color: #389e0d; }
    .stats-doc-hit { background: #fffbe6; color: #d48806; }
    .stats-miss { background: #fff2f0; color: #cf1322; }
  }

  .qa-section {
    padding: 20px 24px; background: #fff; margin: 0 24px 24px;
    border-radius: 12px; border: 1px solid #f0f0f0;

    .section-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 16px; }
    .section-title { margin: 0; font-size: 16px; font-weight: 600; color: #1a1a2e; }
  }

  .qa-expand-content {
    padding: 12px 16px; background: #fafafa; border-radius: 6px;
    .expand-section { margin-bottom: 12px; &:last-child { margin-bottom: 0; } }
    .expand-label { font-size: 12px; color: #999; margin-bottom: 4px; }
    .expand-value {
      font-size: 14px; color: #333; line-height: 1.6;
      &.chunk-content { padding: 8px 12px; background: #fff; border-radius: 4px; border-left: 3px solid #1890ff; }
    }
  }
}
</style>
