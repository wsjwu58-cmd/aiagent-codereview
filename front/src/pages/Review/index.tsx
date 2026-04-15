import { useEffect, useMemo, useState } from 'react'
import { Button, Card, Col, Collapse, Form, Input, Radio, Row, Space, Tag, Typography, message } from 'antd'
import { useAgentStream } from '../../hooks/useAgentStream'
import { cancelReview, getReview, submitReview, type ReviewTaskDetail, type ReviewType } from '../../services/api'
import { CodeViewer } from '../../components/CodeViewer'
import { DiffViewer } from '../../components/DiffViewer'
import { ReviewReport } from '../../components/ReviewReport'
import { AgentMessage } from '../../components/AgentMessage'

interface Props {
  onCreated: (sessionId: string, reviewId: string) => void
  currentReviewId?: string
  currentSessionId?: string
}

export function ReviewPage({ onCreated, currentReviewId, currentSessionId }: Props) {
  const [reviewId, setReviewId] = useState(currentReviewId)
  const [sessionId, setSessionId] = useState(currentSessionId)
  const [detail, setDetail] = useState<ReviewTaskDetail>()
  const [submitting, setSubmitting] = useState(false)
  const [activeKeys, setActiveKeys] = useState<string[]>([])

  const stream = useAgentStream(sessionId)
  const agentOutputs = useMemo(() => Object.entries(stream.outputs), [stream.outputs])

  const hasResults = detail?.report || agentOutputs.length > 0 || detail?.refactoredCode || detail?.codeContent

  const loadReviewDetail = async (targetReviewId: string, options?: { silent?: boolean; suppressError?: boolean }) => {
    const silent = options?.silent ?? false
    const suppressError = options?.suppressError ?? false
    try {
      const data = await getReview(targetReviewId)
      setDetail(data)
      if (!silent) {
        message.success('已刷新审查报告')
      }
      return data
    } catch (error) {
      if (!suppressError) {
        message.error('获取审查报告失败')
      }
      throw error
    }
  }

  useEffect(() => {
    setReviewId(currentReviewId)
  }, [currentReviewId])

  useEffect(() => {
    setSessionId(currentSessionId)
  }, [currentSessionId])

  useEffect(() => {
    if (!currentReviewId) {
      return
    }
    void loadReviewDetail(currentReviewId, { silent: true, suppressError: true })
  }, [currentReviewId])

  useEffect(() => {
    if (!reviewId || stream.done) {
      return
    }
    const timer = window.setInterval(() => {
      void loadReviewDetail(reviewId, { silent: true, suppressError: true })
    }, 2500)
    return () => window.clearInterval(timer)
  }, [reviewId, stream.done])

  useEffect(() => {
    if (!stream.done || !reviewId) {
      return
    }
    void loadReviewDetail(reviewId, { silent: true, suppressError: true })
  }, [stream.done, reviewId])

  const onSubmit = async (values: { type: ReviewType; codeContent?: string; repoUrl?: string; branch?: string; projectId?: string; language?: string }) => {
    setSubmitting(true)
    setDetail(undefined)
    setActiveKeys(['progress', 'agents', 'results', 'report'])
    try {
      const summary = await submitReview({
        type: values.type,
        codeContent: values.codeContent,
        repoUrl: values.repoUrl,
        branch: values.branch,
        projectId: values.projectId,
        sessionId,
        language: values.language || 'java',
      })
      setSessionId(summary.sessionId)
      setReviewId(summary.reviewId)
      onCreated(summary.sessionId, summary.reviewId)
      await loadReviewDetail(summary.reviewId, { silent: true, suppressError: true })
      message.success('已提交审查任务，正在生成结果')
    } catch {
      message.error('提交失败，请检查输入内容')
    } finally {
      setSubmitting(false)
    }
  }

  const onRefresh = async () => {
    if (!reviewId) {
      return
    }
    await loadReviewDetail(reviewId)
  }

  const onCancel = async () => {
    if (!sessionId) {
      return
    }
    try {
      await cancelReview(sessionId)
      message.success('已发送取消指令')
    } catch {
      message.error('取消失败')
    }
  }

  const collapseItems = [
    {
      key: 'progress',
      label: (
        <Space>
          <span>执行进度</span>
          <Tag color={stream.done ? 'green' : stream.connected ? 'blue' : 'default'}>
            {stream.done ? '已完成' : stream.connected ? '执行中' : '等待中'}
          </Tag>
          {stream.progress && <Tag color="gold">{stream.progress.nodeName}</Tag>}
        </Space>
      ),
      children: (
        <Row gutter={16}>
          <Col xs={24} lg={8}>
            <AgentMessage name="实时事件" content={stream.events.join('\n')} />
          </Col>
          <Col xs={24} lg={16}>
            <Card size="small" title="任务状态">
              <Space direction="vertical" size={8} style={{ width: '100%' }}>
                <Typography.Text>当前会话: {sessionId || '未创建'}</Typography.Text>
                <Typography.Text>当前任务: {reviewId || '未创建'}</Typography.Text>
                <Typography.Text>当前节点: {stream.progress?.nodeName || '等待执行'}</Typography.Text>
                <Typography.Text type="secondary">完成状态: {stream.done ? '已完成' : '执行中'}</Typography.Text>
                {stream.error ? <Typography.Text type="danger">错误信息: {stream.error}</Typography.Text> : null}
              </Space>
            </Card>
          </Col>
        </Row>
      ),
    },
    {
      key: 'agents',
      label: (
        <Space>
          <span>Agent 输出</span>
          {agentOutputs.length > 0 && <Tag color="purple">{agentOutputs.length} 个Agent</Tag>}
        </Space>
      ),
      children: agentOutputs.length > 0 ? (
        <Row gutter={16}>
          {agentOutputs.map(([agentId, content]) => (
            <Col key={agentId} xs={24} lg={12}>
              <AgentMessage name={agentId} content={content} />
            </Col>
          ))}
        </Row>
      ) : (
        <Typography.Text type="secondary">暂无 Agent 输出</Typography.Text>
      ),
    },
    {
      key: 'results',
      label: <span>代码结果</span>,
      children: (
        <Row gutter={16}>
          <Col xs={24} lg={12}>
            <CodeViewer code={detail?.refactoredCode || ''} title="重构结果" />
          </Col>
          <Col xs={24} lg={12}>
            <DiffViewer diff={detail?.codeContent} />
          </Col>
        </Row>
      ),
    },
    {
      key: 'report',
      label: (
        <Space>
          <span>审查报告</span>
          {detail?.report && (
            <Tag color={detail.report.criticalCount > 0 ? 'red' : detail.report.highCount > 0 ? 'orange' : 'green'}>
              {detail.report.totalIssues} 个问题
            </Tag>
          )}
        </Space>
      ),
      children: <ReviewReport detail={detail} />,
    },
  ]

  return (
    <Space direction="vertical" size={16} style={{ width: '100%' }}>
      <Card className="page-card" title="提交代码审查">
        <Form layout="vertical" onFinish={onSubmit} initialValues={{ type: 'PASTE_CODE', language: 'java' }}>
          <Form.Item name="type" label="审查类型">
            <Radio.Group>
              <Radio.Button value="PASTE_CODE">代码粘贴审查</Radio.Button>
              <Radio.Button value="GIT_DIFF">仓库拉取审查</Radio.Button>
            </Radio.Group>
          </Form.Item>
          <Form.Item name="projectId" label="项目 ID">
            <Input placeholder="输入项目标识，如 project-001" />
          </Form.Item>
          <Form.Item name="language" label="语言">
            <Input placeholder="java / python / ts / go" />
          </Form.Item>
          <Form.Item name="repoUrl" label="仓库地址（Git 审查时使用）">
            <Input placeholder="输入 Git 仓库地址" />
          </Form.Item>
          <Form.Item name="branch" label="分支（Git 审查时使用）">
            <Input placeholder="输入分支名，如 main 或 master" />
          </Form.Item>
          <Form.Item name="codeContent" label="代码内容（粘贴审查时使用）">
            <Input.TextArea rows={6} placeholder="粘贴需要审查的代码" />
          </Form.Item>
          <Space wrap>
            <Button type="primary" htmlType="submit" loading={submitting}>
              提交审查
            </Button>
            <Button onClick={onRefresh} disabled={!reviewId}>
              刷新结果
            </Button>
            <Button danger onClick={onCancel} disabled={!sessionId}>
              取消任务
            </Button>
            {sessionId ? <Tag color="blue">会话: {sessionId}</Tag> : null}
            {reviewId ? <Tag color="purple">任务: {reviewId}</Tag> : null}
          </Space>
        </Form>
      </Card>

      {hasResults && (
        <Collapse
          activeKey={activeKeys}
          onChange={(keys) => setActiveKeys(keys as string[])}
          items={collapseItems}
          defaultActiveKey={['progress', 'agents', 'results', 'report']}
          collapsible="icon"
        />
      )}

      {!hasResults && (
        <Card>
          <Typography.Text type="secondary" style={{ textAlign: 'center', display: 'block', padding: '40px 0' }}>
            提交代码审查后，结果将在此展示
          </Typography.Text>
        </Card>
      )}
    </Space>
  )
}
