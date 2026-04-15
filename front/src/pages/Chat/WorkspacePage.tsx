import { Button, Empty, Input, Space, Typography } from 'antd'
import { ChatMessage } from '../../components/ChatMessage/MessageCard'
import { FileUpload } from '../../components/FileUpload'
import { SessionList } from '../../components/SessionList/Panel'
import { useConversationalChat } from '../../hooks/useConversationalChatClean'
import type { SessionSummary } from '../../services/api'

interface Props {
  sessionId?: string
  sessions: SessionSummary[]
  currentProjectId?: string
  onSessionChange: (sessionId?: string) => void
  refreshSessions: (preferredSessionId?: string) => Promise<void>
}

export function ChatPage({ sessionId, sessions, currentProjectId, onSessionChange, refreshSessions }: Props) {
  const {
    draft,
    setDraft,
    messages,
    norms,
    loadingHistory,
    sending,
    uploading,
    sendSimpleMessage,
    sendDeepAnalysis,
    uploadNormFile,
  } = useConversationalChat({
    sessionId,
    currentProjectId,
    onSessionChange,
    refreshSessions,
  })

  return (
    <div className="chat-workspace">
      <aside className="chat-workspace__sidebar page-card">
        <SessionList
          sessions={sessions}
          currentSessionId={sessionId}
          norms={norms}
          onSelectSession={onSessionChange}
          onCreateSession={() => onSessionChange(undefined)}
        />
      </aside>

      <section className="chat-workspace__main page-card">
        <div className="chat-workspace__header">
          <Typography.Title level={4}>代码审查工作台</Typography.Title>
          <Typography.Paragraph style={{ margin: 0, color: 'var(--text-secondary)' }}>
            通过 AI Agent 提问、分析代码，基于上传的 PDF 规范进行回答
          </Typography.Paragraph>
        </div>

        <div className="chat-workspace__messages">
          {messages.length ? (
            messages.map((item) => <ChatMessage key={item.id} message={item} />)
          ) : (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description={
                <span style={{ color: 'var(--text-muted)' }}>
                  {loadingHistory ? '加载消息中...' : '发送消息开始新的分析会话'}
                </span>
              }
            />
          )}
        </div>

        <div className="chat-workspace__composer">
          <Input.TextArea
            value={draft}
            autoSize={{ minRows: 3, maxRows: 8 }}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="输入问题、代码片段或要分析的代码..."
          />
          <Space wrap>
            <Button type="primary" loading={sending} onClick={() => void sendSimpleMessage()}>
              快速问答
            </Button>
            <Button
              style={{
                background: 'linear-gradient(135deg, #8b5cf6 0%, #3b82f6 100%)',
                border: 'none',
                color: '#fff',
              }}
              loading={sending}
              onClick={() => void sendDeepAnalysis()}
            >
              深度分析
            </Button>
            <FileUpload uploading={uploading} onUpload={uploadNormFile} />
          </Space>
        </div>
      </section>
    </div>
  )
}
