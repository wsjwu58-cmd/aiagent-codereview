import { Card, Tag } from 'antd'
import { MarkdownRender } from '../MarkdownRender'

interface Props {
  name: string
  content: string
}

export function AgentMessage({ name, content }: Props) {
  return (
    <Card size="small" title={<Tag color="cyan">{name}</Tag>}>
      <MarkdownRender content={content || '暂无输出'} />
    </Card>
  )
}