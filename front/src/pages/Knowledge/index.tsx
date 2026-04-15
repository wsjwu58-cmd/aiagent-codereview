import { useMemo, useState } from 'react'
import { Button, Card, Empty, Input, List, Space, Tag, Typography, message } from 'antd'
import { searchKnowledge, type KnowledgeRecord, type SessionSummary } from '../../services/api'

interface Props {
  sessionId?: string
  sessions: SessionSummary[]
}

export function KnowledgePage({ sessionId, sessions }: Props) {
  const [query, setQuery] = useState('')
  const [records, setRecords] = useState<KnowledgeRecord[]>([])
  const [rewrittenQueries, setRewrittenQueries] = useState<string[]>([])
  const [loading, setLoading] = useState(false)

  const currentSession = useMemo(
    () => sessions.find((item) => item.sessionId === sessionId),
    [sessions, sessionId],
  )

  const onSearch = async () => {
    setLoading(true)
    try {
      const data = await searchKnowledge({
        query: query.trim() || undefined,
        projectId: currentSession?.projectId || undefined,
        sessionId,
        topK: 8,
      })
      setRecords(data.records)
      setRewrittenQueries(data.rewrittenQueries)
    } catch {
      message.error('知识检索失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card className="page-card" title="知识库检索">
        <Space direction="vertical" size={12} style={{ width: '100%' }}>
          <Typography.Paragraph type="secondary" style={{ marginBottom: 0 }}>
            这里会基于当前会话和项目的历史审查结果做查询重写与知识检索，便于排查重复问题和复用过往建议。
          </Typography.Paragraph>
          <Space wrap>
            {sessionId ? <Tag color="blue">当前会话: {sessionId}</Tag> : <Tag>未选择会话</Tag>}
            {currentSession?.projectId ? <Tag color="gold">项目: {currentSession.projectId}</Tag> : null}
          </Space>
          <Space.Compact style={{ width: '100%' }}>
            <Input
              value={query}
              onChange={(e) => setQuery(e.target.value)}
              placeholder="例如：上次审查有哪些高危问题、当前会话的重构建议"
              onPressEnter={() => void onSearch()}
            />
            <Button type="primary" loading={loading} onClick={() => void onSearch()}>
              开始检索
            </Button>
          </Space.Compact>
          <div>
            <Typography.Text strong>查询重写：</Typography.Text>
            <Space wrap style={{ marginTop: 8 }}>
              {rewrittenQueries.length > 0 ? rewrittenQueries.map((item) => <Tag key={item}>{item}</Tag>) : <Tag>暂无</Tag>}
            </Space>
          </div>
        </Space>
      </Card>

      <Card className="page-card" title="检索结果">
        <List
          dataSource={records}
          locale={{ emptyText: <Empty description="暂无知识检索结果" /> }}
          renderItem={(item) => (
            <List.Item>
              <Space direction="vertical" size={6} style={{ width: '100%' }}>
                <Space wrap>
                  {item.reviewId ? <Tag color="purple">审查: {item.reviewId}</Tag> : null}
                  {item.sessionId ? <Tag color="blue">会话: {item.sessionId}</Tag> : null}
                  {item.projectId ? <Tag color="gold">项目: {item.projectId}</Tag> : null}
                  <Typography.Text type="secondary">{new Date(item.timestamp).toLocaleString()}</Typography.Text>
                </Space>
                <Typography.Paragraph style={{ marginBottom: 0, whiteSpace: 'pre-wrap' }}>
                  {item.summary}
                </Typography.Paragraph>
              </Space>
            </List.Item>
          )}
        />
      </Card>
    </Space>
  )
}
