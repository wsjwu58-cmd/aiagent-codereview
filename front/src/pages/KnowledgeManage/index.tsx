import { useEffect, useState } from 'react'
import { Button, Card, Input, Popconfirm, Segmented, Space, Table, Tag, Typography, message } from 'antd'
import {
  batchDeleteKnowledgeRecords,
  deleteKnowledgeRecord,
  getKnowledgeRecords,
  type KnowledgeDeleteItem,
  type KnowledgeManageRecord,
  type KnowledgeRecordType,
} from '../../services/api'

const typeOptions = [
  { label: '全部', value: 'ALL' },
  { label: '审查历史', value: 'REVIEW_HISTORY' },
  { label: 'PDF规范', value: 'PDF_NORM' },
  { label: '聊天记录', value: 'CHAT_HISTORY' },
] as const

interface Props {
  currentProjectId?: string
}

export function KnowledgeManagePage({ currentProjectId }: Props) {
  const [type, setType] = useState<'ALL' | KnowledgeRecordType>('ALL')
  const [keyword, setKeyword] = useState('')
  const [loading, setLoading] = useState(false)
  const [records, setRecords] = useState<KnowledgeManageRecord[]>([])
  const [selectedRows, setSelectedRows] = useState<KnowledgeManageRecord[]>([])
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [total, setTotal] = useState(0)

  const load = async (nextPage = page, nextPageSize = pageSize) => {
    setLoading(true)
    try {
      const data = await getKnowledgeRecords({
        type: type === 'ALL' ? undefined : type,
        projectId: currentProjectId,
        keyword: keyword.trim() || undefined,
        page: nextPage - 1,
        size: nextPageSize,
      })
      setRecords(data.content)
      setTotal(data.totalElements)
      setPage(nextPage)
      setPageSize(nextPageSize)
    } catch {
      message.error('知识库记录加载失败')
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    void load(1, pageSize)
  }, [type, currentProjectId])

  const onDeleteOne = async (record: KnowledgeManageRecord) => {
    try {
      await deleteKnowledgeRecord(record.id, record.type)
      message.success('记录已删除')
      await load()
    } catch {
      message.error('删除失败')
    }
  }

  const onDeleteSelected = async () => {
    const payload: KnowledgeDeleteItem[] = selectedRows.map((item) => ({ id: item.id, type: item.type }))
    if (payload.length === 0) {
      return
    }
    try {
      await batchDeleteKnowledgeRecords(payload)
      setSelectedRows([])
      message.success('批量删除完成')
      await load()
    } catch {
      message.error('批量删除失败')
    }
  }

  return (
    <div style={{ maxWidth: 1180, margin: '0 auto', padding: '32px 24px' }}>
      <div style={{ marginBottom: 20 }}>
        <Typography.Title level={2} style={{ color: '#e2e8f0', marginBottom: 6 }}>
          知识库管理
        </Typography.Title>
        <Typography.Paragraph style={{ color: '#64748b', marginBottom: 0 }}>
          统一查看并清理审查历史、PDF 规范和聊天记录。
        </Typography.Paragraph>
      </div>

      <Card style={{ marginBottom: 18, background: 'rgba(13,20,36,0.75)', border: '1px solid rgba(148,163,184,0.08)' }}>
        <Space direction="vertical" size={14} style={{ width: '100%' }}>
          <Segmented
            options={typeOptions as unknown as { label: string; value: string }[]}
            value={type}
            onChange={(value) => setType(value as 'ALL' | KnowledgeRecordType)}
          />
          <Space.Compact style={{ width: '100%' }}>
            <Input
              value={keyword}
              onChange={(event) => setKeyword(event.target.value)}
              placeholder="搜索 ID、项目、摘要或文件名"
              onPressEnter={() => void load(1, pageSize)}
            />
            <Button type="primary" onClick={() => void load(1, pageSize)}>搜索</Button>
            <Button onClick={() => void load(page, pageSize)}>刷新</Button>
          </Space.Compact>
          <Space wrap>
            <Popconfirm
              title="确认删除选中记录？"
              onConfirm={() => void onDeleteSelected()}
              disabled={selectedRows.length === 0}
            >
              <Button danger disabled={selectedRows.length === 0}>删除选中</Button>
            </Popconfirm>
            <Typography.Text style={{ color: '#94a3b8' }}>
              已选择 {selectedRows.length} 项
            </Typography.Text>
          </Space>
        </Space>
      </Card>

      <Card style={{ background: 'rgba(13,20,36,0.75)', border: '1px solid rgba(148,163,184,0.08)' }}>
        <Table<KnowledgeManageRecord>
          rowKey={(record) => `${record.type}-${record.id}`}
          loading={loading}
          dataSource={records}
          rowSelection={{
            onChange: (_, rows) => setSelectedRows(rows),
          }}
          pagination={{
            current: page,
            pageSize,
            total,
            onChange: (nextPage, nextPageSize) => void load(nextPage, nextPageSize),
          }}
          columns={[
            {
              title: '类型',
              dataIndex: 'type',
              width: 120,
              render: (value: KnowledgeRecordType) => <Tag color={value === 'REVIEW_HISTORY' ? 'purple' : value === 'PDF_NORM' ? 'gold' : 'blue'}>{value}</Tag>,
            },
            {
              title: 'ID',
              dataIndex: 'id',
              width: 220,
              ellipsis: true,
            },
            {
              title: '项目/会话',
              render: (_, record) => (
                <Space direction="vertical" size={0}>
                  <Typography.Text style={{ color: '#cbd5e1' }}>{record.projectId || '-'}</Typography.Text>
                  <Typography.Text type="secondary">{record.sessionId || '-'}</Typography.Text>
                </Space>
              ),
            },
            {
              title: '摘要',
              dataIndex: 'summary',
              ellipsis: true,
            },
            {
              title: '时间',
              dataIndex: 'createdAt',
              width: 180,
              render: (value: number) => new Date(value).toLocaleString(),
            },
            {
              title: '操作',
              width: 100,
              render: (_, record) => (
                <Popconfirm title="确认删除该记录？" onConfirm={() => void onDeleteOne(record)}>
                  <Button danger type="link">删除</Button>
                </Popconfirm>
              ),
            },
          ]}
        />
      </Card>
    </div>
  )
}
