// import { useState } from 'react'
// import { MyButton } from "components/DataBox";
import './App.css'
import io from 'socket.io-client'
import { useEffect } from 'react';

const socket = io.connect("http://localhost:8080");

function MyPayload({ payload_image_src, payload_name, payload_content, payload_unit }) {  
  return (
      <div className='payload-card-box'>
        <img src={payload_image_src} className='payload-image'/>
        <div className='wrapper-payload'>
          <div className='payload-name'>{payload_name}</div>
          <div className='wrapper-content'>
            <div className='payload-content'>{payload_content}</div>
            <div className='payload-unit'>{payload_unit}</div>
          </div>
        </div>
      </div>
  );
}

function MyRoom(payload_content){

  useEffect(() =>{
    socket.on('mqtt_data', (data) => {
      // alert(data.message);
      console.log("New data", data);
    })
  }, [socket])

  const payloadData = [
    { id: 1, image: "public/vite.svg", name: "Temperature", content: "0", unit: " °C" },
    { id: 2, image: "public/vite.svg", name: "Humidity", content: "0", unit: " % RH" },
    { id: 3, image: "public/vite.svg", name: "Air Pressure", content: "0", unit: " kPa" },
    { id: 4, image: "public/vite.svg", name: "Light Intensity", content: "0", unit: " lx" },
    { id: 5, image: "public/vite.svg", name: "Vibration", content: "0", unit: " g" },
  ]

  return(
    <div className='room-wrapper'>
      <div className="room-name">Ruang 9017</div>
      {payloadData.map((item) => (
        <MyPayload
          key={item.id}
          payload_image_src={item.image}
          payload_name={item.name}
          payload_content={item.content}
          payload_unit={item.unit}
        />
      ))}      
    </div>
  );
}

export default function App() {

  return (
    <>
      <div className='dashboard-layout'>
        <MyRoom/>
        <MyRoom/>
        <MyRoom/>
        <MyRoom/>
        <MyRoom/>
      </div>
    </>
  )
}
