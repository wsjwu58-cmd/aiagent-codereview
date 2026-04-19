import { Card } from 'antd'

interface Props {
  diff?: string
}

export function DiffViewer({ diff }: Props) {
  return (
    <Card size="small" title="Diff预览">
      <pre className="agent-stream">{diff || '暂无Diff内容'}</pre>
    </Card>
  )
}
