/*
import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Empty, Input, List, Select, Space, Tag, Typography, message } from 'antd'
import { listChatMessages, sendChat, type ChatMessageRecord, type SessionSummary } from '../../services/api'

interface Props {
  sessionId?: string
  sessions: SessionSummary[]
  currentProjectId?: string
  onSessionChange: (sessionId?: string) => void
  refreshSessions: (preferredSessionId?: string) => Promise<void>
}
*/
export { ChatPage } from './Workspace'

export function ChatPage({ sessionId, sessions, currentProjectId, onSessionChange, refreshSessions }: Props) {
  const [text, setText] = useState('')
  const [messages, setMessages] = useState<ChatMessageRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [loadingHistory, setLoadingHistory] = useState(false)

  const currentSession = useMemo(
    () => sessions.find((item) => item.sessionId === sessionId),
    [sessions, sessionId],
  )

  useEffect(() => {
    if (!sessionId) {
      setMessages([])
      return
    }
    setLoadingHistory(true)
    listChatMessages(sessionId)
      .then((data) => setMessages(data))
      .catch(() => message.error('加载会话消息失败'))
      .finally(() => setLoadingHistory(false))
  }, [sessionId])

  const onSend = async () => {
    if (!text.trim()) {
      return
    }
    const question = text.trim()
    setText('')
    if (sessionId) {
      setMessages((prev) => [...prev, { role: 'user', content: question, timestamp: Date.now() }])
    }
    setLoading(true)
    try {
      const data = await sendChat(sessionId, question, currentProjectId)
      if (data.sessionId !== sessionId) {
        onSessionChange(data.sessionId)
      }
      await refreshSessions(data.sessionId)
      const latestMessages = await listChatMessages(data.sessionId)
      setMessages(latestMessages)
    } catch {
      message.error('发送失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card className="page-card" title="Chat对话">
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Space wrap>
            <Select
              style={{ minWidth: 320 }}
              value={sessionId || '__new__'}
              onChange={(value) => onSessionChange(value === '__new__' ? undefined : value)}
              options={[
                { value: '__new__', label: '新建对话会话' },
                ...sessions.map((item) => ({
                  value: item.sessionId,
                  label: `${item.sessionId}${item.projectId ? ` · ${item.projectId}` : ''}`,
                })),
              ]}
            />
            {currentSession?.projectId ? <Tag color="gold">项目: {currentSession.projectId}</Tag> : null}
            {currentSession?.latestReviewId ? <Tag color="purple">最近审查: {currentSession.latestReviewId}</Tag> : null}
          </Space>

          <List
            size="small"
            loading={loadingHistory}
            dataSource={messages}
            locale={{ emptyText: <Empty description={sessionId ? '当前会话暂无消息' : '请选择历史会话或直接发送消息创建新会话'} /> }}
            renderItem={(item) => (
              <List.Item>
                <Space direction="vertical" size={4} style={{ width: '100%' }}>
                  <Typography.Text strong>{item.role === 'user' ? '你' : 'AI助手'}</Typography.Text>
                  <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>{item.content}</Typography.Paragraph>
                  <Typography.Text type="secondary">{new Date(item.timestamp).toLocaleString()}</Typography.Text>
                </Space>
              </List.Item>
            )}
          />

          <Space.Compact style={{ width: '100%' }}>
            <Input
              value={text}
              onChange={(e) => setText(e.target.value)}
              placeholder="请输入你的问题，未选择会话时会自动创建新会话"
              onPressEnter={(e) => {
                e.preventDefault()
                void onSend()
              }}
            />
            <Button type="primary" loading={loading} onClick={() => void onSend()}>
              发送
            </Button>
          </Space.Compact>
        </Space>
      </Card>
    </Space>
  )
}
