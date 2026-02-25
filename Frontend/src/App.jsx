import './App.css'
import io from 'socket.io-client'
import { useEffect, useState } from 'react';
  <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.0.0-beta3/css/all.min.css"/>

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

function MyRoom({roomId}){
  
  const initialData = [
    {topicName: "temperature"      ,value: 0, unit: " °C"  , imgSrc: "temperature.png", label: "Temperature"},
    {topicName: "air_pressure"     ,value: 0, unit: " kPa" , imgSrc: "temperature.png", label: "Air Pressure"},
    {topicName: "humidity"         ,value: 0, unit: " %RH" , imgSrc: "temperature.png", label: "Humidity"},
    {topicName: "light_intensity"  ,value: 0, unit: " lx"  , imgSrc: "temperature.png", label: "Light Intensity"},
    {topicName: "vibration"        ,value: 0, unit: " g"   , imgSrc: "temperature.png", label: "Vibration"},
  ]
  
  const [sensorData, setSensorData] = useState(initialData)

  // sumber: https://react.dev/learn/updating-arrays-in-state
  function updateSensorData(payloadName, payloadContent){
    const nextList = sensorData.map(payload => {
      if(payload.topicName === payloadName){
        return{...payload, value: payloadContent};
      } else{
        return payload;
      }
    });
    setSensorData(nextList);
  }
  
  useEffect(() =>{
    socket.on('mqtt_data', (data) => {
      if (data.roomName == roomId){
        updateSensorData(data.payloadName, data.payloadContent)
      }
      // console.log(data);
    })
  }, [socket])

  return(
    <div className='room-wrapper'>
      <div className="room-name">{roomId}</div>
      <div className='payload-wrapper'>
        {sensorData.map((item) => (
          <MyPayload
            key={item.topicName}
            payloadImageSrc={item.imgSrc}
            payloadName={item.label}
            payloadContent={item.value}
            payloadUnit={item.unit}
          />
        ))}      
      </div>
    </div>
  );
}

export default function App() {
  return (
    <>
      <div className='dashboard-layout'>
        <MyRoom roomId={9017}/>
        <MyRoom roomId={9018}/>
        {/* <MyRoom roomId={9019}/>
        <MyRoom roomId={9020}/> */}
      </div>
    </>
  )
}
