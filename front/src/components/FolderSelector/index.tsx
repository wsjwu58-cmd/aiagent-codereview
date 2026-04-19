import { Button, Input, Space } from 'antd'

interface Props {
  folderPath: string
  fileFilters: string
  disabled?: boolean
  onFolderPathChange: (value: string) => void
  onFileFiltersChange: (value: string) => void
  onStart: () => void
  onAutoFix: () => void
}

export function FolderSelector({
  folderPath,
  fileFilters,
  disabled,
  onFolderPathChange,
  onFileFiltersChange,
  onStart,
  onAutoFix,
}: Props) {
  return (
    <Space direction="vertical" size={10} style={{ width: '100%' }}>
      <Input
        value={folderPath}
        onChange={(event) => onFolderPathChange(event.target.value)}
        placeholder="输入本地目录，例如 E:/AIAgent/code-review-api/src/main/java"
      />
      <Input
        value={fileFilters}
        onChange={(event) => onFileFiltersChange(event.target.value)}
        placeholder="文件过滤，例如 *.java, *.xml, *.yml"
      />
      <Space wrap>
        <Button type="primary" disabled={disabled} onClick={onStart}>
          开始分析
        </Button>
        <Button disabled={disabled} onClick={onAutoFix}>
          授权自动修复
        </Button>
      </Space>
    </Space>
  )
}
