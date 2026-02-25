export default function MyPayload({ payloadImageSrc, payloadName, payloadContent, payloadUnit }) {  
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