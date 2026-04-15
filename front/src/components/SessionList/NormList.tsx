import { Empty, List, Tag, Typography } from 'antd'
import type { NormSummary } from '../../services/api'

interface Props {
  norms: NormSummary[]
}

export function NormList({ norms }: Props) {
  if (!norms.length) {
    return <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="暂无规范文档" />
  }

  return (
    <List
      size="small"
      dataSource={norms}
      renderItem={(item) => (
        <List.Item>
          <div className="norm-item">
            <Typography.Text strong>{item.fileName}</Typography.Text>
            <div className="norm-item__meta">
              <Tag color="gold">{item.pageCount} 页</Tag>
              {item.description ? <Typography.Text type="secondary">{item.description}</Typography.Text> : null}
            </div>
          </div>
        </List.Item>
      )}
    />
  )
}
