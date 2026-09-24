import React from 'react'
import { Navigate, Route, Routes } from 'react-router-dom'
import { getToken } from '@/utils/cookies'
import Layout from '@/layout/Layout'
import Login from '@/views/login/Login'
import NotFound from '@/views/NotFound'
import Dashboard from '@/views/dashboard/Dashboard'
import Statistics from '@/views/statistics/Statistics'
import Order from '@/views/order/Order'
import Setmeal from '@/views/setmeal/Setmeal'
import AddSetmeal from '@/views/setmeal/AddSetmeal'
import Dish from '@/views/dish/Dish'
import AddDishtype from '@/views/dish/AddDishtype'
import Category from '@/views/category/Category'
import Employee from '@/views/employee/Employee'
import AddEmployee from '@/views/employee/AddEmployee'

// Route guard — mirrors permission.ts: cookie `token` present -> allow, else -> /login
const RequireAuth: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  if (!getToken()) return <Navigate to="/login" replace />
  return <>{children}</>
}

const App: React.FC = () => {
  return (
    <Routes>
      <Route path="/login" element={<Login />} />
      <Route path="/404" element={<NotFound />} />
      <Route
        path="/"
        element={
          <RequireAuth>
            <Layout />
          </RequireAuth>
        }
      >
        <Route index element={<Navigate to="/dashboard" replace />} />
        <Route path="dashboard" element={<Dashboard />} />
        <Route path="statistics" element={<Statistics />} />
        <Route path="order" element={<Order />} />
        <Route path="setmeal" element={<Setmeal />} />
        <Route path="setmeal/add" element={<AddSetmeal />} />
        <Route path="dish" element={<Dish />} />
        <Route path="dish/add" element={<AddDishtype />} />
        <Route path="category" element={<Category />} />
        <Route path="employee" element={<Employee />} />
        <Route path="employee/add" element={<AddEmployee />} />
      </Route>
      <Route path="*" element={<Navigate to="/404" replace />} />
    </Routes>
  )
}

export default App
