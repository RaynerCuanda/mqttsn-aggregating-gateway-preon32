import { useEffect, useState } from 'react';
export default function MyPayload({ payloadImageSrc, payloadName, payloadContent, payloadUnit, lastReceive }) {  
  const [timeText, setTimeText] = useState("Node Sensor is off");

  useEffect(() => {
    if (!lastReceive || isNaN(lastReceive)) return;

    const updateTimer = () => {
      // Calculate how many seconds have passed since the timestamp
      const secondsPast = Math.floor((Date.now() - lastReceive) / 1000);
      setTimeText(`${secondsPast} secs since last receive`);
    };

    updateTimer();

    const intervalId = setInterval(updateTimer, 1000);

    return () => clearInterval(intervalId);
    
  }, [lastReceive]);
  
  return (
    <div className='payload-card-box'>
        <img src={payloadImageSrc} className='payload-image'/>
        <div className='wrapper-payload'>
          <div className='payload-name'>{payloadName}</div>
          <div className='wrapper-content'>
            <div className='payload-content'>{payloadContent}</div>
            <div className='payload-unit'>{payloadUnit}</div>
            <div className='time'>{timeText}</div>
          </div>
        </div>
      </div>
  );
}