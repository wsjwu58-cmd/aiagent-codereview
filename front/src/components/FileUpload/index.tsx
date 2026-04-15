import { Button, Upload } from 'antd'
import type { UploadProps } from 'antd'

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
      <Button loading={uploading}>上传 PDF</Button>
    </Upload>
  )
}
