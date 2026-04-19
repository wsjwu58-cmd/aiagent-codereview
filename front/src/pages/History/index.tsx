import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Empty, Input, List, Select, Space, Tag, Typography, message } from 'antd'
import { searchHistory, type HistoryRecord, type SessionSummary } from '../../services/api'

interface Props {
  sessionId?: string
  sessions: SessionSummary[]
  onSelectSession: (sessionId: string) => void
}

export function HistoryPage({ sessionId, sessions, onSelectSession }: Props) {
  const [keyword, setKeyword] = useState('')
  const [records, setRecords] = useState<HistoryRecord[]>([])
  const [loading, setLoading] = useState(false)
  const [filterSessionId, setFilterSessionId] = useState<string>('')

  const currentSession = useMemo(
    () => sessions.find((item) => item.sessionId === filterSessionId),
    [sessions, filterSessionId],
  )

  useEffect(() => {
    void loadHistory('', undefined)
  }, [])

  useEffect(() => {
    if (sessionId && !filterSessionId) {
      void loadHistory(keyword, undefined)
    }
  }, [sessionId])

  const loadHistory = async (nextKeyword = keyword, nextSessionId = filterSessionId) => {
    setLoading(true)
    try {
      const data = await searchHistory({
        keyword: nextKeyword || undefined,
        sessionId: nextSessionId || undefined,
        projectId: currentSession?.projectId || undefined,
      })
      setRecords(data.records)
    } catch {
      message.error('历史检索失败')
    } finally {
      setLoading(false)
    }
  }

  return (
    <Card className="page-card" title="历史检索">
      <Space direction="vertical" size={12} style={{ width: '100%' }}>
        <Space wrap style={{ width: '100%' }}>
          <Select
            style={{ minWidth: 320 }}
            value={filterSessionId}
            onChange={(value) => setFilterSessionId(value)}
            options={[
              { value: '', label: '全部会话' },
              ...sessions.map((item) => ({
                value: item.sessionId,
                label: `${item.sessionId}${item.projectId ? ` · ${item.projectId}` : ''}`,
              })),
            ]}
          />
          <Button onClick={() => { setFilterSessionId(''); void loadHistory(keyword, ''); }}>查看全部</Button>
        </Space>

        <Space.Compact style={{ width: '100%' }}>
          <Input
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            placeholder="输入关键词检索历史对话和审查记录"
            onPressEnter={() => void loadHistory()}
          />
          <Button type="primary" loading={loading} onClick={() => void loadHistory()}>
            搜索
          </Button>
        </Space.Compact>

        <List
          bordered
          loading={loading}
          dataSource={records}
          locale={{ emptyText: <Empty description="暂无历史记录" /> }}
          renderItem={(item) => (
            <List.Item
              actions={item.sessionId ? [<Button key="switch" type="link" onClick={() => onSelectSession(item.sessionId)}>切换到该会话</Button>] : []}
            >
              <Space direction="vertical" size={4} style={{ width: '100%' }}>
                <Space wrap>
                  <Tag color="blue">{item.sessionId || '无会话'}</Tag>
                  <Typography.Text type="secondary">{new Date(item.timestamp).toLocaleString()}</Typography.Text>
                </Space>
                <Typography.Paragraph style={{ marginBottom: 0 }}>{item.summary}</Typography.Paragraph>
              </Space>
            </List.Item>
          )}
        />
      </Space>
    </Card>
  )
}