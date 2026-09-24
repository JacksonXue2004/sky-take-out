import React from 'react'
import { useNavigate } from 'react-router-dom'
import { Button } from 'antd'
import img404 from '@/assets/404.png'

// Mirrors src/views/404.vue
const NotFound: React.FC = () => {
  const navigate = useNavigate()
  return (
    <div style={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'center', justifyContent: 'center', gap: 20 }}>
      <img src={img404} alt="404" style={{ maxWidth: 480, width: '60%' }} />
      <Button type="primary" onClick={() => navigate('/')}>Back to Dashboard</Button>
    </div>
  )
}

export default NotFound
