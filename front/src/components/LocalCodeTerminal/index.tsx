import { Card, Space, Tag, Typography } from 'antd'

interface Props {
  lines: string[]
  status: 'idle' | 'running' | 'done' | 'error'
  summary?: string
  modifiedFiles?: string[]
}

export function LocalCodeTerminal({ lines, status, summary, modifiedFiles }: Props) {
  const color = status === 'done' ? 'green' : status === 'error' ? 'red' : status === 'running' ? 'blue' : 'default'

  return (
    <Card
      title="本地代码终端"
      extra={<Tag color={color}>{status}</Tag>}
      style={{ background: 'rgba(13,20,36,0.55)', border: '1px solid rgba(148,163,184,0.08)' }}
      styles={{ body: { padding: 0 } }}
    >
      <div style={{
        minHeight: 320,
        maxHeight: 460,
        overflow: 'auto',
        padding: '16px 18px',
        background: '#050a14',
        fontFamily: '"JetBrains Mono", monospace',
        fontSize: 12,
        lineHeight: 1.7,
        color: '#cbd5e1',
        whiteSpace: 'pre-wrap',
      }}>
        {lines.length > 0 ? lines.join('\n') : '> 等待开始本地代码分析...'}
      </div>
      <div style={{ padding: '14px 18px', borderTop: '1px solid rgba(148,163,184,0.08)' }}>
        <Space direction="vertical" size={6} style={{ width: '100%' }}>
          {summary ? <Typography.Text style={{ color: '#94a3b8' }}>{summary}</Typography.Text> : null}
          {modifiedFiles && modifiedFiles.length > 0 ? (
            <Typography.Text style={{ color: '#67e8f9' }}>
              已修改文件: {modifiedFiles.join(', ')}
            </Typography.Text>
          ) : null}
        </Space>
      </div>
    </Card>
  )
}
