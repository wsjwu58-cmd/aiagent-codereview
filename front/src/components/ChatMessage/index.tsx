/*
import { useState } from 'react'
import { Avatar, Card, Typography } from 'antd'
import { ThinkingPanel } from './ThinkingPanel'
import type { ThinkingStepItem } from '../../hooks/useReActStream'

interface MessageProps {
  message: {
    role: string
    content: string
    timestamp: number
    thinkingSteps?: ThinkingStepItem[]
    analysisStatus?: 'thinking' | 'done' | 'error'
  }
}
*/
export { ChatMessage } from './MessageCard'

export function ChatMessage({ message }: MessageProps) {
  const isUser = message.role === 'user'
  const [collapsed, setCollapsed] = useState(message.analysisStatus !== 'thinking')

  return (
    <div className={`chat-message ${isUser ? 'chat-message--user' : 'chat-message--assistant'}`}>
      <Avatar className="chat-message__avatar">{isUser ? '我' : 'AI'}</Avatar>
      <Card className="chat-message__card" bordered={false}>
        {!isUser && message.thinkingSteps?.length ? (
          <ThinkingPanel
            steps={message.thinkingSteps}
            status={message.analysisStatus ?? 'done'}
            collapsed={collapsed}
            onToggle={() => setCollapsed((prev) => !prev)}
          />
        ) : null}

        <Typography.Paragraph className="chat-message__text">
          {message.content || (message.analysisStatus === 'thinking' ? '深度分析中...' : '')}
        </Typography.Paragraph>
        <Typography.Text type="secondary">{new Date(message.timestamp).toLocaleString()}</Typography.Text>
      </Card>
    </div>
  )
}
