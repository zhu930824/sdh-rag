import request from '@/utils/request'
import type { ApiResponse } from '@/types'

export interface VoiceRecord {
  id: number
  userId: number
  sessionId: string
  voiceUrl: string
  voiceDuration: number
  textContent: string
  answerText: string
  answerVoiceUrl: string
  status: number
  createTime: string
}

export function getVoiceRecords(page: number = 1, pageSize: number = 10): Promise<ApiResponse<{ records: VoiceRecord[], total: number }>> {
  return request.get('/api/voice/records', { params: { page, pageSize } })
}

export function getSessionRecords(sessionId: string): Promise<ApiResponse<VoiceRecord[]>> {
  return request.get(`/api/voice/session/${sessionId}`)
}

export function getVoiceRecord(id: number): Promise<ApiResponse<VoiceRecord>> {
  return request.get(`/api/voice/${id}`)
}

export function voiceAsk(formData: FormData): Promise<ApiResponse<VoiceRecord>> {
  return request.post('/api/voice/ask', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function textToVoice(text: string, sessionId?: string): Promise<ApiResponse<VoiceRecord>> {
  return request.post('/api/voice/text-to-voice', null, { params: { text, sessionId } })
}

export function transcribeVoice(formData: FormData): Promise<ApiResponse<string>> {
  return request.post('/api/voice/transcribe', formData, {
    headers: { 'Content-Type': 'multipart/form-data' }
  })
}

export function synthesizeSpeech(text: string): Promise<ApiResponse<Blob>> {
  return request.get('/api/voice/synthesize', { params: { text }, responseType: 'blob' })
}

export function getVoiceStats(): Promise<ApiResponse<number>> {
  return request.get('/api/voice/stats')
}

export function deleteVoiceRecord(id: number): Promise<ApiResponse<null>> {
  return request.post(`/api/voice/delete/${id}`)
}
