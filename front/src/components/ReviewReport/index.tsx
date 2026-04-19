import { Button, Card, Table, Tag, Typography, message } from 'antd'
import { useState } from 'react'
import { downloadReviewPdf, type ReviewTaskDetail } from '../../services/api'
import { MarkdownRender } from '../MarkdownRender'
import { AlertTriangle, CheckCircle, Shield, Zap, AlertCircle, Download } from 'lucide-react'

interface Props {
  detail?: ReviewTaskDetail
}

const severityColor: Record<string, { bg: string; color: string; border: string; label: string }> = {
  CRITICAL: { bg: 'rgba(239,68,68,0.12)', color: '#fca5a5', border: 'rgba(239,68,68,0.25)', label: '严重' },
  HIGH:     { bg: 'rgba(249,115,22,0.12)', color: '#fdba74', border: 'rgba(249,115,22,0.25)', label: '高危' },
  MEDIUM:   { bg: 'rgba(234,179,8,0.12)', color: '#fde047', border: 'rgba(234,179,8,0.25)', label: '中危' },
  LOW:      { bg: 'rgba(6,182,212,0.1)', color: '#67e8f9', border: 'rgba(6,182,212,0.2)', label: '低危' },
}

export function ReviewReport({ detail }: Props) {
  const [downloading, setDownloading] = useState(false)

  if (!detail) {
    return (
      <Card style={{ background: 'rgba(13,20,36,0.6)', border: '1px solid rgba(148,163,184,0.08)', borderRadius: 14 }}>
        <div style={{ textAlign: 'center', padding: '20px 0' }}>
          <Typography.Text type="secondary">暂无报告</Typography.Text>
        </div>
      </Card>
    )
  }

  const onDownload = async () => {
    try {
      setDownloading(true)
      const blob = await downloadReviewPdf(detail.reviewId)
      const url = window.URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `review_${detail.reviewId}.pdf`
      anchor.click()
      window.URL.revokeObjectURL(url)
    } catch {
      message.error('PDF 瀵煎嚭澶辫触')
    } finally {
      setDownloading(false)
    }
  }

  const agentOutputEntries = Object.entries(detail.agentOutputs ?? {})
    .filter(([, content]) => Boolean(content?.trim()))

  if (!detail.report) {
    const fallbackContent = agentOutputEntries
      .map(([agent, content]) => `## ${agent}\n\n${content}`)
      .join('\n\n')
    const taskFinished = ['COMPLETED', 'FAILED', 'CANCELLED'].includes(detail.status)

    if (fallbackContent) {
      return (
        <Card
          title={
            <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
              <Zap size={16} color="#7c3aed" />
              <span style={{ fontFamily: '"Syne", sans-serif', fontWeight: 600, fontSize: '14px' }}>瀹℃煡鎶ュ憡</span>
            </div>
          }
          extra={taskFinished && (
            <Button size="small" loading={downloading} icon={<Download size={14} />} onClick={() => void onDownload()}>
              瀵煎嚭 PDF
            </Button>
          )}
          style={{ background: 'rgba(13,20,36,0.6)', border: '1px solid rgba(148,163,184,0.08)', borderRadius: 14 }}
          styles={{ header: { borderBottom: '1px solid rgba(148,163,184,0.06)', padding: '14px 18px' }, body: { padding: '18px' } }}
        >
          <Typography.Text style={{ display: 'block', color: '#94a3b8', marginBottom: 12 }}>
            结构化报告暂未生成，以下为本次审查已产生的 Agent 结论。
          </Typography.Text>
          <div style={{
            padding: '12px 14px',
            background: 'rgba(124,58,237,0.05)',
            border: '1px solid rgba(124,58,237,0.12)',
            borderRadius: 10,
          }}>
            <MarkdownRender content={fallbackContent} />
          </div>
        </Card>
      )
    }

    return (
      <Card style={{ background: 'rgba(13,20,36,0.6)', border: '1px solid rgba(148,163,184,0.08)', borderRadius: 14 }}>
        <div style={{ textAlign: 'center', padding: '20px 0', display: 'flex', alignItems: 'center', justifyContent: 'center', gap: 10 }}>
          <div style={{ width: 20, height: 20, border: '2px solid rgba(6,182,212,0.3)', borderTopColor: '#06b6d4', borderRadius: '50%', animation: 'spin 0.8s linear infinite' }} />
          <Typography.Text style={{ color: '#64748b' }}>审查进行中，报告生成后会自动展示</Typography.Text>
        </div>
      </Card>
    )
  }

  const { report } = detail
  const suggestions = report.suggestions ?? []
  const issues = report.issues ?? []

  const onDownloadFromReport = async () => {
    try {
      setDownloading(true)
      const blob = await downloadReviewPdf(detail.reviewId)
      const url = window.URL.createObjectURL(blob)
      const anchor = document.createElement('a')
      anchor.href = url
      anchor.download = `review_${detail.reviewId}.pdf`
      anchor.click()
      window.URL.revokeObjectURL(url)
    } catch {
      message.error('PDF 导出失败')
    } finally {
      setDownloading(false)
    }
  }

  const severityItems = [
    { key: 'critical', count: report.criticalCount, ...severityColor.CRITICAL, icon: AlertTriangle },
    { key: 'high', count: report.highCount, ...severityColor.HIGH, icon: AlertCircle },
    { key: 'medium', count: report.mediumCount, ...severityColor.MEDIUM, icon: Shield },
    { key: 'low', count: report.lowCount, ...severityColor.LOW, icon: CheckCircle },
  ].filter((s) => s.count > 0)

  return (
    <Card
      title={
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <Zap size={16} color="#7c3aed" />
          <span style={{ fontFamily: '"Syne", sans-serif', fontWeight: 600, fontSize: '14px' }}>审查报告</span>
        </div>
      }
      extra={
        <Button size="small" loading={downloading} icon={<Download size={14} />} onClick={() => void onDownloadFromReport()}>
          瀵煎嚭 PDF
        </Button>
      }
      style={{
        background: 'rgba(13,20,36,0.6)',
        border: '1px solid rgba(148,163,184,0.08)',
        borderRadius: 14,
      }}
      styles={{ header: { borderBottom: '1px solid rgba(148,163,184,0.06)', padding: '14px 18px' }, body: { padding: '18px' } }}
    >
      {/* Score & stats bar */}
      <div style={{
        display: 'flex',
        gap: 16,
        marginBottom: 18,
        padding: '14px 16px',
        background: 'rgba(148,163,184,0.03)',
        border: '1px solid rgba(148,163,184,0.06)',
        borderRadius: 10,
        flexWrap: 'wrap',
      }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{
            fontSize: '1.6rem', fontFamily: '"JetBrains Mono", monospace', fontWeight: 700,
            color: report.score >= 80 ? '#22c55e' : report.score >= 60 ? '#eab308' : '#ef4444',
            lineHeight: 1,
            textShadow: `0 0 20px ${report.score >= 80 ? 'rgba(34,197,94,0.4)' : report.score >= 60 ? 'rgba(234,179,8,0.4)' : 'rgba(239,68,68,0.4)'}`,
          }}>
            {report.score}
          </div>
          <div>
            <div style={{ fontSize: '10px', color: '#4a5568', textTransform: 'uppercase', letterSpacing: '0.05em' }}>综合评分</div>
            <div style={{ fontSize: '10px', color: '#64748b' }}>/ 100</div>
          </div>
        </div>
        <div style={{ width: 1, background: 'rgba(148,163,184,0.1)', alignSelf: 'stretch' }} />
        <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
          <div style={{ fontSize: '1.6rem', fontFamily: '"JetBrains Mono", monospace', fontWeight: 700, color: '#e2e8f0', lineHeight: 1 }}>
            {report.totalIssues}
          </div>
          <div>
            <div style={{ fontSize: '10px', color: '#4a5568', textTransform: 'uppercase', letterSpacing: '0.05em' }}>问题总数</div>
          </div>
        </div>
        <div style={{ flex: 1, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
          {severityItems.map((item) => (
            <div
              key={item.key}
              style={{
                display: 'flex', alignItems: 'center', gap: 6,
                padding: '5px 10px',
                background: item.bg,
                border: `1px solid ${item.border}`,
                borderRadius: 8,
              }}
            >
              <item.icon size={13} color={item.color} />
              <span style={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '14px', fontWeight: 600, color: item.color }}>{item.count}</span>
              <span style={{ fontSize: '10px', color: item.color }}>{item.label}</span>
            </div>
          ))}
        </div>
      </div>

      {/* Summary */}
      {report.summary && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: '11px', fontWeight: 600, color: '#4a5568', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 8 }}>
            总结
          </div>
          <div style={{
            padding: '12px 14px',
            background: 'rgba(124,58,237,0.05)',
            border: '1px solid rgba(124,58,237,0.12)',
            borderRadius: 10,
          }}>
            <MarkdownRender content={report.summary} />
          </div>
        </div>
      )}

      {/* Suggestions */}
      {suggestions.length > 0 && (
        <div style={{ marginBottom: 16 }}>
          <div style={{ fontSize: '11px', fontWeight: 600, color: '#4a5568', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 10 }}>
            优化建议 ({suggestions.length})
          </div>
          <div style={{ display: 'flex', flexDirection: 'column', gap: 8 }}>
            {suggestions.map((item, i) => (
              <div key={i} style={{
                padding: '10px 14px',
                background: 'rgba(148,163,184,0.03)',
                border: '1px solid rgba(148,163,184,0.07)',
                borderRadius: 9,
                display: 'flex', gap: 10,
              }}>
                <div style={{
                  width: 22, height: 22, borderRadius: 6,
                  background: 'rgba(6,182,212,0.12)',
                  border: '1px solid rgba(6,182,212,0.2)',
                  display: 'flex', alignItems: 'center', justifyContent: 'center',
                  fontSize: '10px', fontFamily: '"JetBrains Mono", monospace', fontWeight: 700,
                  color: '#06b6d4', flexShrink: 0,
                }}>
                  P{item.priority}
                </div>
                <div>
                  <div style={{ fontSize: '13px', fontWeight: 600, color: '#e2e8f0', marginBottom: 3 }}>{item.title}</div>
                  <div style={{ fontSize: '12px', color: '#64748b', lineHeight: 1.5 }}>{item.description}</div>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}

      {/* Issues table */}
      {issues.length > 0 && (
        <div>
          <div style={{ fontSize: '11px', fontWeight: 600, color: '#4a5568', textTransform: 'uppercase', letterSpacing: '0.08em', marginBottom: 10 }}>
            问题明细 ({issues.length})
          </div>
          <Table
            size="small"
            rowKey="id"
            pagination={{ pageSize: 8, showSizeChanger: false }}
            dataSource={issues}
            locale={{ emptyText: '当前没有问题明细' }}
            scroll={{ y: 300 }}
            style={{
              background: 'rgba(13,20,36,0.4)',
              borderRadius: 10,
              border: '1px solid rgba(148,163,184,0.06)',
              overflow: 'hidden',
            }}
            columns={[
              {
                title: '级别',
                dataIndex: 'severity',
                width: 80,
                render: (level: string) => {
                  const s = severityColor[level] || severityColor.LOW
                  return (
                    <Tag style={{ background: s.bg, color: s.color, border: `1px solid ${s.border}`, borderRadius: 5, fontSize: '10px', padding: '1px 7px' }}>
                      {s.label}
                    </Tag>
                  )
                },
              },
              { title: '规则', dataIndex: 'ruleId', width: 120, render: (v: string) => <span style={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '11px', color: '#64748b' }}>{v}</span> },
              { title: '位置', width: 160, render: (_, row) => <span style={{ fontFamily: '"JetBrains Mono", monospace', fontSize: '11px', color: '#67e8f9' }}>{row.file}:{row.lineNumber}</span> },
              { title: '描述', dataIndex: 'message', ellipsis: true },
              { title: '建议', dataIndex: 'suggestion', ellipsis: true, render: (v: string) => <span style={{ color: '#94a3b8' }}>{v}</span> },
            ]}
          />
        </div>
      )}
    </Card>
  )
}
