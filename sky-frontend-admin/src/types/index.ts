// Backend unified response envelope: Result<T> { code, msg, data }
// success === (code === 1)
export interface Result<T = any> {
  code: number
  msg: string | null
  data: T
  status?: number
}

export interface PageResult<T = any> {
  total: number
  records: T[]
}
