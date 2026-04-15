import { Card, Descriptions, Empty, List, Space, Table, Tag } from 'antd'
import type { ReviewTaskDetail } from '../../services/api'
import { MarkdownRender } from '../MarkdownRender'

interface Props {
  detail?: ReviewTaskDetail
}

const colorMap: Record<string, string> = {
  CRITICAL: 'red',
  HIGH: 'volcano',
  MEDIUM: 'gold',
  LOW: 'blue',
}

export function ReviewReport({ detail }: Props) {
  if (!detail) {
    return <Card title="审查报告">暂无报告</Card>
  }

  if (!detail.report) {
    return <Card title="审查报告">审查进行中，报告生成后会自动展示。</Card>
  }

  const suggestions = detail.report.suggestions ?? []
  const issues = detail.report.issues ?? []

  return (
    <Card title="审查报告">
      <Space direction="vertical" size={16} style={{ width: '100%' }}>
        <Descriptions column={2} size="small">
          <Descriptions.Item label="任务状态">{detail.status}</Descriptions.Item>
          <Descriptions.Item label="评分">{detail.report.score}</Descriptions.Item>
          <Descriptions.Item label="问题总数">{detail.report.totalIssues}</Descriptions.Item>
          <Descriptions.Item label="严重问题">{detail.report.criticalCount}</Descriptions.Item>
          <Descriptions.Item label="高危问题">{detail.report.highCount}</Descriptions.Item>
          <Descriptions.Item label="中危问题">{detail.report.mediumCount}</Descriptions.Item>
          <Descriptions.Item label="低危问题">{detail.report.lowCount}</Descriptions.Item>
          <Descriptions.Item label="审查 ID">{detail.reviewId}</Descriptions.Item>
        </Descriptions>

        <Card size="small" title="总结">
          <MarkdownRender content={detail.report.summary || '暂无总结'} />
        </Card>

        <Card size="small" title="优化建议" style={{ maxHeight: 300, overflow: 'auto' }}>
          {suggestions.length > 0 ? (
            <List
              dataSource={suggestions}
              renderItem={(item) => (
                <List.Item>
                  <List.Item.Meta title={`P${item.priority} - ${item.title}`} description={item.description} />
                </List.Item>
              )}
            />
          ) : (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="当前没有结构化建议" />
          )}
        </Card>

        <Table
          size="small"
          rowKey="id"
          pagination={{ pageSize: 5 }}
          dataSource={issues}
          locale={{ emptyText: '当前没有问题明细' }}
          scroll={{ y: 240 }}
          columns={[
            {
              title: '级别',
              dataIndex: 'severity',
              render: (level: string) => <Tag color={colorMap[level] || 'default'}>{level}</Tag>,
            },
            { title: '规则', dataIndex: 'ruleId' },
            { title: '位置', render: (_, row) => `${row.file}:${row.lineNumber}` },
            { title: '描述', dataIndex: 'message' },
            { title: '建议', dataIndex: 'suggestion' },
          ]}
        />
      </Space>
    </Card>
  )
}