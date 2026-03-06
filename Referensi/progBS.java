import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;
import java.io.IOException;
import java.io.OutputStream;

import com.virtenio.driver.device.ADT7410;
import com.virtenio.driver.device.ADXL345;
import com.virtenio.driver.device.MPL115A2;
import com.virtenio.driver.device.SHT21;
import com.virtenio.driver.gpio.GPIO;
import com.virtenio.driver.gpio.GPIOException;
import com.virtenio.driver.gpio.NativeGPIO;
import com.virtenio.driver.i2c.I2C;
import com.virtenio.driver.i2c.NativeI2C;
import com.virtenio.driver.spi.NativeSPI;
import com.virtenio.driver.spi.SPIException;
import com.virtenio.driver.usart.NativeUSART;
import com.virtenio.driver.usart.USART;
import com.virtenio.driver.usart.USARTException;
import com.virtenio.driver.usart.USARTParams;
import com.virtenio.io.ChannelBusyException;
import com.virtenio.io.NoAckException;
import com.virtenio.misc.StringUtils;
import com.virtenio.preon32.examples.common.USARTConstants;
import com.virtenio.preon32.examples.common.RadioInit;
import com.virtenio.preon32.shuttle.Shuttle;
import com.virtenio.radio.RadioDriverException;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;

import com.virtenio.driver.device.at86rf231.*;

import com.virtenio.vm.Time;

public class progBS {
	private int COMMON_CHANNEL = 24;
	private int COMMON_PANID = 0xCAFE;
	private int BROADCAST_ADDR = 0xFFFF;
	/*
	//					  { 0	6		1	7		2	8		3	9		4	10		5	11	}
	int[] NODE_ADDR 	= { 0xBABE, 	0xAFFE, 	0xBAFE, 	0xBEBA, 	0xAFAF, 	0xEFEF }; 
	String[] NODE_ADDRs = { "0xBABE", 	"0xAFFE", 	"0xBAFE", 	"0xBEBA", 	"0xAFAF", 	"0xEFEF"};
	long[] NODE_ADDRi 	= {47806, 		45054, 		47870, 		48826, 		44975, 		61423};
	int[] statusBC 		= {0,0,0,0,0,0};
	long[] rttTABLE		= {0,0,0,0,0,0};
	*/
	int[] NODE_ADDR 	= { 0xBABE, 	0xAFFE, 	0xBAFE, 	0xBEBA, 	0xAFAF, 	0xEFEF, 
							0xBAB1, 	0xAFF1, 	0xBAF1, 	0xBEB1, 	0xAFA1, 	0xEFE1 }; 
	String[] NODE_ADDRs = { "0xBABE", 	"0xAFFE", 	"0xBAFE", 	"0xBEBA", 	"0xAFAF", 	"0xEFEF",
							"0xBAB1", 	"0xAFF1", 	"0xBAF1", 	"0xBEB1", 	"0xAFA1", 	"0xEFE1"};
	// DAFTAR COM		  {  COM3		COM5		COM4		COM6	   	COM7		COM8	}	
	long[] NODE_ADDRi 	= {47806, 45054, 47870, 48826, 44975, 61423, 47793, 45041, 47857, 48817, 44961, 61409 };
	
	int[] statusBC 		= {0,0,0,0,0,0,0,0,0,0,0,0};
	long[] rttTABLE		= {0,0,0,0,0,0,0,0,0,0,0,0};
	
	
	AT86RF231 radio;
	FrameIO fio;
	Shuttle shuttle;
	USART usart;
	private ADXL345 acclSensor;
	private GPIO accelCs;
	
	/* khusus Temperature, Humidity dan Barometer
	 * 
	 */
	private NativeI2C i2c;
	private ADT7410 tempSensor;
	private SHT21 humTempSensor;
	private MPL115A2 pressSensor;
	private boolean openTHPSensor = false;
	private boolean stopTHPSensing  = false;
	
	long hour7 = 25200000;
	long hour8 = 28800000;
	int nodeKE = 0;
	private volatile boolean stop = false;
	
	int myADDR = NODE_ADDR[nodeKE]; 
	private static OutputStream out;
	
	int actSensor = 0;
	boolean finrecvReply = false;
	
	Integer SN;
	

	
	
	public void initRadio() throws Exception {
		radio = RadioInit.initRadio();
		radio.setChannel(COMMON_CHANNEL);
		radio.setPANId(COMMON_PANID);
		radio.setShortAddress(NODE_ADDR[0]);
	}
	
	public void resetSN() throws Exception {
		SN = 0;
	}
	
	public void initFrameIO() throws Exception {
		final RadioDriver radioDriver = new AT86RF231RadioDriver(radio);
		fio = new RadioDriverFrameIO(radioDriver);	
	}
	
	public void useUSART() throws Exception {
		usart = configUSART();
	}
	private USART configUSART() throws Exception {

		int instanceID = 0;
		USARTParams params = USARTConstants.PARAMS_115200;
		NativeUSART usart = NativeUSART.getInstance(instanceID);
		try {
			usart.close();
			usart.open(params); 
			return usart;
		}
		catch (Exception e) {
			return null;
		}
	}
	
	private void initACCL() throws Exception
	{
		accelCs = NativeGPIO.getInstance(20); // init GPIO
		NativeSPI spi = NativeSPI.getInstance(0); // init SPI
		spi.open(ADXL345.SPI_MODE, ADXL345.SPI_BIT_ORDER, ADXL345.SPI_MAX_SPEED); // open SPI
		// Inisiasi ADXL345
		acclSensor = new ADXL345(spi,accelCs);
		acclSensor.open();
		acclSensor.setPowerControl(ADXL345.POWER_MODE_NORMAL); 
		acclSensor.setDataFormat(ADXL345.DATA_FORMAT_RANGE_2G); 
		acclSensor.setDataRate(ADXL345.DATA_RATE_100HZ);
		acclSensor.setPowerControl(ADXL345.POWER_CONTROL_MEASURE);
	}
	
	private void resetStatusBC () throws Exception
	{
		for (int i=0;i<statusBC.length;i++) statusBC[i]=0;
	}
	
	private void initrttTABLE () throws Exception
	{
		for (int i=0;i<NODE_ADDRi.length;i++) rttTABLE[i]=0;
	}
	
	private int indexOf (long srcAddr) throws Exception 
	{
		int idx = -1;
		
		for (int i=0;i< NODE_ADDRi.length;i++) {
			if (NODE_ADDRi[i] == srcAddr) idx = i;
		}
		return idx;
	}
	
	
	/* INI VOID BERLAKU LOCAL BASE STATION 
	 * 
	 */
	
	public void initTempHumPressSensor() throws Exception 
	{
		// init I2C
		System.out.println("I2C(Init)");
		i2c = NativeI2C.getInstance(1);
		i2c.open(I2C.DATA_RATE_400);
		
		// init GPIO
		GPIO resetPin = NativeGPIO.getInstance(24);
		GPIO shutDownPin = NativeGPIO.getInstance(12);
		
		// init temperature sensor
		tempSensor = new ADT7410(i2c, ADT7410.ADDR_0, null, null);
		tempSensor.open();
		tempSensor.setMode(ADT7410.CONFIG_MODE_CONTINUOUS);

		// SHT21 ( Init ) humidity
		humTempSensor = new SHT21(i2c);  
		humTempSensor.open();
		humTempSensor.setResolution(SHT21.RESOLUTION_RH12_T14);
		humTempSensor.reset();
		
		// init MPL115A2 (barometer)
		pressSensor = new MPL115A2(i2c, resetPin, shutDownPin);
		pressSensor.open();
		pressSensor.setReset(false);
		pressSensor.setShutdown(false);
		
		// 
		openTHPSensor = true;
		System.out.println("DONE !");
}	
		
	public void senseTempHumBaroLocal() throws Exception 
	{
			if (!openTHPSensor) {
				initTempHumPressSensor();
			}
			try { useUSART(); out = usart.getOutputStream(); } catch (Exception e) { e.printStackTrace();}
			while (!stopTHPSensing)
			{
				try {		
					
						int raw = tempSensor.getTemperatureRaw();
						float celsius = tempSensor.getTemperatureCelsius();

						// humidity conversion
						humTempSensor.startRelativeHumidityConversion(); 
						Thread.sleep(SHT21.MAX_HUMIDITY_CONVERSION_TIME_R12); // 3 ms 
						int rawRH = humTempSensor.getRelativeHumidityRaw();
						float rh = SHT21.convertRawRHToRHw(rawRH);
	
						pressSensor.startBothConversion();
						Thread.sleep(MPL115A2.BOTH_CONVERSION_TIME); // 3 ms - 0.003 detik
						int pressurePr = pressSensor.getPressureRaw();
						int tempRaw = pressSensor.getTemperatureRaw();
						float pressure = pressSensor.compensate(pressurePr, tempRaw);
						//Thread.sleep(1000 - MPL115A2.BOTH_CONVERSION_TIME); 

						long getT = Time.currentTimeMillis();
						String message = "SENSE " + stringFormatTime.SFFull(getT) + " T:" + celsius + " " + "H:" + rh + " " + "P:" + pressure;
						//System.out.println(message + " ");
						String mesgUART = "#" + message + "\n" + "#";
						out.write(mesgUART.getBytes()); 
						out.flush();
					} catch (Exception e) { System.out.println("sensor error"); 
					}
			
			}

			System.out.println("Berhenti sensing TempHumBaroLocal");
	}
	
	public void broadCast(String mesg, Integer sn) throws Exception
	{
		try {
			Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
					| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16); 
			frame.setSrcAddr(NODE_ADDR[0]);
			frame.setSrcPanId(COMMON_PANID);
			frame.setDestAddr(BROADCAST_ADDR);
			frame.setDestPanId(COMMON_PANID); 
			frame.setSequenceNumber(sn);
			String message = mesg + " " + Time.currentTimeMillis(); // + transmit Time;
			frame.setPayload(message.getBytes());
			radio.setState(AT86RF231.STATE_TX_ARET_ON);
			radio.transmitFrame(frame);
		} catch (RadioDriverException e) {
			//e.printStackTrace();
		} catch (NoAckException e) {
			//e.printStackTrace();
		} catch (ChannelBusyException e) {
			//e.printStackTrace();
		}
	}
	
	public void dispatch(final Frame f, final long t4) throws Exception 
	{
		int code=0; int idx=-1; String reply;
		try { initACCL(); out = usart.getOutputStream(); } catch (Exception e) { e.printStackTrace();}	
		if (f!=null) 
		{
			byte[] dg = f.getPayload(); 
			String mesgRecv = new String(dg, 0, dg.length);
			String mesgSplit[] = StringUtils.split(mesgRecv, " ");
			String hex_addr = Integer.toHexString((int) f.getSrcAddr());
			try { idx = indexOf(f.getSrcAddr()); } catch (Exception e) {};
			if (mesgSplit[0].equalsIgnoreCase("001")) code = 1;
			else if (mesgSplit[0].equalsIgnoreCase("010")) code = 2;
			else if (mesgSplit[0].equalsIgnoreCase("011")) code = 3;
			else if (mesgSplit[0].equalsIgnoreCase("100")) code = 4;
			
			switch (code)        
			{
				case 1 : //cari delta
				{ 
					long t1 = Long.parseLong(mesgSplit[1]);
					long t2 = Long.parseLong(mesgSplit[2]);
					long t3 = Long.parseLong(mesgSplit[3]);
					long RTT = t4-t1-(t3-t2); 
					// CS
					rttTABLE[idx] = rttTABLE[idx] + RTT;
					reply = "#HELLO: " + hex_addr + " " + stringFormatTime.SFFull(t3) + " | RTT: " + RTT + "#";
					try {
						out.write(reply.getBytes(), 0, reply.length());
					} catch (IOException e) { e.printStackTrace(); }
					break;
				}
				case 2 	: //suruh nge set
				{ 	// format pesan reply : 010 t2 t3 (after set)
					long t3 = Long.parseLong(mesgSplit[3]);
					reply = "#SET:" + hex_addr + " change to " + stringFormatTime.SFFull(t3) + "#";
					try {
						out.write(reply.getBytes(), 0, reply.length());
					} catch (IOException e) { e.printStackTrace(); }
					break;
				}
						
				case 3	: 
				{
					long t3 = Long.parseLong(mesgSplit[3]);
					reply = "#NOW: " + stringFormatTime.SFFull(t3) + " at " + hex_addr + "#";
					try {
						out.write(reply.getBytes(), 0, reply.length());
					} catch (IOException e) { e.printStackTrace(); }
					break;
				}
				case 4	: 
				{	
					break;
				}
			}
		}
	}
	
	public void sendTONODE(final FrameIO fio, final String mesg, final Integer sn, final long destADDR) throws Exception
	{ 
		try {
			Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
					| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16); 
			frame.setSrcAddr(NODE_ADDR[0]);
			frame.setSrcPanId(COMMON_PANID);
			frame.setDestAddr(destADDR); 
			frame.setDestPanId(COMMON_PANID);
			frame.setSequenceNumber(sn); 
			// format pesan : 010 deltaDelay t1
			String message = mesg + " " + Time.currentTimeMillis(); // + t1 : transmit Time;
			frame.setPayload(message.getBytes());
			radio.setState(AT86RF231.STATE_TX_ARET_ON);
			fio.transmit(frame); 
		} catch (RadioDriverException e) {
			//e.printStackTrace();
		} catch (NoAckException e) {
			//e.printStackTrace();
		} catch (ChannelBusyException e) {
			//e.printStackTrace();
		} catch (IOException e) {
		}
	}

	public void recvReply(final FrameIO fio, final long t0) throws Exception
	{
		new Thread () {
			@Override
			final Lock lock = new ReentrantLock();  
			
			public void run()
			{
				//String levRSSI;
				try { out = usart.getOutputStream(); } catch (Exception e) { e.printStackTrace();}
				Frame frame = new Frame();
				int idx=-1;
				int count=0; Integer prevSN = -1;
				while ( (count < statusBC.length) && ( (Time.currentTimeMillis()  - t0) <= 1000))
				{
					try {
						// receive a frame
						radio.setState(AT86RF231.STATE_RX_AACK_ON);  
						fio.receive(frame); 
						long t4 = Time.currentTimeMillis();
						idx = indexOf(frame.getSrcAddr());
						if (statusBC[idx] == 0)
						{
							statusBC[idx] = 1;
							if (frame.getSequenceNumber() != prevSN) dispatch(frame,t4);	
							count++;
						}
					} catch (Exception e) { e.printStackTrace();}
					prevSN = frame.getSequenceNumber();
				}
				/*
				lock.lock();
				try {
					finrecvReply = true;
					actSensor = count;
				} finally { lock.unlock(); } */
			}
		}.start();
	}
	
	// SENSE in this node
	public void goSense() throws Exception
	{
		new Thread() 
		{
			public void run()
			{
				// initialisasi accelerometer
				final Lock lock = new ReentrantLock();
				try { initACCL(); out = usart.getOutputStream(); } catch (Exception e) { e.printStackTrace();}		
				short[] valAccl = new short[3];
				String valStr = null;
				int i=1;
				while (true) {
					try { 			
						long getT = Time.currentTimeMillis(); acclSensor.getValuesRaw(valAccl, 0);
						valStr = Integer.toHexString(myADDR) + " " + stringFormatTime.SFFull(getT) + " " + 
										Arrays.toString(valAccl);
						
						String mesgRecv = "#SENSE" + " " + i + " " + valStr + " " + "\n" + "#";
						lock.lock();
						try { //usart.write(mesgRecv.getBytes()); 
							out.write(mesgRecv.getBytes());
							out.flush();
						//} catch (USARTException e) { e.printStackTrace();
						} catch (IOException e) { e.printStackTrace(); 
						} finally { lock.unlock();}
						i++;
						if (i==60000) {
							i=1;
							try { Thread.sleep(500); } catch (Exception e) { e.printStackTrace();}
						}
					} catch (SPIException e) { e.printStackTrace();
					} catch (GPIOException e) { e.printStackTrace();
					} catch (Exception e) {};
				}
			} // end of public void run()
		}.start();
	}
	
	public void recvSense(final FrameIO fio) 
	{
		new Thread() 
		{
			public void run()
			{
				final Lock lock = new ReentrantLock();
				try { useUSART(); out = usart.getOutputStream(); } catch (Exception e) { e.printStackTrace();}
				Frame frame = null; 
				frame = new Frame();
				//boolean stop = false;
				Integer prevSN = -1;
				while (!stop) {
					try {
						radio.setState(AT86RF231.STATE_RX_AACK_ON); 
						fio.receive(frame);
					} catch ( RadioDriverException e) { e.printStackTrace(); 
					} catch ( Exception e) { e.printStackTrace();}
 			
					// bagaimana kalau bagian ini disimpan dulu di flash, jika < 1000 * 1024 byte
					// tapi ini hanya menangani bagaimana ke PC
					
					
					if ((frame!=null) && (frame.getSequenceNumber() !=prevSN)) {
						try {
							byte[] dg = frame.getPayload(); 
							String mesgRecv = (new String(dg, 0, dg.length) + "\n");
							mesgRecv = "#" + mesgRecv + "#";
							lock.lock();
							try { 
								out.write(mesgRecv.getBytes()); 
								out.flush();
								//} catch (USARTException e) { e.printStackTrace();
							} finally { lock.unlock();}
						} catch (IOException e) { e.printStackTrace();
						} catch (NegativeArraySizeException e) { e.printStackTrace();
						} catch (StringIndexOutOfBoundsException e) { e.printStackTrace();
						};
						prevSN = frame.getSequenceNumber();
					}
					
				}
			}
		}.start();
	}
	
	public void sayHello() throws Exception
	{ // void for this node say hello to all other nodes
		long t0;
		String message = "001" + " " + (t0 = Time.currentTimeMillis());
		broadCast(message,SN++);
		recvReply(fio,t0);
	}
	
	public void setYourTime(int hi) throws Exception
	{	
		// Format pesan: 010 deltaDelay
		long t0 = Time.currentTimeMillis();
		int snThis = SN++;
		recvReply(fio, t0);
		for (int i=1; i < NODE_ADDRi.length; i++) 
		{
			
			rttTABLE[i] = (rttTABLE[i]/hi);
			String message = "010" + " " + rttTABLE[i]/2;
			sendTONODE(fio, message, snThis, NODE_ADDR[i]); 
		}
	}
	
	public void tellYourTime() throws Exception
	{
		long t0 = Time.currentTimeMillis();
	//	initFrameIO();
		String message = "011" + " " + t0;
		broadCast(message, SN++);
		recvReply(fio, t0);
	}

	public void goSenseNOW() throws Exception
	{ // untuk menyuruh sensor lakukan sensing 
		String message = "100" + " " + Time.currentTimeMillis() + " " + Time.currentTimeMillis();
		broadCast(message, SN++);
		recvSense(fio);
	}
	
	public void goSenseHumTemp() throws Exception
	{
		String message = "101" + " " + Time.currentTimeMillis() + " " + Time.currentTimeMillis();
		broadCast(message, SN++);
		recvSense(fio);
	}
		
	
	public void goSenseTempHumBaroBS() throws Exception
	{
		senseTempHumBaroLocal();
	}
	
	public void goSTOP() throws Exception
	{
		System.out.println("goSTOP");
		String message = "111" + " " + Time.currentTimeMillis();
		broadCast(message, SN++);
		stop = true;
	}
	
	public static void main(String [] args ) throws Exception 
	{ 		
		progBS bs = new progBS();
		
		int[] menuChoice	= {0,0,0,0,0,0,0};
		bs.initRadio();	
		bs.initrttTABLE(); bs.initFrameIO();
		bs.resetSN();
		try { bs.useUSART(); } catch (Exception e) { e.printStackTrace();}
		int choice;
		int hi = 0;
		// Synchronize with host have been done
		// time di adjust
		Time.setCurrentTimeMillis(Time.currentTimeMillis() + bs.hour7);
		do 
	    {
	    	choice = bs.usart.read();
	    	//choice = 6;
	    	switch (choice) {
				case 0: 
				{ // STOP SENSE and restart
					bs.goSTOP();
					break;
				}
				
	    		case 1: // Hi all & get Delta Delay // dan menghitung delay
	    		{
	    			menuChoice[choice] = menuChoice[choice] + 1;
	    			bs.resetStatusBC();
	    			bs.sayHello(); 
	    			hi++;
	    			break; 
	    		}
	    			/*
	    		for (int i=1; i< bs.NODE_ADDRi.length; i++) {
	    			System.out.println("RTT per node 	: " + bs.rttTABLE[i]);
	    		} */
	    		case 2: // Synchronize time to all sensor
	    		{
	    			menuChoice[choice] = menuChoice[choice] + 1;
	    			if ((hi > 0) && (menuChoice[1] > 0))
	    			{
	    				bs.resetStatusBC();
	    				bs.setYourTime(hi);
	    			}
	    			break;
	    		}
	    		case 3: // Please tell your time // tanpa menghitung delay
	    		{	
	    			menuChoice[choice] = menuChoice[choice] + 1;
	    			bs.resetStatusBC();
	    			bs.tellYourTime();
	    			break;
	    		}
	    		case 4:
	    			// GO SENSE and TRANSMIT TO BS
	    		{	// pilihan ini dilakukan hanya jika pilihan 5 tidak dilakukan
	    			if ((menuChoice[2] > 0) && (menuChoice[5] == 0)) {
	    				bs.goSenseNOW();
	    			//	bs.goSense();
	    			}
	    			break;
	    		}
	    		
	    		case 5:                                     
	    		{   // pilihan ini dilakukan hanya jika pilihan 4 tidak dilakukan
	    			if ((menuChoice[2] > 0) && (menuChoice[4] == 0)) {
	    				bs.goSenseHumTemp(); 
	    			}
	    			break;
	    		}
	    		case 6:
	    		{ // pilihan ini untuk melakukan sense humidity, temperature dan tekanan udara di BS saja
	    			if ((menuChoice[6]==0)) {
	    				//bs.goSenseTempHumBaroBS();
	    				bs.senseTempHumBaroLocal();
	    			}
	    		}
	    		default:
	    			// The user input an unexpected choice.
	    			break;
	    	}
	    } while (choice !=0);
	}
}