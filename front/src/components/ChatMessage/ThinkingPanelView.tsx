import { Button, Space, Tag, Typography } from 'antd'
import type { ThinkingStepItem } from '../../hooks/useReActStream'

interface Props {
  steps: ThinkingStepItem[]
  status: 'thinking' | 'done' | 'error'
  collapsed: boolean
  onToggle: () => void
}

export function ThinkingPanel({ steps, status, collapsed, onToggle }: Props) {
  if (!steps.length && status === 'done') {
    return null
  }

  if (collapsed) {
    return (
      <Button type="link" size="small" className="thinking-toggle" onClick={onToggle}>
        查看推理过程 ({steps.length} 步)
      </Button>
    )
  }

  return (
    <div className="thinking-panel">
      <div className="thinking-panel__header">
        <Space size={8}>
          <Typography.Text strong>推理过程</Typography.Text>
          <Tag color={status === 'thinking' ? 'processing' : status === 'error' ? 'error' : 'success'}>
            {status === 'thinking' ? '分析中' : status === 'error' ? '失败' : '完成'}
          </Tag>
        </Space>
        <Button type="link" size="small" onClick={onToggle}>
          收起
        </Button>
      </div>

      <div className="thinking-panel__body">
        {steps.map((step) => (
          <div key={`${step.step}-${step.agentId}-${step.type}`} className="thinking-step">
            <div className="thinking-step__meta">
              <Tag>{step.agentName || step.agentId}</Tag>
              <Typography.Text type="secondary">#{step.step}</Typography.Text>
            </div>
            {step.type === 'TOOL_CALL' ? (
              <div className="thinking-step__content">
                <Typography.Text strong>工具：{step.tool}</Typography.Text>
                {step.output ? <Typography.Paragraph>{step.output}</Typography.Paragraph> : null}
              </div>
            ) : (
              <Typography.Paragraph className="thinking-step__content">{step.content}</Typography.Paragraph>
            )}
          </div>
        ))}
      </div>
    </div>
  )
}
