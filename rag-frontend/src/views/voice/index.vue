<template>
  <div class="voice-qa-page">
    <a-card title="语音问答" :bordered="false">
      <template #extra>
        <a-button type="primary" @click="showHelp = true">
          使用说明
        </a-button>
      </template>

      <a-row :gutter="24">
        <a-col :span="12">
          <a-card title="语音输入" size="small">
            <div class="voice-input-section">
              <div class="record-controls">
                <a-button 
                  :type="isRecording ? 'danger' : 'primary'" 
                  size="large" 
                  shape="circle"
                  @click="toggleRecording"
                >
                  <template #icon>
                    <mic-outlined v-if="!isRecording" />
                    <stop-outlined v-else />
                  </template>
                </a-button>
                <p class="record-status">{{ isRecording ? '录音中...' : '点击开始录音' }}</p>
              </div>

              <a-divider>或者</a-divider>

              <a-textarea
                v-model:value="textInput"
                placeholder="输入文字问题..."
                :rows="4"
                @pressEnter="handleTextSubmit"
              />
              <a-button type="primary" block style="margin-top: 16px" @click="handleTextSubmit">
                发送问题
              </a-button>
            </div>
          </a-card>
        </a-col>

        <a-col :span="12">
          <a-card title="回答" size="small">
            <div v-if="currentRecord" class="answer-section">
              <div class="question-box">
                <strong>问题：</strong>
                <p>{{ currentRecord.textContent }}</p>
              </div>
              <a-divider />
              <div class="answer-box">
                <strong>回答：</strong>
                <p>{{ currentRecord.answerText || '正在处理中...' }}</p>
              </div>
              <div v-if="currentRecord.answerVoiceUrl" class="voice-player">
                <a-button @click="playAnswerVoice">
                  <template #icon><sound-outlined /></template>
                  播放语音回答
                </a-button>
              </div>
            </div>
            <a-empty v-else description="暂无问答记录" />
          </a-card>
        </a-col>
      </a-row>

      <a-divider />

      <a-card title="历史记录" size="small">
        <a-table :dataSource="records" :columns="columns" :pagination="pagination" :loading="loading" rowKey="id">
          <template #bodyCell="{ column, record }">
            <template v-if="column.key === 'status'">
              <a-tag :color="record.status === 1 ? 'success' : 'error'">
                {{ record.status === 1 ? '成功' : '失败' }}
              </a-tag>
            </template>
            <template v-if="column.key === 'action'">
              <a-space>
                <a-button type="link" size="small" @click="viewRecord(record)">查看</a-button>
                <a-popconfirm title="确定删除?" @confirm="handleDelete(record.id)">
                  <a-button type="link" size="small" danger>删除</a-button>
                </a-popconfirm>
              </a-space>
            </template>
          </template>
        </a-table>
      </a-card>
    </a-card>

    <a-modal v-model:open="showHelp" title="使用说明" :footer="null">
      <a-list :data-source="helpItems">
        <template #renderItem="{ item }">
          <a-list-item>{{ item }}</a-list-item>
        </template>
      </a-list>
    </a-modal>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { message } from 'ant-design-vue'
import { MicOutlined, StopOutlined, SoundOutlined } from '@ant-design/icons-vue'
import { getVoiceRecords, textToVoice, deleteVoiceRecord, type VoiceRecord } from '@/api/voice'

const loading = ref(false)
const isRecording = ref(false)
const textInput = ref('')
const currentRecord = ref<VoiceRecord | null>(null)
const records = ref<VoiceRecord[]>([])
const showHelp = ref(false)
const sessionId = ref('')

const helpItems = [
  '1. 点击麦克风按钮开始录音，再次点击停止',
  '2. 录音完成后系统会自动进行语音识别并回答',
  '3. 您也可以直接输入文字问题',
  '4. 支持语音和文字两种方式的回答',
  '5. 历史记录会自动保存，可以随时查看'
]

const columns = [
  { title: 'ID', dataIndex: 'id', key: 'id', width: 80 },
  { title: '问题', dataIndex: 'textContent', key: 'textContent', ellipsis: true },
  { title: '状态', dataIndex: 'status', key: 'status', width: 100 },
  { title: '创建时间', dataIndex: 'createTime', key: 'createTime', width: 180 },
  { title: '操作', key: 'action', width: 150 }
]

const pagination = reactive({
  current: 1,
  pageSize: 10,
  total: 0,
  onChange: (page: number) => {
    pagination.current = page
    loadRecords()
  }
})

const loadRecords = async () => {
  loading.value = true
  try {
    const res = await getVoiceRecords(pagination.current, pagination.pageSize)
    if (res.data) {
      records.value = res.data.records
      pagination.total = res.data.total
    }
  } finally {
    loading.value = false
  }
}

const toggleRecording = () => {
  if (isRecording.value) {
    isRecording.value = false
    message.info('录音已停止')
  } else {
    isRecording.value = true
    message.info('开始录音...')
  }
}

const handleTextSubmit = async () => {
  if (!textInput.value.trim()) {
    message.warning('请输入问题')
    return
  }

  try {
    const res = await textToVoice(textInput.value, sessionId.value)
    if (res.data) {
      currentRecord.value = res.data
      if (!sessionId.value && res.data.sessionId) {
        sessionId.value = res.data.sessionId
      }
      textInput.value = ''
      loadRecords()
      message.success('问题已提交')
    }
  } catch (error) {
    message.error('提交失败')
  }
}

const viewRecord = (record: VoiceRecord) => {
  currentRecord.value = record
}

const handleDelete = async (id: number) => {
  try {
    await deleteVoiceRecord(id)
    message.success('删除成功')
    loadRecords()
  } catch (error) {
    message.error('删除失败')
  }
}

const playAnswerVoice = () => {
  if (currentRecord.value?.answerVoiceUrl) {
    const audio = new Audio(currentRecord.value.answerVoiceUrl)
    audio.play()
  }
}

onMounted(() => {
  sessionId.value = `session_${Date.now()}`
  loadRecords()
})
</script>

<style scoped>
.voice-qa-page {
  padding: 24px;
}

.voice-input-section {
  text-align: center;
}

.record-controls {
  padding: 24px;
}

.record-status {
  margin-top: 16px;
  color: #666;
}

.answer-section {
  padding: 16px;
}

.question-box,
.answer-box {
  margin-bottom: 16px;
}

.question-box strong,
.answer-box strong {
  color: #1890ff;
}

.voice-player {
  margin-top: 16px;
  text-align: center;
}
</style>
