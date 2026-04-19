import { Button, Upload } from 'antd'
import type { UploadProps } from 'antd'
import { FileUp } from 'lucide-react'

interface Props {
  uploading?: boolean
  onUpload: (file: File) => Promise<void>
}

export function FileUpload({ uploading, onUpload }: Props) {
  const uploadProps: UploadProps = {
    accept: '.pdf',
    showUploadList: false,
    beforeUpload: async (file) => {
      await onUpload(file)
      return false
    },
  }

  return (
    <Upload {...uploadProps}>
      <Button
        loading={uploading}
        icon={<FileUp size={13} />}
        style={{
          fontSize: '12.5px',
          background: uploading ? undefined : 'rgba(148,163,184,0.05)',
          border: '1px solid rgba(148,163,184,0.1)',
          color: uploading ? undefined : '#94a3b8',
          height: 32,
          borderRadius: 8,
          display: 'flex', alignItems: 'center', gap: 5,
          fontFamily: '"Noto Sans SC", sans-serif',
        }}
      >
        {uploading ? '上传中...' : '上传 PDF'}
      </Button>
    </Upload>
  )
}
