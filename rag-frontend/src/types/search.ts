export interface MetadataFilter {
  department?: string
  docType?: string
  dateFrom?: string
  dateTo?: string
}

export interface RankedChunk {
  documentId: number
  documentTitle: string
  documentSummary?: string
  content: string
  chunkIndex: number
  totalChunks: number
  relevanceScore: number
  freshnessScore: number
  finalScore: number
  department?: string
  docType?: string
  docDate?: string
}

export interface SearchResult {
  success: boolean
  message?: string
  chunks: RankedChunk[]
  originalQuery: string
  fallbackTriggered: boolean
  fallbackReason?: string
}

export interface KnowledgeBaseConfigExtended {
  enableSummaryIndex?: boolean
  summaryCoarseTopK?: number
  enableColdArchive?: boolean
  hotMinResults?: number
  mmrLambda?: number
  recencyAlpha?: number
  coarseTopK?: number
  fineTopK?: number
}

export const DOC_TYPES = [
  { value: 'policy', label: '制度规范' },
  { value: 'manual', label: '操作手册' },
  { value: 'contract', label: '合同协议' },
  { value: 'report', label: '分析报告' },
  { value: 'faq', label: 'FAQ问答' },
] as const
