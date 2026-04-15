import { Button, Card, Col, Empty, List, Row, Space, Statistic, Tag, Typography } from 'antd'
import type { SessionSummary } from '../../services/api'

interface Props {
  username: string
  currentSessionId?: string
  currentReviewId?: string
  sessions: SessionSummary[]
  onSelectSession: (sessionId: string) => void
}

export function DashboardPage({ username, currentSessionId, currentReviewId, sessions, onSelectSession }: Props) {
  const currentSession = sessions.find((item) => item.sessionId === currentSessionId)

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <div className="page-card">
        <Typography.Title level={2} className="brand-title" style={{ marginBottom: 8 }}>
          欢迎回来，{username}
        </Typography.Title>
        <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
          这里会展示你最近参与的会话、最新审查任务和会话上下文。
        </Typography.Paragraph>
      </div>

      <Row gutter={16}>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="当前会话" value={currentSessionId || '未选择'} />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="最近审查任务" value={currentReviewId || currentSession?.latestReviewId || '暂无'} />
          </Card>
        </Col>
        <Col xs={24} md={8}>
          <Card>
            <Statistic title="历史会话数" value={sessions.length} />
          </Card>
        </Col>
      </Row>

      <Card title="历史会话">
        {sessions.length > 0 ? (
          <List
            itemLayout="vertical"
            dataSource={sessions}
            renderItem={(item) => {
              const active = item.sessionId === currentSessionId
              return (
                <List.Item
                  actions={[
                    <Button key="switch" type={active ? 'primary' : 'default'} onClick={() => onSelectSession(item.sessionId)}>
                      {active ? '当前会话' : '切换到此会话'}
                    </Button>,
                  ]}
                >
                  <Space wrap style={{ marginBottom: 8 }}>
                    <Tag color={active ? 'blue' : 'default'}>{item.sessionId}</Tag>
                    {item.projectId ? <Tag color="gold">项目: {item.projectId}</Tag> : null}
                    {item.latestReviewStatus ? <Tag color="green">状态: {item.latestReviewStatus}</Tag> : null}
                    {item.language ? <Tag color="purple">语言: {item.language}</Tag> : null}
                  </Space>
                  <Typography.Paragraph style={{ marginBottom: 8 }}>
                    {item.latestMessagePreview || '当前会话还没有可展示的消息摘要'}
                  </Typography.Paragraph>
                  <Typography.Text type="secondary">
                    最近活跃时间：{item.lastActivity ? new Date(item.lastActivity).toLocaleString() : '未知'}
                    {' | '}
                    消息数：{item.messageCount}
                    {' | '}
                    审查数：{item.reviewCount}
                  </Typography.Text>
                </List.Item>
              )
            }}
          />
        ) : (
          <Empty description="当前还没有历史会话" />
        )}
      </Card>
    </Space>
  )
}