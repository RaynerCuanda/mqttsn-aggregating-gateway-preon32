import { useState } from 'react'
import './App.css'

function App() {
  // const [count, setCount] = useState(0)

  return (
    <>
      <div className='dashboard-layout'>
        <div className='wrapper'>
          <div className='card-box'>
            <div className=''>Big Number</div>
            <div>Details</div>
          </div>
          <div className='card-box'>
            <div>Big Number</div>
            <div>Details</div>
          </div>
        </div>
        <div className='wrapper'>
          <div className='card-box'>
            <div>Big Number</div>
            <div>Details</div>
          </div>
          <div className='card-box'>
            <div>Big Number</div>
            <div>Details</div>
          </div>
        </div>
      </div>
    </>
  )
}

export default App
