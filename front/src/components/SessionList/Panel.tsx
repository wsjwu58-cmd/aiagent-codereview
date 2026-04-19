import { Button, Empty, List, Tag, Typography } from 'antd'
import type { NormSummary, SessionSummary } from '../../services/api'
import { Plus, FileText, Layers, MessageSquare } from 'lucide-react'

function stripMarkdown(text: string): string {
  return text
    .replace(/#{1,6}\s/g, '')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/\*([^*]+)\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/```[\s\S]*?```/g, '[代码]')
    .replace(/\[([^\]]+)\]\([^)]+\)/g, '$1')
    .replace(/\n+/g, ' ')
    .trim()
}

interface Props {
  sessions: SessionSummary[]
  currentSessionId?: string
  norms: NormSummary[]
  onSelectSession: (sessionId?: string) => void
  onCreateSession: () => void
}

export function SessionList({ sessions, currentSessionId, norms, onSelectSession, onCreateSession }: Props) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: '100%', padding: '14px 0' }}>

      {/* Header */}
      <div style={{ padding: '0 14px 14px', borderBottom: '1px solid rgba(148,163,184,0.05)' }}>
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 10 }}>
          <span style={{ fontSize: '10px', fontWeight: 700, color: '#4a5568', textTransform: 'uppercase', letterSpacing: '0.1em', display: 'flex', alignItems: 'center', gap: 5 }}>
            <Layers size={11} /> 会话
          </span>
          <Button
            size="small"
            icon={<Plus size={12} />}
            onClick={onCreateSession}
            style={{
              height: 26, fontSize: '11.5px',
              background: 'rgba(6,182,212,0.08)',
              border: '1px solid rgba(6,182,212,0.2)',
              color: '#67e8f9',
              borderRadius: 7,
              display: 'flex', alignItems: 'center', gap: 4,
              fontFamily: '"Noto Sans SC", sans-serif',
            }}
          >
            新建
          </Button>
        </div>
      </div>

      {/* Session items */}
      <div style={{ flex: 1, overflowY: 'auto', padding: '8px 10px' }}>
        {sessions.length > 0 ? (
          <List
            size="small"
            dataSource={sessions}
            locale={{ emptyText: <span style={{ color: '#4a5568', fontSize: '12px' }}>暂无会话</span> }}
            renderItem={(item) => {
              const active = item.sessionId === currentSessionId
              return (
                <List.Item
                  key={item.sessionId}
                  onClick={() => onSelectSession(item.sessionId)}
                  style={{
                    padding: '9px 10px',
                    borderRadius: 8,
                    cursor: 'pointer',
                    marginBottom: 3,
                    border: 'none',
                    background: active ? 'rgba(6,182,212,0.1)' : 'transparent',
                    borderLeft: active ? '2px solid #06b6d4' : '2px solid transparent',
                    transition: 'all 0.15s ease',
                  }}
                  onMouseEnter={(e) => {
                    if (!active) (e.currentTarget as HTMLDivElement).style.background = 'rgba(148,163,184,0.04)'
                  }}
                  onMouseLeave={(e) => {
                    if (!active) (e.currentTarget as HTMLDivElement).style.background = 'transparent'
                  }}
                >
                  <div style={{ width: '100%' }}>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginBottom: 4 }}>
                      <MessageSquare size={11} color={active ? '#06b6d4' : '#4a5568'} />
                      <span style={{ fontSize: '11.5px', fontFamily: '"JetBrains Mono", monospace', color: active ? '#67e8f9' : '#64748b', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', flex: 1 }}>
                        {item.latestMessagePreview ? stripMarkdown(item.latestMessagePreview).slice(0, 20) : `会话 ${item.sessionId?.slice(0, 8)}`}
                      </span>
                      {active && (
                        <div style={{ width: 6, height: 6, borderRadius: '50%', background: '#06b6d4', boxShadow: '0 0 8px #06b6d4', flexShrink: 0 }} />
                      )}
                    </div>
                    <div style={{ fontSize: '11px', color: '#4a5568', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', marginBottom: 5 }}>
                      {item.latestMessagePreview || '暂无消息'}
                    </div>
                    <div style={{ display: 'flex', gap: 5, flexWrap: 'wrap' }}>
                      {item.language && (
                        <Tag style={{ fontSize: '9.5px', padding: '0 6px', height: 16, lineHeight: '14px', background: 'rgba(124,58,237,0.1)', color: '#a78bfa', border: 'none', margin: 0 }}>
                          {item.language}
                        </Tag>
                      )}
                      {item.reviewCount !== undefined && item.reviewCount > 0 && (
                        <Tag style={{ fontSize: '9.5px', padding: '0 6px', height: 16, lineHeight: '14px', background: 'rgba(6,182,212,0.1)', color: '#67e8f9', border: 'none', margin: 0 }}>
                          审 {item.reviewCount}
                        </Tag>
                      )}
                      {item.messageCount !== undefined && item.messageCount > 0 && (
                        <Tag style={{ fontSize: '9.5px', padding: '0 6px', height: 16, lineHeight: '14px', background: 'rgba(148,163,184,0.06)', color: '#64748b', border: 'none', margin: 0 }}>
                          聊 {item.messageCount}
                        </Tag>
                      )}
                    </div>
                  </div>
                </List.Item>
              )
            }}
          />
        ) : (
          <div style={{ padding: '24px 0', textAlign: 'center' }}>
            <Empty description={<span style={{ color: '#4a5568', fontSize: '12px' }}>暂无会话记录</span>} />
          </div>
        )}
      </div>

      {/* Norm files */}
      {norms.length > 0 && (
        <div style={{ borderTop: '1px solid rgba(148,163,184,0.05)', padding: '12px 14px', flexShrink: 0 }}>
          <div style={{ fontSize: '10px', fontWeight: 700, color: '#4a5568', textTransform: 'uppercase', letterSpacing: '0.1em', marginBottom: 8, display: 'flex', alignItems: 'center', gap: 5 }}>
            <FileText size={10} /> 规范文档
          </div>
          <List
            size="small"
            dataSource={norms}
            locale={{ emptyText: <span style={{ color: '#4a5568', fontSize: '11px' }}>暂无规范</span> }}
            renderItem={(item) => (
              <div style={{
                padding: '6px 8px',
                background: 'rgba(234,179,8,0.06)',
                border: '1px solid rgba(234,179,8,0.1)',
                borderRadius: 7,
                marginBottom: 4,
                display: 'flex', alignItems: 'center', gap: 6,
              }}>
                <FileText size={11} color="#fde047" />
                <span style={{ fontSize: '11.5px', color: '#94a3b8', flex: 1, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                  {item.fileName}
                </span>
                {item.pageCount && (
                  <Tag style={{ fontSize: '9.5px', padding: '0 5px', height: 15, lineHeight: '13px', background: 'rgba(234,179,8,0.12)', color: '#fde047', border: 'none', margin: 0 }}>
                    {item.pageCount}页
                  </Tag>
                )}
              </div>
            )}
          />
        </div>
      )}
    </div>
  )
}
