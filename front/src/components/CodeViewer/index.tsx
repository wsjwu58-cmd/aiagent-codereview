import { Card, Typography } from 'antd'

interface Props {
  code?: string
  title?: string
}

export function CodeViewer({ code, title = '代码预览' }: Props) {
  return (
    <Card size="small" title={title}>
      <pre className="agent-stream">{code || '暂无内容'}</pre>
    </Card>
  )
}
