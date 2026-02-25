import './App.css'
import MyRoom from "./components/Room"; 

export default function App() {
  return (
    <>
      <div className='dashboard-layout'>
        <MyRoom roomId={9017}/>
        <MyRoom roomId={9018}/>
        <MyRoom roomId={9019}/>
        {/* <MyRoom roomId={9020}/> */}
      </div>
    </>
  )
}
