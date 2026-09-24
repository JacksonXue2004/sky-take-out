import { useCallback, useEffect, useState } from 'react'
import { Button, Form, Input, InputNumber, Modal, Pagination, Select, Space, Table, message } from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import { addCategory, deleCategory, editCategory, enableOrDisableEmployee, getCategoryPage } from '@/api/category'

export default function Category() {
  const [name,setName]=useState(''); const [type,setType]=useState<number>(); const [page,setPage]=useState(1); const [pageSize,setPageSize]=useState(10)
  const [rows,setRows]=useState<any[]>([]); const [total,setTotal]=useState(0); const [loading,setLoading]=useState(false); const [open,setOpen]=useState(false)
  const [editing,setEditing]=useState<any>(); const [addType,setAddType]=useState(1); const [form]=Form.useForm()
  const load=useCallback(async()=>{setLoading(true);try{const r=await getCategoryPage({page,pageSize,name:name||undefined,type:type||undefined});if(String(r.data.code)==='1'){setRows(r.data.data.records||[]);setTotal(Number(r.data.data.total||0))}else message.error(r.data.msg)}finally{setLoading(false)}},[page,pageSize,name,type])
  useEffect(()=>{load()},[load])
  const showAdd=(t:number)=>{setEditing(undefined);setAddType(t);form.resetFields();setOpen(true)}
  const showEdit=(r:any)=>{setEditing(r);form.setFieldsValue({name:r.name,sort:r.sort});setOpen(true)}
  const save=async(keep=false)=>{const v=await form.validateFields();const r=editing?await editCategory({id:editing.id,...v}):await addCategory({...v,type:String(addType)});if(r.data.code===1){message.success(editing?'Category updated successfully.':'Category added successfully.');load();if(!keep||editing)setOpen(false);else form.resetFields()}else message.error(r.data.msg||r.data.desc)}
  const toggle=(r:any)=>Modal.confirm({title:'Notice',content:'Change this category status?',onOk:async()=>{await enableOrDisableEmployee({id:r.id,status:r.status?0:1});message.success('Category status updated.');load()}})
  const remove=(id:string)=>Modal.confirm({title:'Confirm Delete',content:'This will permanently delete the category. Continue?',okText:'Delete',okButtonProps:{danger:true},onOk:async()=>{const r=await deleCategory(id);if(r.data.code===1){message.success('Deleted successfully.');load()}else message.error(r.data.msg)}})
  return <div className="dashboard-container"><div className="content-card">
    <div className="tableBar"><span className="tableLab">Category Name:</span><Input value={name} allowClear placeholder="Enter a valueCategory Name" style={{width:200}} onChange={e=>setName(e.target.value)} />
      <span className="tableLab">Category Type:</span><Select value={type} allowClear placeholder="Select" style={{width:150}} options={[{value:1,label:'Item Category'},{value:2,label:'Combo Category'}]} onChange={setType}/>
      <Button type="primary" icon={<SearchOutlined/>} onClick={()=>{setPage(1);load()}}>Search</Button><span className="spacer"/><Button onClick={()=>showAdd(1)}>Add Item Category</Button><Button type="primary" onClick={()=>showAdd(2)}>Add Combo Category</Button>
    </div>
    <Table rowKey="id" loading={loading} dataSource={rows} pagination={false} columns={[
      {title:'Category Name',dataIndex:'name'},{title:'Category Type',dataIndex:'type',render:v=>Number(v)===1?'Item Category':'Combo Category'},{title:'Sort Order',dataIndex:'sort'},
      {title:'Status',dataIndex:'status',render:v=><span className={`tableColumn-status ${String(v)==='0'?'stop-use':''}`}>{String(v)==='0'?'Disabled':'Enabled'}</span>},{title:'Last Updated',dataIndex:'updateTime'},
      {title:'Actions',width:220,render:(_,r:any)=><Space><Button type="link" onClick={()=>showEdit(r)}>Edit</Button><Button type="link" danger onClick={()=>remove(r.id)}>Delete</Button><Button type="link" onClick={()=>toggle(r)}>{r.status?'Disabled':'Enabled'}</Button></Space>}
    ]}/><div className="pageList"><Pagination current={page} pageSize={pageSize} total={total} showSizeChanger showQuickJumper showTotal={n=>`${n} total`} onChange={(p,s)=>{setPage(p);setPageSize(s)}}/></div>
    <Modal open={open} title={editing?'Edit Category':`Add ${addType===1?'Item':'Combo'} Category`} onCancel={()=>setOpen(false)} footer={<Space><Button onClick={()=>setOpen(false)}>Cancel</Button><Button type="primary" onClick={()=>save(false)}>Confirm</Button>{!editing&&<Button onClick={()=>save(true)}>Save & Add Another</Button>}</Space>}>
      <Form form={form} layout="vertical"><Form.Item label="Category Name" name="name" rules={[{required:true,message:'Enter a category name'},{min:2,max:20,message:'Category name must be 2–20 characters'},{pattern:/^[A-Za-z\u4e00-\u9fa5]+$/,message:'Use letters or Chinese characters only'}]}><Input/></Form.Item><Form.Item label="Sort Order" name="sort" rules={[{required:true,message:'Enter a sort order'}]}><InputNumber min={0} max={99} precision={0} style={{width:'100%'}}/></Form.Item></Form>
    </Modal>
  </div></div>
}
