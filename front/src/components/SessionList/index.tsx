import { Button, Divider, Empty, List, Tag, Typography } from 'antd'
import type { NormSummary, SessionSummary } from '../../services/api'
import { NormList } from './NormListView'

function stripMarkdown(text: string): string {
  return text
    .replace(/#{1,6}\s/g, '')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/```[\s\S]*?```/g, '[代码]')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/\n+/g, ' ')
    .trim()
}

interface Props {
  sessions: SessionSummary[]
  currentSessionId?: string
  norms: NormSummary[]
  onSelectSession: (sessionId?: string) => void
  onCreateSession: () => void
}

export function SessionList({ sessions, currentSessionId, norms, onSelectSession, onCreateSession }: Props) {
  return (
    <div className="session-list">
      <div className="session-list__header">
        <Typography.Title level={5}>会话列表</Typography.Title>
        <Button
          type="primary"
          block
          onClick={onCreateSession}
          style={{
            background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
            border: 'none',
          }}
        >
          + 新建会话
        </Button>
      </div>

      <div className="session-list__items">
        {sessions.length ? (
          <List
            size="small"
            dataSource={sessions}
            renderItem={(item) => (
              <List.Item
                className={`session-item ${item.sessionId === currentSessionId ? 'session-item--active' : ''}`}
                onClick={() => onSelectSession(item.sessionId)}
                style={{ padding: '10px 12px' }}
              >
                <Typography.Text style={{ color: 'var(--text-primary)', fontSize: '13px' }}>
                  {item.latestMessagePreview ? stripMarkdown(item.latestMessagePreview).slice(0, 20) : `会话 ${item.sessionId.slice(0, 8)}`}
                </Typography.Text>
              </List.Item>
            )}
          />
        ) : (
          <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无会话记录" />
        )}
      </div>

      {norms.length > 0 && (
        <>
          <Divider style={{ margin: '16px 0', borderColor: 'var(--border-color)' }} />
          <div className="norm-section">
            <Typography.Title level={5} className="norm-section__title">
              规范文档
            </Typography.Title>
            <NormList norms={norms} />
          </div>
        </>
      )}
    </div>
  )
}
