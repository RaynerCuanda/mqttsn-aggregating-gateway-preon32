import java.util.Arrays;
import com.virtenio.driver.device.ADXL345; //accelerometer
import com.virtenio.driver.device.ADT7410; // suhu / temperature
import com.virtenio.driver.device.MPL115A2; // barometer
import com.virtenio.driver.device.SHT21; // humidity

import com.virtenio.driver.device.at86rf231.AT86RF231; // radio transmiiter
import com.virtenio.driver.device.at86rf231.Constants; //
import com.virtenio.driver.gpio.GPIO; //
import com.virtenio.driver.gpio.NativeGPIO;
import com.virtenio.driver.i2c.I2C;
import com.virtenio.driver.i2c.NativeI2C;
import com.virtenio.driver.spi.NativeSPI;
import com.virtenio.io.ChannelBusyException;
import com.virtenio.io.NoAckException;
import com.virtenio.preon32.examples.common.RadioInit; // radio
import com.virtenio.preon32.shuttle.Shuttle; 
import com.virtenio.radio.RadioDriverException;
import com.virtenio.radio.ieee_802_15_4.Frame; // frame
import com.virtenio.vm.Time; // get

//import com.virtenio.driver.usart.USART; // usb 
//import com.virtenio.preon32.examples.common.RadioInit;

public class nSensorRefence {
	private int COMMON_CHANNEL = 24; // channel
	private int COMMON_PANID = 0xCAFE; // Personal Area Network ID
	//private int BROADCAST_ADDR = 0xFFFF;
	private int NODESENSOR_ADDR = 0xAFFE; // ini aja yang diubah // punya alamat
	private int BASESTATION_ADDR = 0xBABE; // ini base station  / sink

	private ADXL345 accelerationSensor; 
	private ADT7410 suhu;
	private SHT21 sht21;
	private NativeI2C i2c;
	private GPIO accelIrqPin1;
	private GPIO accelIrqPin2;
	private GPIO accelCs;
	

	AT86RF231 radio;
	Shuttle shuttle;

	long hour7 = 25200000;

	public void initRadio() throws Exception {
		radio = RadioInit.initRadio();
		radio.setChannel(COMMON_CHANNEL);
		radio.setPANId(COMMON_PANID);
		radio.setShortAddress(BASESTATION_ADDR);
	}

	private void init() throws Exception
	{
		// accl
		accelIrqPin1 = NativeGPIO.getInstance(37);
		accelIrqPin2 = NativeGPIO.getInstance(25);
		accelCs = NativeGPIO.getInstance(20);
		// suhu
		i2c = NativeI2C.getInstance(1);
		i2c.open(I2C.DATA_RATE_400);

		NativeSPI spi = NativeSPI.getInstance(0);
		spi.open(ADXL345.SPI_MODE, ADXL345.SPI_BIT_ORDER, ADXL345.SPI_MAX_SPEED);
		accelerationSensor = new ADXL345(spi, accelCs);
		accelerationSensor.open();
		accelerationSensor.setDataFormat(ADXL345.DATA_FORMAT_RANGE_2G);
		accelerationSensor.setDataRate(ADXL345.DATA_RATE_3200HZ);
		accelerationSensor.setPowerControl(ADXL345.POWER_CONTROL_MEASURE);
		
		// suhu
		suhu = new ADT7410(i2c, ADT7410.ADDR_0, null, null);
		suhu.open();
		suhu.setMode(ADT7410.CONFIG_MODE_CONTINUOUS);
		
		// humidity
		sht21 = new SHT21(i2c);
		sht21.open();
		sht21.setResolution(SHT21.RESOLUTION_RH12_T14);
	}


	public void sendMessage(Frame frame) throws Exception
	{
		new Thread()
		{
			@Override
			public void run() {
				try { radio.transmitFrame(frame); //

				} catch (RadioDriverException e) {
					//e.printStackTrace();
				} catch (NoAckException e) {
					//e.printStackTrace();
				} catch (ChannelBusyException e) {
					//e.printStackTrace();
				}
			}
		}.start();
	}

	public void senseAccl() throws Exception {
		init();
		short[] accl = new short[3];
		int sn = 1; // ini boleh tdk ada
		String mesg = null; 
		Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
				| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
			frame.setSrcAddr(NODESENSOR_ADDR);
			frame.setSrcPanId(COMMON_PANID);
			frame.setDestAddr(BASESTATION_ADDR);
			frame.setDestPanId(COMMON_PANID);
			radio.setState(Constants.STATE_TX_ARET_ON);

		while (true) {
			try {
				//int raw = suhu.getTemperatureRaw();
				float celsius = suhu.getTemperatureCelsius();
				accelerationSensor.getValuesRaw(accl, 0);
				mesg = "accl" + Arrays.toString(accl) + "suhu" + suhu.getTemperatureCelsius() + "hum";
				
				//suhu+hum+baro
				frame.setSequenceNumber(sn);
				frame.setPayload(mesg.getBytes());
				System.out.println("from " + NODESENSOR_ADDR + " to " + BASESTATION_ADDR );
				sendMessage(frame);
			} catch (Exception e) {
				System.out.println("ADXL345 error");
			}
			Thread.sleep(500);
			sn++;
		}
	}

	public static void main(String [] args ) throws Exception
	{
		nSensorRefence ns = new nSensorRefence();
		ns.initRadio();
		long getTime = Time.currentTimeMillis()+ ns.hour7;
		System.out.println(stringFormatTime.SFFull(getTime) + "\n");
		ns.senseAccl();
	}

}