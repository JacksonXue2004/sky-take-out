import React from 'react'
import { ArrowLeftOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'

interface Props {
  title?: string
  goback?: boolean
  children?: React.ReactNode
}

// Mirrors src/components/HeadLable/index.vue
const HeadLable: React.FC<Props> = ({ title = 'Management', goback = false, children }) => {
  const navigate = useNavigate()
  return (
    <div className="HeadLable">
      {goback && (
        <span className="goBack" onClick={() => navigate(-1)}>
          <ArrowLeftOutlined /> Back
        </span>
      )}
      {children ? children : <span className="title">{title}</span>}
    </div>
  )
}

export default HeadLable
