import React from 'react'
import tableEmpty from '@/assets/table_empty.png'
import searchEmpty from '@/assets/search_table_empty.png'

interface Props {
  isSearch?: boolean
}

// Mirrors src/components/Empty/index.vue
const Empty: React.FC<Props> = ({ isSearch }) => {
  return (
    <div className="emptyPage">
      {!isSearch ? (
        <>
          <img src={tableEmpty} alt="empty" />
          <p>No data available.</p>
        </>
      ) : (
        <>
          <img src={searchEmpty} alt="empty" />
          <p>No matching results found.</p>
        </>
      )}
    </div>
  )
}

export default Empty
