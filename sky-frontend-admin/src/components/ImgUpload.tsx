import React from 'react'
import { Upload, message } from 'antd'
import type { UploadProps } from 'antd'
import { PlusOutlined } from '@ant-design/icons'
import { getToken } from '@/utils/cookies'
import './ImgUpload.css'

interface Props {
  value?: string
  onChange?: (url: string) => void
  propImageUrl?: string
  size?: number // MB, default 2
  type?: string // accept, default .jpg,.jpeg,.png
  onImageChange?: (url: string) => void
}

// Mirrors src/components/ImgUpload/index.vue
// POST /api/common/upload (multipart, field name `file`, header `token`); url = res.data.data
const ImgUpload: React.FC<Props> = ({
  value,
  onChange,
  propImageUrl = '',
  size = 2,
  type = '.jpg,.jpeg,.png',
  onImageChange,
}) => {
  const imageUrl = value ?? propImageUrl
  const emitChange = (url: string) => { onChange?.(url); onImageChange?.(url) }
  const beforeUpload = (file: File) => {
    const tooBig = file.size / 1024 / 1024 >= size
    if (tooBig) {
      message.error(`File size cannot exceed ${size}M!`)
      return Upload.LIST_IGNORE
    }
    return true
  }

  const handleChange: UploadProps['onChange'] = (info) => {
    if (info.file.status === 'done') {
      const resp = info.file.response
      if (resp && String(resp.code) === '1') {
        emitChange(resp.data)
      } else {
        message.error((resp && resp.msg) || 'Image upload failed')
      }
    } else if (info.file.status === 'error') {
      message.error('Image upload failed')
    }
  }

  return (
    <div className="img-upload">
      <Upload
        name="file"
        accept={type}
        action="/api/common/upload"
        headers={{ token: getToken() || '' }}
        showUploadList={false}
        beforeUpload={beforeUpload}
        onChange={handleChange}
      >
        {imageUrl ? (
          <div className="img-upload-preview">
            <img src={imageUrl} alt="dish" />
            <div className="img-upload-mask">
              <span>Replace Image</span>
            </div>
          </div>
        ) : (
          <div className="img-upload-btn">
            <PlusOutlined />
            <div className="img-upload-text">Upload Image</div>
          </div>
        )}
      </Upload>
      {imageUrl && (
        <div className="img-upload-actions">
          <a onClick={() => emitChange('')}>Remove Image</a>
        </div>
      )}
    </div>
  )
}

export default ImgUpload
