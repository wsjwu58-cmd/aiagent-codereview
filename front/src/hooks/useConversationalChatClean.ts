import { useEffect, useMemo, useState } from 'react'
import { message } from 'antd'
import {
  listChatMessages,
  listNorms,
  sendChat,
  type ChatMessageRecord,
  type NormSummary,
} from '../services/api'
import { usePdfUpload } from './usePdfUpload'
import { useReActStream, type ThinkingStepItem } from './useReActStream'

export interface ConversationMessage extends ChatMessageRecord {
  id: string
  thinkingSteps?: ThinkingStepItem[]
  analysisStatus?: 'thinking' | 'done' | 'error'
}

interface UseConversationalChatProps {
  sessionId?: string
  currentProjectId?: string
  onSessionChange: (sessionId?: string) => void
  refreshSessions: (preferredSessionId?: string) => Promise<void>
}

export function useConversationalChat({
  sessionId,
  currentProjectId,
  onSessionChange,
  refreshSessions,
}: UseConversationalChatProps) {
  const [draft, setDraft] = useState('')
  const [messages, setMessages] = useState<ConversationMessage[]>([])
  const [norms, setNorms] = useState<NormSummary[]>([])
  const [loadingHistory, setLoadingHistory] = useState(false)
  const [sending, setSending] = useState(false)
  const reactStream = useReActStream()

  const effectiveProjectId = useMemo(() => currentProjectId || sessionId || 'default-chat', [currentProjectId, sessionId])

  const refreshNorms = async () => {
    const data = await listNorms(effectiveProjectId)
    setNorms(data)
  }

  const { upload, uploading } = usePdfUpload(async () => {
    await refreshNorms()
  })

  useEffect(() => {
    if (!sessionId) {
      setMessages([])
      return
    }

    setLoadingHistory(true)
    listChatMessages(sessionId)
      .then((records) => setMessages(normalizeMessages(records)))
      .catch(() => message.error('Failed to load session messages'))
      .finally(() => setLoadingHistory(false))
  }, [sessionId])

  useEffect(() => {
    void refreshNorms().catch(() => setNorms([]))
  }, [effectiveProjectId])

  const sendSimpleMessage = async () => {
    const question = draft.trim()
    if (!question) {
      return
    }

    setDraft('')
    setSending(true)
    try {
      const data = await sendChat(sessionId, question, effectiveProjectId)
      if (data.sessionId !== sessionId) {
        onSessionChange(data.sessionId)
      }
      await refreshSessions(data.sessionId)
      const records = await listChatMessages(data.sessionId)
      setMessages(normalizeMessages(records))
    } catch {
      message.error('Failed to send chat message')
    } finally {
      setSending(false)
    }
  }

  const sendDeepAnalysis = async () => {
    const question = draft.trim()
    if (!question) {
      return
    }

    const now = Date.now()
    const analysisId = `analysis-${now}`
    const userId = `user-${now}`
    let resolvedSessionId = sessionId

    setDraft('')
    setSending(true)
    setMessages((prev) => [
      ...prev,
      { id: userId, role: 'user', content: question, timestamp: now },
      { id: analysisId, role: 'assistant', content: '', timestamp: now, thinkingSteps: [], analysisStatus: 'thinking' },
    ])

    try {
      await reactStream.start(
        {
          sessionId,
          message: question,
          projectId: effectiveProjectId,
        },
        {
          onConnected: (payload) => {
            const nextSessionId = String(payload.sessionId ?? '')
            if (nextSessionId) {
              resolvedSessionId = nextSessionId
            }
          },
          onStep: (step) => {
            setMessages((prev) =>
              prev.map((item) =>
                item.id === analysisId
                  ? {
                      ...item,
                      thinkingSteps: [...(item.thinkingSteps ?? []), step],
                    }
                  : item,
              ),
            )
          },
          onDone: (payload) => {
            setMessages((prev) =>
              prev.map((item) =>
                item.id === analysisId
                  ? {
                      ...item,
                      content: String(payload.content ?? ''),
                      analysisStatus: 'done',
                    }
                  : item,
              ),
            )
          },
          onError: (reason) => {
            setMessages((prev) =>
              prev.map((item) =>
                item.id === analysisId
                  ? {
                      ...item,
                      content: reason,
                      analysisStatus: 'error',
                    }
                  : item,
              ),
            )
          },
        },
      )

      if (resolvedSessionId && resolvedSessionId !== sessionId) {
        onSessionChange(resolvedSessionId)
      }
      if (resolvedSessionId) {
        await refreshSessions(resolvedSessionId)
        const records = await listChatMessages(resolvedSessionId)
        setMessages(normalizeMessages(records))
      }
    } catch {
      message.error('Deep analysis failed')
    } finally {
      setSending(false)
    }
  }

  const uploadNormFile = async (file: File) => {
    await upload(file, effectiveProjectId)
  }

  return {
    draft,
    setDraft,
    messages,
    norms,
    loadingHistory,
    sending,
    uploading,
    reactStatus: reactStream.status,
    sendSimpleMessage,
    sendDeepAnalysis,
    uploadNormFile,
  }
}

function normalizeMessages(records: ChatMessageRecord[]): ConversationMessage[] {
  return records.map((item, index) => ({
    ...item,
    id: `${item.role}-${item.timestamp}-${index}`,
  }))
}
