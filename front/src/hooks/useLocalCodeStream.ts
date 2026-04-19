import { useRef, useState } from 'react'
import {
  streamLocalCodeAnalysis,
  type LocalCodeAnalyzeRequest,
} from '../services/api'

type LocalCodeStatus = 'idle' | 'running' | 'done' | 'error'

export function useLocalCodeStream() {
  const controllerRef = useRef<AbortController | null>(null)
  const [status, setStatus] = useState<LocalCodeStatus>('idle')
  const [lines, setLines] = useState<string[]>([])
  const [modifiedFiles, setModifiedFiles] = useState<string[]>([])
  const [summary, setSummary] = useState('')
  const [error, setError] = useState('')

  const start = async (payload: LocalCodeAnalyzeRequest) => {
    controllerRef.current?.abort()
    const controller = new AbortController()
    controllerRef.current = controller

    setStatus('running')
    setLines([])
    setModifiedFiles([])
    setSummary('')
    setError('')

    try {
      await streamLocalCodeAnalysis(
        payload,
        ({ event, data }) => {
          if (event === 'connected') {
            setLines((prev) => [...prev, `> 已连接本地代码分析会话 ${String(data.sessionId ?? '')}`])
            return
          }
          if (event === 'thinking') {
            setLines((prev) => [...prev, `> ${String(data.content ?? '')}`])
            return
          }
          if (event === 'tool_call') {
            const tool = String(data.tool ?? '')
            const input = JSON.stringify(data.input ?? {})
            setLines((prev) => [...prev, `> [TOOL] ${tool} ${input}`])
            return
          }
          if (event === 'file_modified') {
            const path = String(data.path ?? '')
            setModifiedFiles((prev) => [...prev, path])
            setLines((prev) => [...prev, `> 已修改文件: ${path}`])
            return
          }
          if (event === 'done') {
            setStatus('done')
            setSummary(String(data.summary ?? ''))
            return
          }
          if (event === 'error') {
            const reason = String(data.message ?? '本地代码分析失败')
            setStatus('error')
            setError(reason)
            setLines((prev) => [...prev, `> ERROR: ${reason}`])
          }
        },
        controller.signal,
      )
    } catch (error) {
      if (error instanceof Error && error.name === 'AbortError') {
        return
      }
      const reason = error instanceof Error ? error.message : '本地代码分析失败'
      setStatus('error')
      setError(reason)
      setLines((prev) => [...prev, `> ERROR: ${reason}`])
      throw error
    }
  }

  const stop = () => {
    controllerRef.current?.abort()
    controllerRef.current = null
    setStatus('idle')
  }

  return {
    status,
    lines,
    modifiedFiles,
    summary,
    error,
    start,
    stop,
  }
}
