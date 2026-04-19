import ReactMarkdown from 'react-markdown'
import remarkGfm from 'remark-gfm'
import './MarkdownRender.css'

interface Props {
  content: string
}

function normalizeMarkdown(content: string): string {
  const lines = content.replace(/\r\n?/g, '\n').split('\n')
  const normalized: string[] = []
  let insideFence = false

  for (let index = 0; index < lines.length; index += 1) {
    const currentLine = lines[index]
    const trimmedLine = currentLine.trim()

    if (/^(```|~~~)/.test(trimmedLine)) {
      insideFence = !insideFence
      normalized.push(currentLine)
      continue
    }

    if (insideFence) {
      normalized.push(currentLine)
      continue
    }

    if (/^\d+\.\s*$/.test(trimmedLine)) {
      let nextIndex = index + 1
      while (nextIndex < lines.length && !lines[nextIndex].trim()) {
        nextIndex += 1
      }

      if (
        nextIndex < lines.length &&
        lines[nextIndex].trim() &&
        !/^([-*+]|\d+\.)\s+/.test(lines[nextIndex].trim()) &&
        !/^(```|~~~|#{1,6}\s)/.test(lines[nextIndex].trim())
      ) {
        normalized.push(`${trimmedLine} ${lines[nextIndex].trim()}`)
        index = nextIndex
        continue
      }
    }

    normalized.push(currentLine)
  }

  return normalized.join('\n').replace(/\n{3,}/g, '\n\n').trim()
}

export function MarkdownRender({ content }: Props) {
  return (
    <div className="markdown-body">
      <ReactMarkdown remarkPlugins={[remarkGfm]}>{normalizeMarkdown(content || '')}</ReactMarkdown>
    </div>
  )
}
