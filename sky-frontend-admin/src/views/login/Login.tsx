import React, { useState } from 'react'
import { Button, Form, Input } from 'antd'
import { UserOutlined, LockOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useAuth } from '@/context/AuthContext'
import './Login.css'

// Mirrors src/views/login/index.vue
const Login: React.FC = () => {
  const [loading, setLoading] = useState(false)
  const navigate = useNavigate()
  const { login } = useAuth()

  const handleLogin = async (values: { username: string; password: string }) => {
    setLoading(true)
    try {
      const res = await login(values)
      if (res && String(res.code) === '1') {
        navigate('/')
      } else {
        setLoading(false)
      }
    } catch {
      setLoading(false)
    }
  }

  return (
    <div className="login">
      <div className="login-box">
        <div className="login-illustration">
          <div className="login-brand login-brand-large">
            <span className="login-brand-mark">SKY</span>
            <span className="login-brand-name">TAKE-OUT</span>
            <span className="login-brand-subtitle">MERCHANT ADMIN</span>
          </div>
        </div>
        <div className="login-form">
          <Form
            initialValues={{ username: 'admin', password: '123456' }}
            onFinish={handleLogin}
            layout="vertical"
          >
            <div className="login-form-title">
              <div className="login-brand login-brand-small">
                <span className="login-brand-mark">SKY</span>
                <span className="login-brand-name">TAKE-OUT</span>
              </div>
            </div>
            <Form.Item
              name="username"
              rules={[{ required: true, message: 'Enter your username' }]}
            >
              <Input
                size="large"
                prefix={<UserOutlined />}
                placeholder="Username"
                autoComplete="off"
              />
            </Form.Item>
            <Form.Item
              name="password"
              rules={[
                { required: true, message: 'Enter your password' },
                { min: 6, message: 'Password must contain at least 6 characters' },
              ]}
            >
              <Input.Password
                size="large"
                prefix={<LockOutlined />}
                placeholder="Password"
                onPressEnter={() => {}}
              />
            </Form.Item>
            <Form.Item>
              <Button
                className="login-btn"
                type="primary"
                htmlType="submit"
                block
                size="large"
                loading={loading}
              >
                {loading ? 'Signing in...' : 'Sign In'}
              </Button>
            </Form.Item>
          </Form>
        </div>
      </div>
    </div>
  )
}

export default Login
