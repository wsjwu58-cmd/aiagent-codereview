/*
import { Button, Empty, Input, Space, Typography } from 'antd'
import { ChatMessage } from '../../components/ChatMessage'
import { FileUpload } from '../../components/FileUpload'
import { SessionList } from '../../components/SessionList'
import { useConversationalChat } from '../../hooks/useConversationalChat'
import type { SessionSummary } from '../../services/api'

interface Props {
  sessionId?: string
  sessions: SessionSummary[]
  currentProjectId?: string
  onSessionChange: (sessionId?: string) => void
  refreshSessions: (preferredSessionId?: string) => Promise<void>
}
*/
export { ChatPage } from './WorkspacePage'

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
          <div>
            <Typography.Title level={4}>Code Review 对话工作台</Typography.Title>
            <Typography.Paragraph>
              支持普通问答、深度分析、多 Agent 协作和 PDF 规范辅助审查。
            </Typography.Paragraph>
          </div>
        </div>

        <div className="chat-workspace__messages">
          {messages.length ? (
            messages.map((item) => <ChatMessage key={item.id} message={item} />)
          ) : (
            <Empty description={loadingHistory ? '正在加载会话消息...' : '发送第一条消息，开始新的分析会话'} />
          )}
        </div>

        <div className="chat-workspace__composer">
          <Input.TextArea
            value={draft}
            autoSize={{ minRows: 3, maxRows: 8 }}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="输入你的问题、代码片段或希望重点分析的方向"
          />
          <Space wrap>
            <Button type="primary" loading={sending} onClick={() => void sendSimpleMessage()}>
              发送
            </Button>
            <Button loading={sending} onClick={() => void sendDeepAnalysis()}>
              深度分析
            </Button>
            <FileUpload uploading={uploading} onUpload={uploadNormFile} />
          </Space>
        </div>
      </section>
    </div>
  )
}
