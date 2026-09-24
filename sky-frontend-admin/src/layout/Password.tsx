import React from 'react'
import { Form, Input, Modal, message } from 'antd'
import { editPassword } from '@/api/users'

interface Props {
  visible: boolean
  onClose: () => void
}

// Mirrors src/layout/components/components/password.vue
const Password: React.FC<Props> = ({ visible, onClose }) => {
  const [form] = Form.useForm()

  const handleSave = async () => {
    let values: any
    try {
      values = await form.validateFields()
    } catch {
      return
    }
    try {
      const res = await editPassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      })
      if (res.data.code === 1) {
        message.success('Password updated successfully.')
      } else {
        message.error(res.data.msg)
      }
    } catch (err: any) {
      message.error('Request failed: ' + err.message)
    }
    form.resetFields()
    onClose()
  }

  const pwdRule = {
    required: true,
    pattern: /^[0-9A-Za-z]{6,20}$/,
    message: '6–20 letters or numbers (case-sensitive)',
  }

  return (
    <Modal
      title="Change Password"
      open={visible}
      width={568}
      onCancel={() => {
        form.resetFields()
        onClose()
      }}
      onOk={handleSave}
      okText="Save"
      cancelText="Cancel"
    >
      <Form form={form} labelCol={{ style: { width: 85 } }} preserve={false}>
        <Form.Item label="Current Password" name="oldPassword" rules={[pwdRule]}>
          <Input.Password placeholder="Enter a value" />
        </Form.Item>
        <Form.Item label="New Password" name="newPassword" rules={[pwdRule]}>
          <Input.Password placeholder="6–20 letters or numbers (case-sensitive)" />
        </Form.Item>
        <Form.Item
          label="Confirm Password"
          name="affirmPassword"
          dependencies={['newPassword']}
          rules={[
            { required: true, message: 'Re-enter your password' },
            ({ getFieldValue }) => ({
              validator(_, value) {
                if (!value || getFieldValue('newPassword') === value) {
                  return Promise.resolve()
                }
                return Promise.reject(new Error('Passwords do not match'))
              },
            }),
          ]}
        >
          <Input.Password placeholder="Enter a value" />
        </Form.Item>
      </Form>
    </Modal>
  )
}

export default Password
