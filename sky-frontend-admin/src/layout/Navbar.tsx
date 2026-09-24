import React, { useEffect, useRef, useState } from 'react'
import { Button, Dropdown, Modal, Radio, message, notification } from 'antd'
import { MenuFoldOutlined, MenuUnfoldOutlined, DownOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { useApp } from '@/context/AppContext'
import { useAuth } from '@/context/AuthContext'
import { getStatus, setStatus as setStatusApi } from '@/api/users'
import Password from './Password'
import previewMp3 from '@/assets/preview.mp3'
import reminderMp3 from '@/assets/reminder.mp3'

// Mirrors src/layout/components/Navbar/index.vue (incl. WebSocket new-order/Order Reminder notifications)
const Navbar: React.FC = () => {
  const { sidebarOpened, toggleSidebar } = useApp()
  const { name, logout } = useAuth()
  const navigate = useNavigate()

  const [status, setStatusState] = useState<number>(1)
  const [radioStatus, setRadioStatus] = useState<number>(1)
  const [statusDialog, setStatusDialog] = useState(false)
  const [pwdVisible, setPwdVisible] = useState(false)

  const audioVo = useRef<HTMLAudioElement>(null)
  const audioVo2 = useRef<HTMLAudioElement>(null)
  const wsRef = useRef<WebSocket | null>(null)

  const fetchStatus = async () => {
    try {
      const { data } = await getStatus()
      setStatusState(data.data)
      setRadioStatus(data.data)
    } catch {
      /* ignore */
    }
  }

  // mount: get business status + open WebSocket (order push). unmount: close ws.
  useEffect(() => {
    fetchStatus()

    if (typeof WebSocket === 'undefined') {
      notification.warning({
        message: 'Notice',
        description: 'This browser cannot receive real-time order alerts. Please use Chrome.',
        duration: 0,
      })
      return
    }

    const clientId = Math.random().toString(36).substr(2)
    const proto = window.location.protocol === 'https:' ? 'wss:' : 'ws:'
    const socketUrl = `${proto}//${window.location.host}/ws/${clientId}`

    let ws: WebSocket | null = null
    try {
      ws = new WebSocket(socketUrl)
      wsRef.current = ws
      ws.onopen = () => console.log('WebSocket connected')
      ws.onmessage = (msg) => {
        try {
          if (audioVo.current) audioVo.current.currentTime = 0
          if (audioVo2.current) audioVo2.current.currentTime = 0
          const jsonMsg = JSON.parse(msg.data)
          if (jsonMsg.type === 1) {
            audioVo.current?.play().catch(() => {})
          } else if (jsonMsg.type === 2) {
            audioVo2.current?.play().catch(() => {})
          }
          notification.open({
            message: jsonMsg.type === 1 ? 'Pending Acceptance' : 'Order Reminder',
            description:
              jsonMsg.type === 1
                ? `You have a new order: ${jsonMsg.content}. Please accept it promptly.`
                : `${jsonMsg.content} View Order`,
            duration: 0,
            onClick: () => {
              navigate(`/order?orderId=${jsonMsg.orderId}`)
              setTimeout(() => window.location.reload(), 100)
            },
          })
        } catch (e) {
          console.log(e)
        }
      }
      ws.onerror = () => {
        notification.error({
          message: 'Error',
          description: 'Server error: real-time order alerts are unavailable.',
          duration: 0,
        })
      }
      ws.onclose = () => console.log('WebSocket closed')
    } catch {
      /* ignore */
    }

    return () => {
      try {
        ws && ws.close()
      } catch {
        /* ignore */
      }
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const handleSaveStatus = async () => {
    const { data } = await setStatusApi(radioStatus)
    if (data.code === 1) {
      setStatusDialog(false)
      fetchStatus()
    }
  }

  const handleLogout = async () => {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <div className="navbar">
      <div className="statusBox">
        <span className="hamburger-container" onClick={toggleSidebar}>
          {sidebarOpened ? <MenuFoldOutlined /> : <MenuUnfoldOutlined />}
        </span>
        {status === 1 ? (
          <span className="businessBtn">Open</span>
        ) : (
          <span className="businessBtn closing">Closed</span>
        )}
      </div>

      <div className="right-menu">
        <audio ref={audioVo} hidden>
          <source src={previewMp3} type="audio/mp3" />
        </audio>
        <audio ref={audioVo2} hidden>
          <source src={reminderMp3} type="audio/mp3" />
        </audio>

        <span className="navicon operatingState" onClick={() => setStatusDialog(true)}>
          Business Status
        </span>

        <Dropdown
          menu={{
            items: [
              { key: 'pwd', label: 'Change Password', onClick: () => setPwdVisible(true) },
              { key: 'logout', label: 'Sign Out', onClick: handleLogout },
            ],
          }}
        >
          <Button type="primary">
            {name} <DownOutlined />
          </Button>
        </Dropdown>
      </div>

      <Modal
        title="Business Status"
        open={statusDialog}
        width="420px"
        closable={false}
        onCancel={() => setStatusDialog(false)}
        footer={[
          <Button key="cancel" onClick={() => setStatusDialog(false)}>
            Cancel
          </Button>,
          <Button key="ok" type="primary" onClick={handleSaveStatus}>
            Confirm
          </Button>,
        ]}
      >
        <Radio.Group
          value={radioStatus}
          onChange={(e) => setRadioStatus(e.target.value)}
          className="status-radio-group"
        >
          <Radio value={1} className="status-radio">
            <div className="status-radio-title">Open</div>
            <div className="status-radio-desc">
              The restaurant is open and can receive orders. Select Closed to stop normal order intake.
            </div>
          </Radio>
          <Radio value={0} className="status-radio">
            <div className="status-radio-title">Closed</div>
            <div className="status-radio-desc">
              The restaurant is closed. Select Open to resume normal order intake.
            </div>
          </Radio>
        </Radio.Group>
      </Modal>

      <Password visible={pwdVisible} onClose={() => setPwdVisible(false)} />
    </div>
  )
}

export default Navbar
