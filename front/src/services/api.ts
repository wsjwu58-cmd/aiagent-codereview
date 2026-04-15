import axios from 'axios'

const client = axios.create({
  baseURL: '/api',
  timeout: 20000,
})

export type ReviewType = 'GIT_DIFF' | 'PASTE_CODE'

export interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

export interface AuthResponse {
  token: string
  user: {
    userId: string
    username: string
    email: string
  }
}

export interface ReviewSubmitRequest {
  type: ReviewType
  repoUrl?: string
  branch?: string
  codeContent?: string
  projectId?: string
  sessionId?: string
  language?: string
  templateId?: string
}

export interface ReviewTaskSummary {
  reviewId: string
  sessionId: string
  status: string
}

export interface ReviewIssue {
  id: string
  severity: string
  file: string
  lineNumber: number
  message: string
  ruleId: string
  suggestion: string
}

export interface ReviewTaskDetail {
  reviewId: string
  sessionId: string
  status: string
  codeContent: string
  refactoredCode: string
  report: {
    summary: string
    score: number
    totalIssues: number
    criticalCount: number
    highCount: number
    mediumCount: number
    lowCount: number
    issues: ReviewIssue[]
    suggestions: Array<{ priority: number; title: string; description: string }>
  }
}

export interface SessionSummary {
  sessionId: string
  projectId: string
  latestReviewId: string
  latestReviewStatus: string
  language: string
  latestMessagePreview: string
  lastActivity: number
  messageCount: number
  reviewCount: number
}

export interface ChatMessageRecord {
  role: 'user' | 'assistant' | string
  content: string
  timestamp: number
}

export interface HistoryRecord {
  id: string
  sessionId: string
  summary: string
  timestamp: number
}

export interface KnowledgeRecord {
  recordId: string
  reviewId: string
  sessionId: string
  projectId: string
  summary: string
  timestamp: number
}

export interface KnowledgeSearchResponse {
  query: string
  rewrittenQueries: string[]
  records: KnowledgeRecord[]
}

export interface NormRecord {
  id: string
  fileName: string
  pageNumber: number
  content: string
  summary: string
  metadata: Record<string, unknown>
}

export interface NormSummary {
  fileId: string
  fileName: string
  projectId: string
  description: string
  pageCount: number
  uploadedAt: number
}

export interface NormUploadResult {
  fileId: string
  fileName: string
  projectId: string
  pageCount: number
  description: string
  uploadedAt: number
}

export interface ReactChatRequest {
  sessionId?: string
  message: string
  projectId?: string
}

export interface StreamEventPayload {
  event: string
  data: Record<string, unknown>
}

export async function register(username: string, password: string, email: string) {
  const res = await client.post<ApiResponse<AuthResponse>>('/auth/register', { username, password, email })
  return res.data.data
}

export async function login(username: string, password: string) {
  const res = await client.post<ApiResponse<AuthResponse>>('/auth/login', { username, password })
  return res.data.data
}

export async function submitReview(payload: ReviewSubmitRequest) {
  const res = await client.post<ApiResponse<ReviewTaskSummary>>('/review/submit', payload)
  return res.data.data
}

export async function getReview(reviewId: string) {
  const res = await client.get<ApiResponse<ReviewTaskDetail>>(`/review/${reviewId}`)
  return res.data.data
}

export async function cancelReview(sessionId: string) {
  await client.post(`/review/cancel?sessionId=${encodeURIComponent(sessionId)}`)
}

export async function listSessions() {
  const res = await client.get<ApiResponse<SessionSummary[]>>('/chat/sessions')
  return Array.isArray(res.data.data) ? res.data.data : []
}

export async function listChatMessages(sessionId: string) {
  const res = await client.get<ApiResponse<ChatMessageRecord[]>>(`/chat/messages?sessionId=${encodeURIComponent(sessionId)}`)
  return Array.isArray(res.data.data) ? res.data.data : []
}

export async function sendChat(sessionId: string | undefined, message: string, projectId?: string) {
  const res = await client.post<ApiResponse<{ sessionId: string; answer: string; references: string[] }>>('/chat/send', {
    sessionId,
    message,
    projectId,
  })
  return res.data.data
}

export async function searchHistory(params: { sessionId?: string; keyword?: string; projectId?: string }) {
  const query = new URLSearchParams()
  if (params.sessionId) {
    query.set('sessionId', params.sessionId)
  }
  if (params.keyword) {
    query.set('keyword', params.keyword)
  }
  if (params.projectId) {
    query.set('projectId', params.projectId)
  }
  const suffix = query.toString()
  const url = suffix ? `/history/search?${suffix}` : '/history/search'
  const res = await client.get<ApiResponse<{ keyword: string; records: HistoryRecord[] }>>(url)
  return res.data.data
}

export async function searchKnowledge(params: { query?: string; projectId?: string; sessionId?: string; topK?: number }) {
  const query = new URLSearchParams()
  if (params.query) {
    query.set('query', params.query)
  }
  if (params.projectId) {
    query.set('projectId', params.projectId)
  }
  if (params.sessionId) {
    query.set('sessionId', params.sessionId)
  }
  if (typeof params.topK === 'number') {
    query.set('topK', String(params.topK))
  }
  const suffix = query.toString()
  const url = suffix ? `/knowledge/search?${suffix}` : '/knowledge/search'
  const res = await client.get<ApiResponse<KnowledgeSearchResponse>>(url)
  return res.data.data
}

export async function searchNorms(params: { query: string; projectId?: string; limit?: number }) {
  const query = new URLSearchParams()
  query.set('query', params.query)
  if (params.projectId) {
    query.set('projectId', params.projectId)
  }
  if (typeof params.limit === 'number') {
    query.set('limit', String(params.limit))
  }
  const res = await client.get<ApiResponse<NormRecord[]>>(`/chat/search-norms?${query.toString()}`)
  return Array.isArray(res.data.data) ? res.data.data : []
}

export async function listNorms(projectId?: string) {
  const suffix = projectId ? `?projectId=${encodeURIComponent(projectId)}` : ''
  const res = await client.get<ApiResponse<NormSummary[]>>(`/chat/norms${suffix}`)
  return Array.isArray(res.data.data) ? res.data.data : []
}

export async function uploadNorm(file: File, projectId: string, description?: string) {
  const formData = new FormData()
  formData.append('file', file)
  formData.append('projectId', projectId)
  if (description) {
    formData.append('description', description)
  }
  const res = await client.post<ApiResponse<NormUploadResult>>('/chat/upload-norm', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  })
  return res.data.data
}

export async function streamReactChat(
  payload: ReactChatRequest,
  onEvent: (payload: StreamEventPayload) => void,
  signal?: AbortSignal,
) {
  const response = await fetch('/api/chat/react', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify(payload),
    signal,
  })

  if (!response.ok || !response.body) {
    throw new Error(`深度分析请求失败: ${response.status}`)
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) {
      break
    }

    buffer += decoder.decode(value, { stream: true })
    const blocks = buffer.split('\n\n')
    buffer = blocks.pop() ?? ''

    for (const block of blocks) {
      const parsed = parseSseBlock(block)
      if (parsed) {
        onEvent(parsed)
      }
    }
  }

  if (buffer.trim()) {
    const parsed = parseSseBlock(buffer)
    if (parsed) {
      onEvent(parsed)
    }
  }
}

export function openSse(sessionId: string) {
  return new EventSource(`/api/sse/stream/${sessionId}`)
}

function parseSseBlock(block: string): StreamEventPayload | null {
  const lines = block.split(/\r?\n/).map((line) => line.trim()).filter(Boolean)
  if (lines.length === 0) {
    return null
  }

  let event = 'message'
  const dataLines: string[] = []

  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim()
    }
    if (line.startsWith('data:')) {
      dataLines.push(line.slice(5).trim())
    }
  }

  if (dataLines.length === 0) {
    return null
  }

  try {
    return {
      event,
      data: JSON.parse(dataLines.join('\n')) as Record<string, unknown>,
    }
  } catch {
    return {
      event,
      data: { raw: dataLines.join('\n') },
    }
  }
}
