import { useCallback, useEffect, useState } from 'react'
import { Button, Input, Modal, Pagination, Space, Table, message } from 'antd'
import { PlusOutlined, SearchOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import { enableOrDisableEmployee, getEmployeeList } from '@/api/employee'

export default function Employee() {
  const navigate = useNavigate()
  const [name, setName] = useState('')
  const [page, setPage] = useState(1)
  const [pageSize, setPageSize] = useState(10)
  const [total, setTotal] = useState(0)
  const [rows, setRows] = useState<any[]>([])
  const [loading, setLoading] = useState(false)
  const load = useCallback(async () => {
    setLoading(true)
    try {
      const res = await getEmployeeList({ page, pageSize, name: name || undefined })
      if (String(res.data.code) === '1') {
        setRows(res.data.data.records || [])
        setTotal(Number(res.data.data.total || 0))
      } else message.error(res.data.msg)
    } catch (e: any) { message.error(`Request failed: ${e.message}`) } finally { setLoading(false) }
  }, [page, pageSize, name])
  useEffect(() => { load() }, [load])

  const toggle = (row: any) => Modal.confirm({
    title: 'Notice', content: 'Change this account status?',
    onOk: async () => {
      const res = await enableOrDisableEmployee({ id: row.id, status: row.status ? 0 : 1 })
      if (res.status === 200) { message.success('Account status updated.'); load() }
    },
  })
  return <div className="dashboard-container"><div className="content-card">
    <div className="tableBar"><span className="tableLab">Employee Name:</span>
      <Input value={name} allowClear placeholder="Enter an employee name" style={{ width: 220 }} onChange={e => setName(e.target.value)} onPressEnter={() => { setPage(1); load() }} />
      <Button type="primary" icon={<SearchOutlined />} onClick={() => { setPage(1); load() }}>Search</Button><span className="spacer" />
      <Button type="primary" icon={<PlusOutlined />} onClick={() => navigate('/employee/add')}>Add Employee</Button>
    </div>
    <Table rowKey="id" loading={loading} dataSource={rows} pagination={false} columns={[
      { title: 'Employee Name', dataIndex: 'name' }, { title: 'Username', dataIndex: 'username' }, { title: 'Phone', dataIndex: 'phone' },
      { title: 'Account Status', dataIndex: 'status', render: v => <span className={`tableColumn-status ${String(v)==='0'?'stop-use':''}`}>{String(v)==='0'?'Disabled':'Enabled'}</span> },
      { title: 'Last Updated', dataIndex: 'updateTime' },
      { title: 'Actions', width: 180, render: (_, r: any) => <Space><Button type="link" disabled={r.username==='admin'} onClick={() => navigate(`/employee/add?id=${r.id}`)}>Edit</Button><Button type="link" danger={!!r.status} disabled={r.username==='admin'} onClick={() => toggle(r)}>{r.status?'Disabled':'Enabled'}</Button></Space> },
    ]} />
    <div className="pageList"><Pagination current={page} pageSize={pageSize} total={total} showSizeChanger showQuickJumper showTotal={n=>`${n} total`} onChange={(p,s)=>{setPage(p);setPageSize(s)}} /></div>
  </div></div>
}
