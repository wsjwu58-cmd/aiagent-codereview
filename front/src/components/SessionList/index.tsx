import { Button, Divider, Empty, List, Tag, Typography } from 'antd'
import type { SessionSummary } from '../../services/api'
import type { NormSummary } from '../../services/api'
import { NormList } from './NormList'

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
        <Typography.Title level={5}>会话</Typography.Title>
        <Button type="primary" block onClick={onCreateSession}>
          新建会话
        </Button>
      </div>

      {sessions.length ? (
        <List
          size="small"
          dataSource={sessions}
          renderItem={(item) => (
            <List.Item
              className={`session-item ${item.sessionId === currentSessionId ? 'session-item--active' : ''}`}
              onClick={() => onSelectSession(item.sessionId)}
            >
              <div className="session-item__body">
                <Typography.Text strong>{item.latestMessagePreview || item.sessionId}</Typography.Text>
                <div className="session-item__meta">
                  {item.projectId ? <Tag color="blue">{item.projectId}</Tag> : null}
                  <Typography.Text type="secondary">{new Date(item.lastActivity).toLocaleDateString()}</Typography.Text>
                </div>
              </div>
            </List.Item>
          )}
        />
      ) : (
        <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无历史会话" />
      )}

      <Divider />
      <Typography.Title level={5}>规范文档</Typography.Title>
      <NormList norms={norms} />
    </div>
  )
}
