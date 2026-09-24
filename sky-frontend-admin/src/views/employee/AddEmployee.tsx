import { useEffect, useState } from 'react'
import { Button, Form, Input, Radio, Space, message } from 'antd'
import { useNavigate, useSearchParams } from 'react-router-dom'
import HeadLable from '@/components/HeadLable'
import { addEmployee, editEmployee, queryEmployeeById } from '@/api/employee'

export default function AddEmployee() {
  const navigate = useNavigate(); const [search] = useSearchParams(); const id = search.get('id')
  const [form] = Form.useForm(); const [saving, setSaving] = useState(false)
  useEffect(() => { if (id) queryEmployeeById(id).then(r => { if (r.data.code===1) form.setFieldsValue({...r.data.data, sex:String(r.data.data.sex)==='0'?'Female':'Male'}); else message.error(r.data.msg) }) }, [id, form])
  const submit = async (stay=false) => {
    const values = await form.validateFields(); setSaving(true)
    try {
      const payload = {...values, ...(id?{id}:{}), sex: values.sex==='Female'?'0':'1'}
      const res = id ? await editEmployee(payload) : await addEmployee(payload)
      if (res.data.code===1) { message.success(id?'Employee updated successfully.':'Employee added successfully.'); if (stay && !id) form.resetFields(); else navigate('/employee') } else message.error(res.data.msg)
    } finally { setSaving(false) }
  }
  return <div className="dashboard-container"><div className="content-card"><HeadLable title={id?'Edit Employee':'Add Employee'} />
    <Form form={form} layout="vertical" initialValues={{sex:'Male'}} style={{maxWidth:560}}>
      <Form.Item label="Username" name="username" rules={[{required:true,message:'Enter a username'},{pattern:/^([a-z]|[0-9]){3,20}$/,message:'Use 3–20 lowercase letters or numbers'}]}><Input disabled={!!id} placeholder="Enter a username" /></Form.Item>
      <Form.Item label="Employee Name" name="name" rules={[{required:true,message:'Enter an employee name'}]}><Input placeholder="Enter an employee name" /></Form.Item>
      <Form.Item label="Phone" name="phone" rules={[{required:true,message:'Enter a phone number'},{pattern:/^1(3|4|5|6|7|8)\d{9}$/,message:'Enter a valid phone number'}]}><Input placeholder="Enter a phone number" /></Form.Item>
      <Form.Item label="Gender" name="sex"><Radio.Group><Radio value="Male">Male</Radio><Radio value="Female">Female</Radio></Radio.Group></Form.Item>
      <Form.Item label="ID Number" name="idNumber" rules={[{required:true,message:'Enter an ID number'},{pattern:/(^\d{15}$)|(^\d{18}$)|(^\d{17}(\d|X|x)$)/,message:'Enter a valid ID number'}]}><Input placeholder="Enter an ID number" /></Form.Item>
      <Space><Button onClick={()=>navigate('/employee')}>Cancel</Button><Button type="primary" loading={saving} onClick={()=>submit(false)}>Save</Button>{!id&&<Button loading={saving} onClick={()=>submit(true)}>Save & Add Another</Button>}</Space>
    </Form>
  </div></div>
}
