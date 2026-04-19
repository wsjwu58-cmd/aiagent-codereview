import { useState } from 'react'
import { Typography, Card, Upload, Button, message, Space, Divider } from 'antd'
import { Upload as UploadIcon, FileText, CheckCircle, AlertCircle } from 'lucide-react'

export function PdfUploadPage() {
  const [uploading, setUploading] = useState(false)
  const [uploadedFiles, setUploadedFiles] = useState<{ name: string; status: 'success' | 'error' }[]>([])

  const handleUpload = async (file: File) => {
    if (!file.name.toLowerCase().endsWith('.pdf')) {
      message.error('只支持 PDF 文件')
      return
    }

    setUploading(true)
    try {
      const formData = new FormData()
      formData.append('file', file)

      formData.append('projectId', 'default'); // 默认项目ID

      const response = await fetch('/api/chat/upload-norm', {
        method: 'POST',
        headers: {
          'Authorization': `Bearer ${localStorage.getItem('token')}`,
        },
        body: formData,
      })

      if (response.ok) {
        message.success(`${file.name} 上传成功`)
        setUploadedFiles(prev => [...prev, { name: file.name, status: 'success' }])
      } else {
        message.error(`${file.name} 上传失败`)
        setUploadedFiles(prev => [...prev, { name: file.name, status: 'error' }])
      }
    } catch {
      message.error(`${file.name} 上传失败`)
      setUploadedFiles(prev => [...prev, { name: file.name, status: 'error' }])
    } finally {
      setUploading(false)
    }
  }

  const uploadProps = {
    accept: '.pdf',
    showUploadList: false,
    beforeUpload: async (file: File) => {
      await handleUpload(file)
      return false
    },
  }

  return (
    <div style={{ maxWidth: '800px', margin: '0 auto' }}>
      <Typography.Title level={3} style={{ marginBottom: '24px', color: '#f1f5f9' }}>
        PDF 文件上传
      </Typography.Title>

      <Card
        style={{
          background: '#1e293b',
          border: '2px dashed #334155',
          borderRadius: '16px',
          textAlign: 'center',
          padding: '48px 24px',
        }}
        styles={{ body: { padding: '48px 24px' } }}
      >
        <Upload.Dragger {...uploadProps}>
          <Space direction="vertical" size="large" style={{ width: '100%' }}>
            <div
              style={{
                width: '64px',
                height: '64px',
                borderRadius: '16px',
                background: 'linear-gradient(135deg, #3b82f6 0%, #8b5cf6 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                margin: '0 auto',
              }}
            >
              <UploadIcon style={{ width: '32px', height: '32px', color: 'white' }} />
            </div>
            <div>
              <Typography.Title level={4} style={{ margin: 0, color: '#f1f5f9' }}>
                点击或拖拽 PDF 文件到此处上传
              </Typography.Title>
              <Typography.Text style={{ color: '#64748b' }}>
                支持 .pdf 格式文件
              </Typography.Text>
            </div>
            <Button type="primary" loading={uploading} size="large" icon={<UploadIcon style={{ width: '18px', height: '18px' }} />}>
              选择文件
            </Button>
          </Space>
        </Upload.Dragger>
      </Card>

      {uploadedFiles.length > 0 && (
        <Card style={{ marginTop: '24px', background: '#1e293b', border: '1px solid #334155', borderRadius: '16px' }}>
          <Typography.Title level={5} style={{ marginBottom: '16px', color: '#f1f5f9' }}>
            上传记录
          </Typography.Title>
          <Divider style={{ borderColor: '#334155', margin: '12px 0' }} />
          {uploadedFiles.map((file, index) => (
            <div key={index} style={{ display: 'flex', alignItems: 'center', gap: '12px', padding: '8px 0' }}>
              <FileText style={{ width: '20px', height: '20px', color: '#60a5fa' }} />
              <Typography.Text style={{ flex: 1, color: '#f1f5f9' }}>{file.name}</Typography.Text>
              {file.status === 'success' ? (
                <CheckCircle style={{ width: '18px', height: '18px', color: '#22c55e' }} />
              ) : (
                <AlertCircle style={{ width: '18px', height: '18px', color: '#ef4444' }} />
              )}
            </div>
          ))}
        </Card>
      )}
    </div>
  )
}