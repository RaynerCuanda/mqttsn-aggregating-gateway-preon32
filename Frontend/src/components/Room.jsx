import { useEffect, useState } from 'react';
import MyPayload from './Payload';
import io from 'socket.io-client'

const socket = io.connect("http://localhost:8080");

export default function MyRoom({roomId}){
  
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