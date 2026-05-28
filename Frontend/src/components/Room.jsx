import { useEffect, useState } from 'react';
import MyPayload from './Payload';
import io from 'socket.io-client'

const socket = io.connect("http://localhost:8080");

export default function MyRoom({roomId}){
  
  const initialData = [
    {topicName: "Temperature"      ,value: 0, unit: " °C"  , imgSrc: "temperature.png", label: "Temperature", timeStamp: "-"},
    {topicName: "AirPressure"     ,value: 0, unit: " kPa" , imgSrc: "air_pressure.png", label: "Air Pressure", timeStamp: "-"},
    {topicName: "Humidity"         ,value: 0, unit: " %RH" , imgSrc: "humidity.png", label: "Humidity", timeStamp: "-"},
    {topicName: "Vibration"        ,value: 0, unit: " g"   , imgSrc: "vibration.png", label: "Vibration", timeStamp: "-"},
    // {topicName: "light_intensity"  ,value: 0, unit: " lx"  , imgSrc: "temperature.png", label: "Light Intensity"},
  ];
  const [sensorData, setSensorData] = useState(initialData);

  // sumber: https://react.dev/learn/updating-arrays-in-state
  function updateSensorData(payloadName, payloadContent, timeStamp){
    setSensorData(prevData => 
      prevData.map(payload => {
        if (payload.topicName === payloadName) {
          return { ...payload, value: payloadContent, timeStamp: timeStamp };
        } else {
          return payload;
        }
      })
    );
  }
  
 useEffect(() =>{
    const handleMqttData = (data) => {
      if (data.roomName === roomId){
        updateSensorData(data.payloadName, data.payloadContent, data.timeStamp)
      }
    };

    socket.on('mqtt_data', handleMqttData);

    return () => {
      socket.off('mqtt_data', handleMqttData);
    };
  }, [roomId])

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
            lastReceive={item.timeStamp}
          />
        ))}      
      </div>
    </div>
  );
}