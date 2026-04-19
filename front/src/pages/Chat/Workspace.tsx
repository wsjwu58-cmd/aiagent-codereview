import { useState } from 'react'
import { Button, Empty, Input, Space, Typography, message } from 'antd'
import { ChatMessage } from '../../components/ChatMessage/MessageCard'
import { FileUpload } from '../../components/FileUpload'
import { FolderSelector } from '../../components/FolderSelector'
import { LocalCodeTerminal } from '../../components/LocalCodeTerminal'
import { SessionList } from '../../components/SessionList/Panel'
import { useConversationalChat as useConversationalChatClean } from '../../hooks/useConversationalChatClean'
import { useLocalCodeStream } from '../../hooks/useLocalCodeStream'
import type { SessionSummary } from '../../services/api'
import { FileCode2, MessageSquare, Sparkles, Zap } from 'lucide-react'

interface Props {
  sessionId?: string
  sessions: SessionSummary[]
  currentProjectId?: string
  onSessionChange: (sessionId?: string) => void
  refreshSessions: (preferredSessionId?: string) => Promise<void>
}

export function ChatPage({ sessionId, sessions, currentProjectId, onSessionChange, refreshSessions }: Props) {
  const [activeMode, setActiveMode] = useState<'chat' | 'local-code'>('chat')
  const [folderPath, setFolderPath] = useState('E:/AIAgent')
  const [fileFilters, setFileFilters] = useState('*.java, *.xml, *.yml, *.yaml')
  const {
    draft, setDraft, messages, norms, loadingHistory,
    sending, uploading, sendSimpleMessage, sendDeepAnalysis, uploadNormFile,
  } = useConversationalChatClean({ sessionId, currentProjectId, onSessionChange, refreshSessions })
  const localCodeStream = useLocalCodeStream()

  const startLocalCodeAnalysis = async (autoWrite: boolean) => {
    if (!folderPath.trim()) {
      message.warning('请输入要分析的本地目录')
      return
    }
    try {
      await localCodeStream.start({
        folderPath: folderPath.trim(),
        fileFilters: fileFilters.split(',').map((item) => item.trim()).filter(Boolean),
        autoWrite,
      })
    } catch {
      message.error('本地代码分析启动失败')
    }
  }

  if (activeMode === 'local-code') {
    return (
      <div style={{ display: 'grid', gridTemplateColumns: '280px minmax(0, 1fr)', gap: 0, height: '100vh' }}>
        <aside style={{
          borderRight: '1px solid rgba(148,163,184,0.06)',
          background: '#0d1424',
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden',
        }}>
          <SessionList
            sessions={sessions}
            currentSessionId={sessionId}
            norms={norms}
            onSelectSession={onSessionChange}
            onCreateSession={() => onSessionChange(undefined)}
          />
        </aside>

        <section style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', background: '#060b14' }}>
          <div style={{
            padding: '22px 28px 18px',
            borderBottom: '1px solid rgba(148,163,184,0.06)',
            background: 'linear-gradient(180deg, rgba(34,197,94,0.03) 0%, transparent 100%)',
            flexShrink: 0,
          }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 6 }}>
              <div style={{
                width: 36, height: 36, borderRadius: 10,
                background: 'linear-gradient(135deg, rgba(34,197,94,0.18), rgba(6,182,212,0.12))',
                border: '1px solid rgba(34,197,94,0.25)',
                display: 'flex', alignItems: 'center', justifyContent: 'center',
              }}>
                <FileCode2 size={18} color="#22c55e" />
              </div>
              <div>
                <Typography.Title level={4} style={{ margin: 0, fontFamily: '"Syne", sans-serif', fontWeight: 700, fontSize: '1.1rem', color: '#e2e8f0' }}>
                  本地代码审查
                </Typography.Title>
                <Typography.Text style={{ fontSize: '11px', color: '#4a5568' }}>
                  选择白名单目录下的代码并发起流式分析
                </Typography.Text>
              </div>
            </div>
            <div style={{ display: 'flex', gap: 8, marginTop: 12 }}>
              <Button size="small" onClick={() => setActiveMode('chat')}>
                返回对话
              </Button>
              <Button
                type="primary"
                size="small"
                danger={localCodeStream.status === 'running'}
                onClick={() => localCodeStream.stop()}
              >
                停止
              </Button>
            </div>
          </div>

          <div style={{ flex: 1, overflowY: 'auto', padding: '24px 28px', display: 'grid', gap: 18 }}>
            <FolderSelector
              folderPath={folderPath}
              fileFilters={fileFilters}
              disabled={localCodeStream.status === 'running'}
              onFolderPathChange={setFolderPath}
              onFileFiltersChange={setFileFilters}
              onStart={() => void startLocalCodeAnalysis(false)}
              onAutoFix={() => void startLocalCodeAnalysis(true)}
            />
            <LocalCodeTerminal
              lines={localCodeStream.lines}
              status={localCodeStream.status}
              summary={localCodeStream.summary}
              modifiedFiles={localCodeStream.modifiedFiles}
            />
          </div>
        </section>
      </div>
    )
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: '280px minmax(0, 1fr)', gap: 0, height: '100vh' }}>

      {/* Sidebar */}
      <aside style={{
        borderRight: '1px solid rgba(148,163,184,0.06)',
        background: '#0d1424',
        display: 'flex',
        flexDirection: 'column',
        overflow: 'hidden',
      }}>
        <SessionList
          sessions={sessions}
          currentSessionId={sessionId}
          norms={norms}
          onSelectSession={onSessionChange}
          onCreateSession={() => onSessionChange(undefined)}
        />
      </aside>

      {/* Main */}
      <section style={{ display: 'flex', flexDirection: 'column', overflow: 'hidden', background: '#060b14' }}>

        {/* Header */}
        <div style={{
          padding: '22px 28px 18px',
          borderBottom: '1px solid rgba(148,163,184,0.06)',
          background: 'linear-gradient(180deg, rgba(6,182,212,0.03) 0%, transparent 100%)',
          flexShrink: 0,
        }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, marginBottom: 6 }}>
            <div style={{
              width: 36, height: 36, borderRadius: 10,
              background: 'linear-gradient(135deg, rgba(124,58,237,0.2), rgba(6,182,212,0.15))',
              border: '1px solid rgba(124,58,237,0.2)',
              display: 'flex', alignItems: 'center', justifyContent: 'center',
            }}>
              <MessageSquare size={18} color="#8b5cf6" />
            </div>
            <div>
              <Typography.Title level={4} style={{ margin: 0, fontFamily: '"Syne", sans-serif', fontWeight: 700, fontSize: '1.1rem', color: '#e2e8f0' }}>
                对话工作台
              </Typography.Title>
              <Typography.Text style={{ fontSize: '11px', color: '#4a5568' }}>
                {sessionId ? `会话 ${sessionId}` : '新建会话'}
              </Typography.Text>
            </div>
          </div>
          <div style={{ display: 'flex', gap: 8, marginTop: 12, flexWrap: 'wrap' }}>
            <Button
              type={activeMode === 'chat' ? 'primary' : 'default'}
              size="small"
              onClick={() => setActiveMode('chat')}
            >
              对话工作台
            </Button>
            <Button
              type="default"
              size="small"
              onClick={() => setActiveMode('local-code')}
            >
              本地代码审查
            </Button>
          </div>
          <div style={{ display: 'flex', gap: 8, flexWrap: 'wrap', marginTop: 10 }}>
            {[
              { label: '快速问答', icon: MessageSquare, color: '#06b6d4' },
              { label: '深度分析', icon: Sparkles, color: '#7c3aed' },
              { label: 'PDF 规范', icon: Zap, color: '#ec4899' },
            ].map((tag) => (
              <div key={tag.label} style={{
                display: 'flex', alignItems: 'center', gap: 5,
                padding: '3px 10px',
                background: `${tag.color}0a`,
                border: `1px solid ${tag.color}22`,
                borderRadius: 99,
                fontSize: '11px',
                color: tag.color,
                fontWeight: 500,
              }}>
                <tag.icon size={11} />
                {tag.label}
              </div>
            ))}
          </div>
        </div>

        {/* Messages */}
        <div style={{
          flex: 1,
          overflowY: 'auto',
          padding: '24px 28px',
          scrollBehavior: 'smooth',
        }}>
          {messages.length ? (
            messages.map((item) => <ChatMessage key={item.id} message={item} />)
          ) : (
            <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
              <Empty
                description={
                  <Space direction="vertical" size={4} style={{ textAlign: 'center' }}>
                    <Typography.Text style={{ color: '#4a5568', fontSize: '14px' }}>
                      {loadingHistory ? '正在加载会话消息...' : '发送第一条消息，开启智能分析'}
                    </Typography.Text>
                    <Typography.Text style={{ color: '#2d3748', fontSize: '12px' }}>
                      {loadingHistory ? 'Loading...' : 'Start your first analysis'}
                    </Typography.Text>
                  </Space>
                }
              >
                <div style={{ marginTop: 16 }}>
                  <svg width="80" height="80" viewBox="0 0 80 80" fill="none" style={{ margin: '0 auto', display: 'block', opacity: 0.15 }}>
                    <circle cx="40" cy="40" r="36" stroke="url(#msg)" strokeWidth="1.5" strokeDasharray="5 5"/>
                    <path d="M28 35c0-6.627 5.373-12 12-12s12 5.373 12 12c0 5.523-3.716 10.18-8.727 11.616L40 50l-3.273-3.384C31.716 45.18 28 40.523 28 35z" stroke="url(#msg)" strokeWidth="1.5" fill="none"/>
                    <circle cx="35" cy="35" r="2" fill="#06b6d4"/>
                    <circle cx="40" cy="35" r="2" fill="#7c3aed"/>
                    <circle cx="45" cy="35" r="2" fill="#ec4899"/>
                    <defs><linearGradient id="msg" x1="0" y1="0" x2="80" y2="80"><stop stopColor="#06b6d4"/><stop offset="1" stopColor="#7c3aed"/></linearGradient></defs>
                  </svg>
                </div>
              </Empty>
            </div>
          )}
        </div>

        {/* Composer */}
        <div style={{
          padding: '18px 28px 22px',
          borderTop: '1px solid rgba(148,163,184,0.06)',
          background: '#0d1424',
          flexShrink: 0,
        }}>
          {/* Mode buttons */}
          <div style={{ display: 'flex', gap: 8, marginBottom: 10 }}>
            <Button
              type="primary"
              size="small"
              loading={sending}
              onClick={() => void sendSimpleMessage()}
              style={{ fontSize: '12.5px' }}
            >
              <MessageSquare size={13} /> 快速问答
            </Button>
            <Button
              size="small"
              loading={sending}
              onClick={() => void sendDeepAnalysis()}
              style={{ background: 'rgba(124,58,237,0.12)', border: '1px solid rgba(124,58,237,0.25)', color: '#c4b5fd', fontSize: '12.5px' }}
            >
              <Sparkles size={13} /> 深度分析
            </Button>
            <div style={{ flex: 1 }} />
            <FileUpload uploading={uploading} onUpload={uploadNormFile} />
          </div>

          {/* Textarea */}
          <Input.TextArea
            value={draft}
            autoSize={{ minRows: 2, maxRows: 8 }}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="输入你的问题、代码片段或希望重点分析的方向..."
            style={{
              fontSize: '14px',
              lineHeight: 1.6,
              fontFamily: '"Noto Sans SC", sans-serif',
              background: '#080d1a',
              border: '1px solid rgba(148,163,184,0.1)',
              borderRadius: 10,
              padding: '10px 14px',
              resize: 'none',
              transition: 'all 0.15s ease',
            }}
            onFocus={(e) => {
              ;(e.currentTarget as HTMLTextAreaElement).style.borderColor = 'rgba(6,182,212,0.4)'
              ;(e.currentTarget as HTMLTextAreaElement).style.boxShadow = '0 0 0 3px rgba(6,182,212,0.08)'
            }}
            onBlur={(e) => {
              ;(e.currentTarget as HTMLTextAreaElement).style.borderColor = 'rgba(148,163,184,0.1)'
              ;(e.currentTarget as HTMLTextAreaElement).style.boxShadow = 'none'
            }}
          />
        </div>
      </section>
    </div>
  )
}
