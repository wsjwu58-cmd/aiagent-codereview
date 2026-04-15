import { useState } from 'react'
import { message } from 'antd'
import { uploadNorm } from '../services/api'

export function usePdfUpload(onUploaded?: () => Promise<void> | void) {
  const [uploading, setUploading] = useState(false)

  const upload = async (file: File, projectId: string, description?: string) => {
    setUploading(true)
    try {
      await uploadNorm(file, projectId, description)
      message.success('规范文档上传成功')
      await onUploaded?.()
    } catch (error) {
      const reason = error instanceof Error ? error.message : '规范文档上传失败'
      message.error(reason)
      throw error
    } finally {
      setUploading(false)
    }
  }

  return {
    uploading,
    upload,
  }
}
