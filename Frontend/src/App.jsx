import './App.css'
import io from 'socket.io-client'
import { useEffect, useState } from 'react';

const socket = io.connect("http://localhost:8080");

function MyPayload({ payloadImageSrc, payloadName, payloadContent, payloadUnit }) {  
  return (
    <div className='payload-card-box'>
        <img src={payloadImageSrc} className='payload-image'/>
        <div className='wrapper-payload'>
          <div className='payload-name'>{payloadName}</div>
          <div className='wrapper-content'>
            <div className='payload-content'>{payloadContent}</div>
            <div className='payload-unit'>{payloadUnit}</div>
          </div>
        </div>
      </div>
  );
}

function MyRoom({roomId, payloadContent}){
  const [temperature, setTemperature] = useState()
  const [humidity, setHumidity] = useState()
  const [airPressure, setAirPressure] = useState()
  const [lightIntensity, setLightIntensity] = useState()
  const [vibration, setVibration] = useState()
  
  useEffect(() =>{
    socket.on('mqtt_data', (data) => {
      console.log(data);
    })
  }, [socket])

  const payloadData = [
    { id: 1, image: "public/temperature.png", payloadName: "Temperature", unit: " °C" },
    { id: 2, image: "public/vite.svg", payloadName: "Humidity", unit: " % RH" },
    { id: 3, image: "public/vite.svg", payloadName: "Air Pressure", unit: " kPa" },
    { id: 4, image: "public/vite.svg", payloadName: "Light Intensity", unit: " lx" },
    { id: 5, image: "public/vite.svg", payloadName: "Vibration", unit: " g" },
  ]

  return(
    <div className='room-wrapper'>
      <div className="room-name">{roomId}</div>
      {payloadData.map((item) => (
        <MyPayload
          key={item.id}
          payloadImageSrc={item.image}
          payloadName={item.payloadName}
          payloadContent={item.content}
          payloadUnit={item.unit}
        />
      ))}      
    </div>
  );
}

export default function App() {

  return (
    <>
      <div className='dashboard-layout'>
        <MyRoom roomId={9018}/>
        <MyRoom roomId={9019}/>
        <MyRoom roomId={9020}/>
      </div>
    </>
  )
}
