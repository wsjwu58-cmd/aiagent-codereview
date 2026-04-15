import type { AxiosError } from 'axios'
import { useEffect, useMemo, useState } from 'react'
import { Button, ConfigProvider, Layout, Menu, Space, Typography, theme } from 'antd'
import { DashboardPage } from './pages/Dashboard'
import { HistoryPage } from './pages/History'
import { KnowledgePage } from './pages/Knowledge'
import { LoginPage } from './pages/Login'
import { ReviewPage } from './pages/Review'
import { ChatPage } from './pages/Chat/WorkspacePage'
import { listSessions, type SessionSummary } from './services/api'

const { Header, Content } = Layout

type TabKey = 'dashboard' | 'review' | 'chat' | 'history' | 'knowledge'

const SESSION_STORAGE_KEY = 'code-review.selectedSessionId'
const REVIEW_STORAGE_KEY = 'code-review.selectedReviewId'
const SESSION_LIST_STORAGE_KEY = 'code-review.sessionList'

interface ApiErrorBody {
  code?: number
  message?: string
}

export function App() {
  const [token, setToken] = useState<string | null>(localStorage.getItem('token'))
  const [username, setUsername] = useState<string>(localStorage.getItem('username') || '')
  const [activeTab, setActiveTab] = useState<TabKey>('dashboard')
  const [sessionId, setSessionId] = useState<string | undefined>(localStorage.getItem(SESSION_STORAGE_KEY) || undefined)
  const [reviewId, setReviewId] = useState<string | undefined>(localStorage.getItem(REVIEW_STORAGE_KEY) || undefined)
  const [sessions, setSessions] = useState<SessionSummary[]>([])
  const safeSessions = Array.isArray(sessions) ? sessions : []

  const {
    token: { colorBgContainer, colorText },
  } = theme.useToken()

  const currentSession = useMemo(
    () => safeSessions.find((item) => item.sessionId === sessionId),
    [safeSessions, sessionId],
  )

  const menuItems = useMemo(
    () => [
      { key: 'dashboard', label: '工作台' },
      { key: 'review', label: '代码审查' },
      { key: 'chat', label: '对话工作台' },
      { key: 'history', label: '历史检索' },
      { key: 'knowledge', label: '知识库' },
    ],
    [],
  )

  useEffect(() => {
    if (sessionId) {
      localStorage.setItem(SESSION_STORAGE_KEY, sessionId)
    } else {
      localStorage.removeItem(SESSION_STORAGE_KEY)
    }
  }, [sessionId])

  useEffect(() => {
    if (reviewId) {
      localStorage.setItem(REVIEW_STORAGE_KEY, reviewId)
    } else {
      localStorage.removeItem(REVIEW_STORAGE_KEY)
    }
  }, [reviewId])

  useEffect(() => {
    if (!token) {
      setSessions([])
      return
    }
    const cachedSessions = localStorage.getItem(SESSION_LIST_STORAGE_KEY)
    if (cachedSessions) {
      try {
        setSessions(normalizeSessions(JSON.parse(cachedSessions)))
      } catch {
        localStorage.removeItem(SESSION_LIST_STORAGE_KEY)
      }
    }
    void refreshSessions()
  }, [token])

  const selectSession = (nextSessionId?: string, nextReviewId?: string) => {
    setSessionId(nextSessionId)
    if (nextSessionId) {
      const matched = safeSessions.find((item) => item.sessionId === nextSessionId)
      setReviewId(nextReviewId ?? (matched?.latestReviewId || undefined))
    } else {
      setReviewId(nextReviewId)
    }
  }

  const normalizeSessions = (value: unknown): SessionSummary[] => {
    return Array.isArray(value) ? (value as SessionSummary[]) : []
  }

  const resolveErrorMessage = (error: unknown) => {
    const axiosError = error as AxiosError<ApiErrorBody>
    return axiosError?.response?.data?.message || '加载会话列表失败'
  }

  const refreshSessions = async (preferredSessionId?: string, preferredReviewId?: string) => {
    try {
      const data = normalizeSessions(await listSessions())
      setSessions(data)
      localStorage.setItem(SESSION_LIST_STORAGE_KEY, JSON.stringify(data))

      const desiredSessionId = preferredSessionId ?? sessionId ?? localStorage.getItem(SESSION_STORAGE_KEY) ?? data[0]?.sessionId
      const matched = data.find((item) => item.sessionId === desiredSessionId)

      if (matched) {
        setSessionId(matched.sessionId)
        setReviewId(preferredReviewId ?? (matched.latestReviewId || undefined))
        return
      }

      if (data.length > 0) {
        setSessionId(data[0].sessionId)
        setReviewId(data[0].latestReviewId || undefined)
        return
      }

      setSessionId(undefined)
      setReviewId(undefined)
    } catch (error) {
      const cachedSessions = localStorage.getItem(SESSION_LIST_STORAGE_KEY)
      if (cachedSessions) {
        try {
          setSessions(normalizeSessions(JSON.parse(cachedSessions)))
        } catch {
          localStorage.removeItem(SESSION_LIST_STORAGE_KEY)
        }
      }
      // message.error(resolveErrorMessage(error))
    }
  }

  if (!token) {
    return (
      <ConfigProvider
        theme={{
          algorithm: theme.darkAlgorithm,
          token: {
            colorPrimary: '#3b82f6',
          },
        }}
      >
        <div className="page-shell">
          <LoginPage
            onAuthed={(newToken, newUsername) => {
              localStorage.setItem('token', newToken)
              localStorage.setItem('username', newUsername)
              setToken(newToken)
              setUsername(newUsername)
            }}
          />
        </div>
      </ConfigProvider>
    )
  }

  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#3b82f6',
          borderRadius: 8,
        },
        components: {
          Layout: {
            headerBg: '#1e293b',
            bodyBg: '#0f172a',
          },
          Menu: {
            darkItemBg: 'transparent',
            darkItemSelectedBg: 'rgba(59, 130, 246, 0.2)',
            darkItemHoverBg: 'rgba(255, 255, 255, 0.05)',
          },
        },
      }}
    >
      <Layout className="page-shell">
        <Header
          style={{
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'space-between',
            padding: '0 24px',
            borderRadius: '16px',
            marginBottom: '16px',
          }}
        >
          <div style={{ display: 'flex', alignItems: 'center', gap: '12px' }}>
            <div
              style={{
                width: '36px',
                height: '36px',
                borderRadius: '10px',
                background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '18px',
                fontWeight: 'bold',
              }}
            >
              ⚡
            </div>
            <Typography.Title
              level={4}
              style={{
                margin: 0,
                background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
                WebkitBackgroundClip: 'text',
                WebkitTextFillColor: 'transparent',
                fontWeight: 700,
              }}
            >
              智能代码审查助手
            </Typography.Title>
          </div>
          <Space>
            <Typography.Text style={{ color: '#94a3b8' }}>{username}</Typography.Text>
            <Button
              size="small"
              onClick={() => {
                localStorage.removeItem('token')
                localStorage.removeItem('username')
                localStorage.removeItem(SESSION_STORAGE_KEY)
                localStorage.removeItem(REVIEW_STORAGE_KEY)
                localStorage.removeItem(SESSION_LIST_STORAGE_KEY)
                setSessions([])
                setSessionId(undefined)
                setReviewId(undefined)
                setToken(null)
              }}
            >
              退出
            </Button>
          </Space>
        </Header>

        <Content style={{ padding: '0 8px' }}>
          <Menu
            mode="horizontal"
            selectedKeys={[activeTab]}
            items={menuItems}
            onClick={(item) => setActiveTab(item.key as TabKey)}
            style={{
              borderRadius: '12px',
              marginBottom: '16px',
              padding: '4px',
              background: '#1e293b',
              border: '1px solid #334155',
            }}
          />

          {activeTab === 'dashboard' && (
            <DashboardPage
              username={username}
              currentSessionId={sessionId}
              currentReviewId={reviewId}
              sessions={safeSessions}
              onSelectSession={(nextSessionId) => selectSession(nextSessionId)}
            />
          )}
          {activeTab === 'review' && (
            <ReviewPage
              currentSessionId={sessionId}
              currentReviewId={reviewId}
              onCreated={(newSessionId, newReviewId) => {
                selectSession(newSessionId, newReviewId)
                void refreshSessions(newSessionId, newReviewId)
              }}
            />
          )}
          {activeTab === 'chat' && (
            <ChatPage
              sessionId={sessionId}
              sessions={safeSessions}
              onSessionChange={(nextSessionId) => selectSession(nextSessionId)}
              refreshSessions={refreshSessions}
              currentProjectId={currentSession?.projectId}
            />
          )}
          {activeTab === 'history' && (
            <HistoryPage
              sessionId={sessionId}
              sessions={safeSessions}
              onSelectSession={(nextSessionId) => {
                selectSession(nextSessionId)
                setActiveTab('chat')
              }}
            />
          )}
          {activeTab === 'knowledge' && <KnowledgePage sessionId={sessionId} sessions={safeSessions} />}
        </Content>
      </Layout>
    </ConfigProvider>
  )
}
