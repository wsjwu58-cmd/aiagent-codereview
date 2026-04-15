import { useState } from 'react'
import { Button, Card, Form, Input, Space, Typography, message } from 'antd'
import { login, register } from '../../services/api'

interface Props {
  onAuthed: (token: string, username: string) => void
}

export function LoginPage({ onAuthed }: Props) {
  const [loading, setLoading] = useState(false)
  const [isRegister, setIsRegister] = useState(false)

  const onLogin = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      const data = await login(values.username, values.password)
      message.success('登录成功')
      onAuthed(data.token, data.user.username)
    } catch {
      message.error('登录失败，请检查账号密码')
    } finally {
      setLoading(false)
    }
  }

  const onRegister = async (values: { username: string; password: string; email?: string }) => {
    setLoading(true)
    try {
      const data = await register(values.username, values.password, values.email || '')
      message.success('注册成功，已自动登录')
      onAuthed(data.token, data.user.username)
    } catch {
      message.error('注册失败，用户名可能已存在')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'linear-gradient(135deg, #0f172a 0%, #1e293b 100%)',
        padding: '20px',
      }}
    >
      <Card
        className="page-card"
        style={{
          maxWidth: '420px',
          width: '100%',
          background: 'rgba(30, 41, 59, 0.95)',
          border: '1px solid #334155',
        }}
      >
        <div style={{ textAlign: 'center', marginBottom: '32px' }}>
          <div
            style={{
              width: '64px',
              height: '64px',
              borderRadius: '16px',
              background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: '28px',
              margin: '0 auto 16px',
              boxShadow: '0 8px 24px rgba(59, 130, 246, 0.3)',
            }}
          >
            ⚡
          </div>
          <Typography.Title
            level={3}
            style={{
              margin: '0 0 8px 0',
              background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
              WebkitBackgroundClip: 'text',
              WebkitTextFillColor: 'transparent',
            }}
          >
            智能代码审查助手
          </Typography.Title>
          <Typography.Text type="secondary">
            {isRegister ? '创建新账号开始使用' : '欢迎回来，请登录'}
          </Typography.Text>
        </div>

        <Form
          layout="vertical"
          onFinish={isRegister ? onRegister : onLogin}
          style={{ marginBottom: isRegister ? '0' : '0' }}
        >
          <Form.Item
            label={<span style={{ color: '#f1f5f9' }}>用户名</span>}
            name="username"
            rules={[{ required: true, message: '请输入用户名' }]}
          >
            <Input
              placeholder="请输入用户名"
              style={{
                background: '#0f172a',
                borderColor: '#334155',
                color: '#f1f5f9',
              }}
            />
          </Form.Item>
          <Form.Item
            label={<span style={{ color: '#f1f5f9' }}>密码</span>}
            name="password"
            rules={[{ required: true, message: '请输入密码' }]}
          >
            <Input.Password
              placeholder="请输入密码"
              style={{
                background: '#0f172a',
                borderColor: '#334155',
                color: '#f1f5f9',
              }}
            />
          </Form.Item>
          {isRegister && (
            <Form.Item label={<span style={{ color: '#f1f5f9' }}>邮箱（可选）</span>} name="email">
              <Input
                placeholder="可选"
                style={{
                  background: '#0f172a',
                  borderColor: '#334155',
                  color: '#f1f5f9',
                }}
              />
            </Form.Item>
          )}
          <Button
            type="primary"
            htmlType="submit"
            loading={loading}
            block
            style={{
              height: '44px',
              fontSize: '16px',
              fontWeight: 600,
              background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
              border: 'none',
              marginTop: '8px',
            }}
          >
            {isRegister ? '注册' : '登录'}
          </Button>
        </Form>

        <div style={{ textAlign: 'center', marginTop: '24px' }}>
          <Typography.Text type="secondary" style={{ marginRight: '8px' }}>
            {isRegister ? '已有账号？' : '没有账号？'}
          </Typography.Text>
          <Button type="link" onClick={() => setIsRegister(!isRegister)} style={{ padding: '0' }}>
            {isRegister ? '登录' : '注册'}
          </Button>
        </div>
      </Card>
    </div>
  )
}
