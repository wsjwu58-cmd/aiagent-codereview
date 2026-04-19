import { useEffect, useMemo, useRef, useState } from 'react'
import { openSse } from '../services/api'

export interface StreamState {
  connected: boolean
  progress: { current: number; nodeName: string } | null
  outputs: Record<string, string>
  events: string[]
  done: boolean
  error?: string
}

export function useAgentStream(sessionId?: string) {
  const [state, setState] = useState<StreamState>({
    connected: false,
    progress: null,
    outputs: {},
    events: [],
    done: false,
  })
  const sourceRef = useRef<EventSource | null>(null)

  useEffect(() => {
    if (!sessionId) {
      setState({
        connected: false,
        progress: null,
        outputs: {},
        events: [],
        done: false,
      })
      return
    }

    setState({
      connected: false,
      progress: null,
      outputs: {},
      events: [],
      done: false,
    })

    const source = openSse(sessionId)
    sourceRef.current = source

    const appendEvent = (line: string) => {
      setState((prev) => ({ ...prev, events: [...prev.events.slice(-200), line] }))
    }

    source.addEventListener('connected', () => {
      setState((prev) => ({ ...prev, connected: true }))
      appendEvent('SSE 连接成功')
    })

    source.addEventListener('chain_node', (event) => {
      const data = JSON.parse((event as MessageEvent).data)
      setState((prev) => ({ ...prev, progress: data }))
      appendEvent(`执行节点：${data.nodeName}`)
    })

    source.addEventListener('agent_stream', (event) => {
      const data = JSON.parse((event as MessageEvent).data)
      setState((prev) => ({
        ...prev,
        outputs: {
          ...prev.outputs,
          [data.agentId]: (prev.outputs[data.agentId] ?? '') + data.content,
        },
      }))
    })

    source.addEventListener('agent_start', (event) => {
      const data = JSON.parse((event as MessageEvent).data)
      appendEvent(`Agent 开始：${data.name}`)
    })

    source.addEventListener('agent_complete', (event) => {
      const data = JSON.parse((event as MessageEvent).data)
      setState((prev) => ({
        ...prev,
        outputs: {
          ...prev.outputs,
          [data.agentId]: String(data.content ?? prev.outputs[data.agentId] ?? ''),
        },
      }))
      appendEvent(`Agent 完成：${data.name ?? data.agentId}`)
    })

    source.addEventListener('heartbeat', () => {
      appendEvent('收到心跳包')
    })

    source.addEventListener('cancelled', () => {
      appendEvent('任务已取消')
      setState((prev) => ({ ...prev, done: true }))
    })

    source.addEventListener('error', (event) => {
      const data = JSON.parse((event as MessageEvent).data)
      appendEvent(`错误：${data.message}`)
      setState((prev) => ({ ...prev, error: data.message }))
    })

    source.addEventListener('done', () => {
      appendEvent('全部完成')
      setState((prev) => ({ ...prev, done: true }))
      source.close()
    })

    return () => {
      source.close()
    }
  }, [sessionId])

  const mergedOutput = useMemo(() => Object.values(state.outputs).join('\n\n'), [state.outputs])

  return {
    ...state,
    mergedOutput,
    close: () => sourceRef.current?.close(),
  }
}
