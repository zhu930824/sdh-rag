<template>
  <div class="document-comparison-page">
    <a-card title="文档对比" :bordered="false">
      <a-row :gutter="24">
        <a-col :span="12">
          <a-card title="选择文档" size="small">
            <a-row :gutter="16">
              <a-col :span="12">
                <a-form-item label="文档1">
                  <a-select v-model:value="docId1" placeholder="选择第一个文档" show-search :filter-option="filterOption">
                    <a-select-option v-for="doc in documents" :key="doc.id" :value="doc.id">
                      {{ doc.title }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
              <a-col :span="12">
                <a-form-item label="文档2">
                  <a-select v-model:value="docId2" placeholder="选择第二个文档" show-search :filter-option="filterOption">
                    <a-select-option v-for="doc in documents" :key="doc.id" :value="doc.id">
                      {{ doc.title }}
                    </a-select-option>
                  </a-select>
                </a-form-item>
              </a-col>
            </a-row>
            <a-row :gutter="16" style="margin-top: 16px">
              <a-col :span="12">
                <a-form-item label="对比方式">
                  <a-radio-group v-model:value="comparisonType">
                    <a-radio-button value="text">文本对比</a-radio-button>
                    <a-radio-button value="semantic">语义相似度</a-radio-button>
                    <a-radio-button value="structure">结构对比</a-radio-button>
                  </a-radio-group>
                </a-form-item>
              </a-col>
              <a-col :span="12" style="text-align: right">
                <a-button type="primary" @click="handleCompare" :loading="comparing" :disabled="!docId1 || !docId2 || docId1 === docId2">
                  开始对比
                </a-button>
              </a-col>
            </a-row>
          </a-card>
        </a-col>
        <a-col :span="12">
          <a-card title="对比历史" size="small">
            <a-list :dataSource="history" size="small">
              <template #renderItem="{ item }">
                <a-list-item>
                  <a-list-item-meta :title="`文档对比 #${item.id}`" :description="item.summary" />
                  <template #actions>
                    <a-tag :color="getScoreColor(item.similarityScore)">{{ (item.similarityScore * 100).toFixed(1) }}%</a-tag>
                    <a-button type="link" size="small" @click="viewHistory(item)">查看</a-button>
                  </template>
                </a-list-item>
              </template>
            </a-list>
          </a-card>
        </a-col>
      </a-row>

      <a-divider />

      <a-card v-if="comparisonResult" title="对比结果" size="small">
        <a-row :gutter="16">
          <a-col :span="8">
            <a-statistic title="相似度" :value="(comparisonResult.similarityScore * 100).toFixed(1)" suffix="%" />
          </a-col>
          <a-col :span="8">
            <a-progress :percent="comparisonResult.similarityScore * 100" :stroke-color="getProgressColor(comparisonResult.similarityScore)" />
          </a-col>
          <a-col :span="8">
            <a-alert :type="getAlertType(comparisonResult.similarityScore)">
              <template #message>{{ getSimilarityDescription(comparisonResult.similarityScore) }}</template>
            </a-alert>
          </a-col>
        </a-row>

        <a-divider>详细对比</a-divider>

        <a-descriptions :column="2" bordered v-if="diffDetails">
          <a-descriptions-item label="总行数(文档1)">{{ diffDetails.totalLines1 }}</a-descriptions-item>
          <a-descriptions-item label="总行数(文档2)">{{ diffDetails.totalLines2 }}</a-descriptions-item>
          <a-descriptions-item label="新增行"><a-tag color="green">{{ diffDetails.addedLines }}</a-tag></a-descriptions-item>
          <a-descriptions-item label="删除行"><a-tag color="red">{{ diffDetails.deletedLines }}</a-tag></a-descriptions-item>
          <a-descriptions-item label="修改行"><a-tag color="orange">{{ diffDetails.modifiedLines }}</a-tag></a-descriptions-item>
          <a-descriptions-item label="未变行"><a-tag>{{ diffDetails.totalLines1 - diffDetails.addedLines - diffDetails.deletedLines - diffDetails.modifiedLines }}</a-tag></a-descriptions-item>
        </a-descriptions>

        <a-divider>差异详情</a-divider>

        <div class="diff-container" v-if="diffDetails && diffDetails.diffLines">
          <div v-for="(line, index) in diffDetails.diffLines" :key="index" class="diff-line" :class="getLineClass(line.type)">
            <span class="line-number">{{ line.lineNumber }}</span>
            <span class="line-content">
              <template v-if="line.type === 'unchanged'">{{ line.content }}</template>
              <template v-else-if="line.type === 'added'">
                <span class="diff-marker">+</span> {{ line.content }}
              </template>
              <template v-else-if="line.type === 'deleted'">
                <span class="diff-marker">-</span> {{ line.content }}
              </template>
              <template v-else-if="line.type === 'modified'">
                <div class="modified-line">
                  <div class="old-content"><span class="diff-marker">-</span> {{ line.oldContent }}</div>
                  <div class="new-content"><span class="diff-marker">+</span> {{ line.newContent }}</div>
                </div>
              </template>
            </span>
          </div>
        </div>

        <a-card title="对比摘要" size="small" style="margin-top: 16px">
          <pre style="white-space: pre-wrap">{{ comparisonResult.summary }}</pre>
        </a-card>
      </a-card>
    </a-card>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { compareDocuments, getComparisonsByDocument, type DocumentComparison } from '@/api/documentComparison'
import { getDocumentList, type KnowledgeDocument } from '@/api/knowledge'

const documents = ref<KnowledgeDocument[]>([])
const docId1 = ref<number>()
const docId2 = ref<number>()
const comparisonType = ref('text')
const comparing = ref(false)
const comparisonResult = ref<DocumentComparison | null>(null)
const history = ref<DocumentComparison[]>([])
const diffDetails = ref<any>(null)

const filterOption = (input: string, option: any) => {
  return option.children[0].children.toLowerCase().indexOf(input.toLowerCase()) >= 0
}

const loadDocuments = async () => {
  try {
    const res = await getDocumentList(1, 1000)
    if (res.data) {
      documents.value = res.data.records || res.data
    }
  } catch (error) {
    message.error('加载文档列表失败')
  }
}

const handleCompare = async () => {
  if (!docId1.value || !docId2.value) {
    message.warning('请选择两个文档进行对比')
    return
  }
  if (docId1.value === docId2.value) {
    message.warning('请选择不同的文档进行对比')
    return
  }

  comparing.value = true
  try {
    const res = await compareDocuments(docId1.value, docId2.value, comparisonType.value)
    if (res.data) {
      comparisonResult.value = res.data
      if (res.data.diffResult) {
        try {
          const parsed = JSON.parse(res.data.diffResult)
          diffDetails.value = parsed.textDiff || parsed
        } catch {
          diffDetails.value = null
        }
      }
      message.success('对比完成')
    }
  } catch (error) {
    message.error('对比失败')
  } finally {
    comparing.value = false
  }
}

const viewHistory = (item: DocumentComparison) => {
  comparisonResult.value = item
  if (item.diffResult) {
    try {
      const parsed = JSON.parse(item.diffResult)
      diffDetails.value = parsed.textDiff || parsed
    } catch {
      diffDetails.value = null
    }
  }
}

const getScoreColor = (score: number) => {
  if (score >= 0.9) return 'green'
  if (score >= 0.7) return 'blue'
  if (score >= 0.5) return 'orange'
  return 'red'
}

const getProgressColor = (score: number) => {
  if (score >= 0.9) return '#52c41a'
  if (score >= 0.7) return '#1890ff'
  if (score >= 0.5) return '#faad14'
  return '#ff4d4f'
}

const getAlertType = (score: number) => {
  if (score >= 0.9) return 'success'
  if (score >= 0.7) return 'info'
  if (score >= 0.5) return 'warning'
  return 'error'
}

const getSimilarityDescription = (score: number) => {
  if (score >= 0.9) return '两个文档内容高度相似，可能是同一文档的不同版本'
  if (score >= 0.7) return '两个文档有较多相似内容，建议检查是否存在抄袭或重复'
  if (score >= 0.5) return '两个文档有部分相似内容，可能涉及相同主题'
  return '两个文档差异较大，属于不同内容'
}

const getLineClass = (type: string) => {
  return {
    'line-unchanged': type === 'unchanged',
    'line-added': type === 'added',
    'line-deleted': type === 'deleted',
    'line-modified': type === 'modified'
  }
}

onMounted(() => {
  loadDocuments()
})
</script>

<style scoped>
.document-comparison-page {
  padding: 24px;
}
.diff-container {
  max-height: 500px;
  overflow-y: auto;
  border: 1px solid #d9d9d9;
  border-radius: 4px;
  font-family: monospace;
  font-size: 13px;
}
.diff-line {
  display: flex;
  padding: 2px 8px;
  border-bottom: 1px solid #f0f0f0;
}
.line-number {
  width: 40px;
  color: #999;
  text-align: right;
  margin-right: 16px;
}
.line-content {
  flex: 1;
}
.diff-marker {
  font-weight: bold;
  margin-right: 8px;
}
.line-added {
  background-color: #e6ffed;
}
.line-deleted {
  background-color: #ffebe9;
}
.line-modified {
  background-color: #fff7e6;
}
.old-content {
  color: #ff4d4f;
  text-decoration: line-through;
}
.new-content {
  color: #52c41a;
}
</style>
