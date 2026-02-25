// import './App.css'
// import io from 'socket.io-client'
// import { useEffect, useState } from 'react';

// const socket = io.connect("http://localhost:8080");

// function MyPayload({ payloadImageSrc, payloadName, payloadContent, payloadUnit }) {  
//   return (
//     <div className='payload-card-box'>
//         <img src={payloadImageSrc} className='payload-image'/>
//         <div className='wrapper-payload'>
//           <div className='payload-name'>{payloadName}</div>
//           <div className='wrapper-content'>
//             <div className='payload-content'>{payloadContent}</div>
//             <div className='payload-unit'>{payloadUnit}</div>
//           </div>
//         </div>
//       </div>
//   );
// }

// function MyRoom({roomId}){
//   const [temperature, setTemperature] = useState(0)
//   const [airPressure, setAirPressure] = useState(0)
//   const [humidity, setHumidity] = useState(0)
//   const [lightItensity, setLightItensity] = useState(0)
//   const [vibration, setVibration] = useState(0)
  
//   useEffect(() =>{
//     socket.on('mqtt_data', (data) => {
//       if (data.roomName == roomId){
//         if(data.payloadName == "temperature"){
//           setTemperature(data.payloadContent);
//         } else if (data.payloadName == "air_pressure"){
//           setAirPressure(data.payloadContent);
//         } else if (data.payloadName == "humidity"){
//           setHumidity(data.payloadContent);
//         } else if (data.payloadName == "light_intensity"){
//           setLightItensity(data.payloadContent);
//         } else if (data.payloadName == "vibration"){
//           setVibration(data.payloadContent);
//         }
//       }
//       console.log(data);
//     })
//   }, [socket])

//   return(
//     <div className='room-wrapper'>
//       <div className="room-name">{roomId}</div>
//         <MyPayload
//           payloadImageSrc={"temperature.png"}
//           payloadName={"Temperature"}
//           payloadContent={temperature}
//           payloadUnit={" °C"}
//         />
//         <MyPayload
//           payloadImageSrc={"temperature.png"}
//           payloadName={"Air Pressure"}
//           payloadContent={airPressure}
//           payloadUnit={" kPa"}
//         />
//         <MyPayload
//           payloadImageSrc={"temperature.png"}
//           payloadName={"Humidity"}
//           payloadContent={humidity}
//           payloadUnit={" %RH"}
//         />
//         <MyPayload
//           payloadImageSrc={"temperature.png"}
//           payloadName={"Light Intensity"}
//           payloadContent={lightItensity}
//           payloadUnit={" lx"}
//         />
//         <MyPayload
//           payloadImageSrc={"temperature.png"}
//           payloadName={"Vibration"}
//           payloadContent={vibration}
//           payloadUnit={" g"}
//         />
//     </div>
//   );
// }

// export default function App() {


//   return (
//     <>
//       <div className='dashboard-layout'>
//         <MyRoom roomId={9017}/>
//         <MyRoom roomId={9018}/>
//         <MyRoom roomId={9019}/>
//         <MyRoom roomId={9020}/>
//       </div>
//     </>
//   )
// }
