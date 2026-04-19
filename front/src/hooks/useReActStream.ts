import { useRef, useState } from 'react'
import { streamReactChat, type ReactChatRequest } from '../services/api'

export type StreamStatus = 'idle' | 'thinking' | 'done' | 'error'

export interface ThinkingStepItem {
  step: number
  type: 'THINKING' | 'TOOL_CALL' | 'OBSERVATION'
  agentId: string
  agentName: string
  content?: string
  tool?: string
  input?: string
  output?: string
}

interface StreamHandlers {
  onConnected?: (payload: Record<string, unknown>) => void
  onStep?: (step: ThinkingStepItem) => void
  onDone?: (payload: Record<string, unknown>) => void
  onError?: (message: string) => void
}

export function useReActStream() {
  const controllerRef = useRef<AbortController | null>(null)
  const stepCounterRef = useRef(0)
  const [steps, setSteps] = useState<ThinkingStepItem[]>([])
  const [status, setStatus] = useState<StreamStatus>('idle')
  const [error, setError] = useState<string>()

  const start = async (payload: ReactChatRequest, handlers: StreamHandlers = {}) => {
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller

    setSteps([])
    stepCounterRef.current = 0
    setError(undefined)
    setStatus('thinking')

    try {
      await streamReactChat(
        payload,
        ({ event, data }) => {
          const resolveStepNumber = (rawStep?: unknown) => {
            const parsed = Number(rawStep)
            if (Number.isFinite(parsed) && parsed > stepCounterRef.current) {
              stepCounterRef.current = parsed
              return parsed
            }
            stepCounterRef.current += 1
            return stepCounterRef.current
          }

          const appendStep = (step: ThinkingStepItem) => {
            setSteps((prev) => [...prev, step])
            handlers.onStep?.(step)
          }

          if (event === 'connected') {
            handlers.onConnected?.(data)
            return
          }

          if (event === 'agent_start') {
            const step = {
              step: resolveStepNumber(data.step),
              type: 'THINKING' as const,
              agentId: String(data.agentId ?? ''),
              agentName: String(data.name ?? data.agentName ?? ''),
              content: `${String(data.name ?? data.agentName ?? 'Agent')} 开始分析`,
            }
            appendStep(step)
            return
          }

          if (event === 'thinking') {
            const step = {
              step: resolveStepNumber(data.step),
              type: String(data.type ?? 'THINKING') as ThinkingStepItem['type'],
              agentId: String(data.agentId ?? ''),
              agentName: String(data.agentName ?? ''),
              content: String(data.content ?? ''),
            }
            appendStep(step)
            return
          }

          if (event === 'tool_call') {
            const step = {
              step: resolveStepNumber(data.step),
              type: 'TOOL_CALL' as const,
              agentId: String(data.agentId ?? ''),
              agentName: String(data.agentName ?? ''),
              tool: String(data.tool ?? ''),
              input: String(data.input ?? ''),
              output: String(data.output ?? ''),
            }
            appendStep(step)
            return
          }

          if (event === 'agent_complete') {
            const summary = String(data.content ?? '')
            const step = {
              step: resolveStepNumber(data.step),
              type: 'OBSERVATION' as const,
              agentId: String(data.agentId ?? ''),
              agentName: String(data.name ?? data.agentName ?? ''),
              content: summary || `${String(data.name ?? data.agentId ?? 'Agent')} 已完成`,
            }
            appendStep(step)
            return
          }

          if (event === 'done') {
            setStatus('done')
            handlers.onDone?.(data)
            return
          }

          if (event === 'error') {
            const reason = String(data.message ?? '深度分析失败')
            setStatus('error')
            setError(reason)
            handlers.onError?.(reason)
          }
        },
        controller.signal,
      )
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        return
      }
      const reason = error instanceof Error ? error.message : '深度分析失败'
      setStatus('error')
      setError(reason)
      handlers.onError?.(reason)
      throw error
    }
  }

  const stop = () => {
    controllerRef.current?.abort()
    controllerRef.current = null
    setStatus('idle')
  }

  return {
    steps,
    status,
    error,
    start,
    stop,
  }
}
