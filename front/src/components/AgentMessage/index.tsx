import { Card, Tag, Typography } from 'antd'
import { MarkdownRender } from '../MarkdownRender'
import { Bot } from 'lucide-react'

interface Props {
  name: string
  content: string
}

// Agent accent colors
const agentColors: Record<string, string> = {
  security: '#ef4444',
  performance: '#06b6d4',
  architecture: '#7c3aed',
  documentation: '#22c55e',
  general: '#f59e0b',
}

export function AgentMessage({ name, content }: Props) {
  const color = agentColors[name.toLowerCase()] || '#7c3aed'
  const initials = name.slice(0, 2).toUpperCase()

  return (
    <Card
      size="small"
      style={{
        background: 'rgba(13,20,36,0.8)',
        border: `1px solid ${color}18`,
        borderRadius: 12,
        overflow: 'hidden',
        height: '100%',
      }}
      styles={{
        header: {
          borderBottom: `1px solid ${color}15`,
          background: `${color}08`,
          padding: '10px 14px',
          display: 'flex', alignItems: 'center', gap: 8,
        },
        body: { padding: 0, minHeight: 80 },
      }}
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{
            width: 24, height: 24, borderRadius: 6,
            background: `${color}20`,
            border: `1px solid ${color}30`,
            display: 'flex', alignItems: 'center', justifyContent: 'center',
            fontSize: '10px', fontFamily: '"JetBrains Mono", monospace', fontWeight: 700,
            color: color,
          }}>
            {initials}
          </div>
          <div>
            <span style={{ fontSize: '12.5px', fontFamily: '"Syne", sans-serif', fontWeight: 600, color }}>
              {name}
            </span>
            <Tag style={{
              marginLeft: 6,
              background: `${color}15`,
              color: color,
              border: `1px solid ${color}25`,
              borderRadius: 5,
              fontSize: '9.5px',
              padding: '0 6px',
            }}>
              Agent
            </Tag>
          </div>
        </div>
      }
    >
      <div className="agent-message-scroll">
      {content ? (
        <MarkdownRender content={content} />
      ) : (
        <Typography.Text type="secondary" style={{ fontSize: '12px' }}>
          暂无输出
        </Typography.Text>
      )}
      </div>
    </Card>
  )
}
