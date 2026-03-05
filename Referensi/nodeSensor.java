import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.Lock;

import com.virtenio.driver.device.ADXL345;
import com.virtenio.driver.gpio.GPIO;
import com.virtenio.driver.gpio.GPIOException;
import com.virtenio.driver.gpio.NativeGPIO;
import com.virtenio.driver.spi.NativeSPI;
import com.virtenio.driver.spi.SPIException;
import com.virtenio.driver.usart.USART;
import com.virtenio.io.ChannelBusyException;
import com.virtenio.io.NoAckException;
import com.virtenio.misc.StringUtils;
import com.virtenio.driver.flash.Flash;
import com.virtenio.flashlogger.FastFlashLogger;
import com.virtenio.flashlogger.DataSet;
import com.virtenio.preon32.examples.common.RadioInit;
import com.virtenio.preon32.shuttle.Shuttle;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;
import com.virtenio.radio.RadioDriverException;

import com.virtenio.driver.device.at86rf231.*;
import com.virtenio.driver.device.at86rf231.AT86RF231RadioDriver;
import com.virtenio.driver.led.LED;

import com.virtenio.vm.Time;

public class nodeSensor {
	private int COMMON_CHANNEL = 24;
	private int COMMON_PANID = 0xCAFE;
	//	  			  	  { 0			1			2			3			4			5		}
	//						{ 0	6		1	7		2	8		3	9		4	10		5	11	}
	//int[] NODE_ADDR 	= { 0xBABE, 	0xAFFE, 	0xBAFE, 	0xBEBA,		0xAFAF, 	0xEFEF }; 
	int[] NODE_ADDR 	= { 0xBABE, 	0xAFFE, 	0xBAFE, 	0xBEBA, 	0xAFAF, 	0xEFEF, 
							0xBAB1, 	0xAFF1, 	0xBAF1, 	0xBEB1, 	0xAFA1, 	0xEFE1 }; 
	
	String[] NODE_ADDRs = { "0xBABE", 	"0xAFFE", 	"0xBAFE", 	"0xBEBA", 	"0xAFAF", 	"0xEFEF" };

	// DAFTAR COM		  {  COM3		COM5		COM4/12		COM6/13	   	COM7/14		COM 8/11 	}
	
	int nodeKE = 4;
	int myADDR = NODE_ADDR[nodeKE];
	
	private LED yellow, green, red, amber;
	AT86RF231 radio;
	FrameIO fio;
	Shuttle shuttle;
	USART usart;
	private ADXL345 acclSensor;
	private GPIO accelCs;
	Flash flash;
	DataSet dataSet;
	FastFlashLogger ffLogger;
	
	long hour7 = 25200000;
	Integer SN;
	
	private volatile boolean exit = false;
	
	public void resetSN() throws Exception {
		SN = 0;
	}
	
	public void initRadio() throws Exception {
		radio = RadioInit.initRadio();
		radio.setChannel(COMMON_CHANNEL);
		radio.setPANId(COMMON_PANID);
		radio.setShortAddress(myADDR);
	}
	
	public void initFrameIO() throws Exception {
		final RadioDriver radioDriver = new AT86RF231RadioDriver(radio);
		fio = new RadioDriverFrameIO(radioDriver);	
	}
	
	public void initLED() throws Exception
	{
		shuttle = Shuttle.getInstance();

		green = shuttle.getLED(Shuttle.LED_GREEN);
		green.open();
		
		yellow = shuttle.getLED(Shuttle.LED_YELLOW);
		yellow.open();
		
		red = shuttle.getLED(Shuttle.LED_RED);
		red.open();
	
		amber = shuttle.getLED(Shuttle.LED_AMBER);
		amber.open();
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
		acclSensor.setDataFormat(ADXL345.DATA_FORMAT_RANGE_16G); 
		acclSensor.setDataRate(ADXL345.DATA_RATE_100HZ);
		acclSensor.setPowerControl(ADXL345.POWER_CONTROL_MEASURE);
	}
	
	public void sendFrame(final FrameIO fio, String mesg, Integer sn) throws InterruptedException
	{
		boolean isOK = false;
		while (!isOK)
		{
			try {
				String message = "SENSE" + " " + mesg;
				Frame frame = new Frame(Frame.TYPE_DATA | Frame.ACK_REQUEST
					| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16);
							
				frame.setSrcAddr(myADDR);
				frame.setSrcPanId(COMMON_PANID);
				frame.setDestAddr(NODE_ADDR[0]);
				frame.setDestPanId(COMMON_PANID);
				radio.setState(AT86RF231.STATE_TX_ARET_ON);
				frame.setPayload(message.getBytes());
				frame.setSequenceNumber(sn);
				fio.transmit(frame);
				isOK=true; 
			} catch (RadioDriverException e) { e.printStackTrace();
			} catch (NoAckException e) { 
			} catch (ChannelBusyException e) { 
			} catch (IOException e) {
			}
		}
	}
		
	// Sensing and transmit 
	public void goSense() throws Exception
	{
		new Thread() { 
			public void run() 
			{
				// initialisasi accelerometer
				try { initACCL();} catch (Exception e) { e.printStackTrace();}		
				short[] valAccl = new short[3];
				String valStr;
				Integer i=1;
				while (!exit) {
					try { 
						long getT = Time.currentTimeMillis(); acclSensor.getValuesRaw(valAccl, 0);
		
						valStr = i + " " + Integer.toHexString(myADDR) + " " + stringFormatTime.SFFull(getT) + " " + 
								Arrays.toString(valAccl);
						
						sendFrame(fio, valStr, SN++);
						i++; 
						if (i==60000) {
							i=1;
							try { Thread.sleep(500); } catch (Exception e) { e.printStackTrace();}
						}
					} catch (SPIException e) { e.printStackTrace();
					} catch (GPIOException e) { e.printStackTrace();
					} catch (InterruptedException e) {};
				}
				System.out.println("Keluar goSense");
			}
		}.start();
	}
			
	public void getHumidityTemperature() throws Exception 
	{
		new Thread()
		{
			
			Humidity hum = new Humidity(); 
			public void run() {
				final Lock lock = new ReentrantLock();
				lock.lock(); 
				try {
				while (!exit) 
				{
					try {
						String mesg = Integer.toHexString(myADDR) + " " + hum.get();
						sendFrame(fio, mesg, SN++);
					} catch (Exception e) {
						System.out.println("SHT21 error");
					}
				}
				} finally { lock.unlock();}
				System.out.println("Keluar getHumidityTemperature()");
			}
		}.start();
	}
	

	private void startTransmitter (final FrameIO fio, final String mesg, final int sn, final long destADDR) 
	{
		new Thread() {
			@Override
			public void run() 
			{
				boolean isOK = false;
				while (!isOK)
				{
					try {
						int frameControl = Frame.TYPE_DATA | Frame.ACK_REQUEST
							| Frame.DST_ADDR_16 | Frame.INTRA_PAN | Frame.SRC_ADDR_16;
						final Frame frame = new Frame(frameControl);
						frame.setSrcAddr(myADDR);
						frame.setSrcPanId(COMMON_PANID);
						frame.setDestAddr(destADDR);
						frame.setDestPanId(COMMON_PANID);
						frame.setSequenceNumber(sn);
						String message = mesg + " " + Time.currentTimeMillis(); // + t3: transmit Time;
						frame.setPayload(message.getBytes());
						radio.setState(AT86RF231.STATE_TX_ARET_ON);
						fio.transmit(frame); System.out.println("transmitted");
						isOK = true;
					} catch (Exception e) { e.printStackTrace();}
				}
				
				
			}
		}.start();
	}

	public void processHELLO(final String mesgSplit[], final long destADDR, final long t2) throws Exception
	{	
		// after frame received, this node process it and transmit frame for reply
		long t1 = Long.parseLong(mesgSplit[2]); // time from CH = transmit time from BS
		String message = "001" + " " + t1 + " " + t2; 
		startTransmitter(fio, message, SN++, destADDR);
	}
	
	public void processSetTimeNOW(String mesgSplit[], long destADDR, long t2) throws Exception
	{ // format pesan: 010 deltaDelay t1
		// set Time
		long deltat3t2;
		Time.setCurrentTimeMillis(
				Long.parseLong(mesgSplit[2]) + 
				Long.parseLong(mesgSplit[1]) + ( deltat3t2 = Time.currentTimeMillis() - t2) );

		// format pesan reply : 010 t2 t3 (time after set)
		String message = "010" + " " + deltat3t2 + " " + (Time.currentTimeMillis());
		System.out.println(stringFormatTime.SFFull(Time.currentTimeMillis()));
		startTransmitter(fio, message, SN++, destADDR); 
	}
	
	public void processGetTimeNOW(String mesgSplit[], long destADDR, long t2) throws Exception
	{ // untuk memproses Get time NOW
	  // <KodePesan>space<Pesan1>space<Pesan2> = 01 t1 t1
	  // 01 t2 t1
		long t1 = Long.parseLong(mesgSplit[2]);
		String message = "011" + " " + t1 + " " + t2;
		startTransmitter(fio, message, SN++, destADDR);
	}
	
	public void dispatch (final Frame f, final long t2) throws Exception
	{
		new Thread () 
		{
			final Lock lock = new ReentrantLock();
			public void run()
			{
				int code=-1;
				if (f!=null) 
				{
					try {
						byte[] dg = f.getPayload(); 
						String mesgRecv = new String(dg, 0, dg.length);
						String mesgSplit[] = StringUtils.split(mesgRecv, " ");
				
						if (mesgSplit[0].equalsIgnoreCase("001")) code = 1;
						else if (mesgSplit[0].equalsIgnoreCase("010")) code = 2;
						else if (mesgSplit[0].equalsIgnoreCase("011")) code = 3;
						else if (mesgSplit[0].equalsIgnoreCase("100")) code = 4;
						else if (mesgSplit[0].equalsIgnoreCase("101")) code = 5;
						else if (mesgSplit[0].equalsIgnoreCase("111")) code = 8;
						switch (code) {
							case 1 : { processHELLO(mesgSplit, f.getSrcAddr(), t2); break; }
							case 2 : { processSetTimeNOW(mesgSplit, f.getSrcAddr(),t2); break; }
							case 3 : { processGetTimeNOW(mesgSplit, f.getSrcAddr(), t2); break; }
							case 4 : { goSense(); break; }
							case 5 : { getHumidityTemperature(); break; }
							case 8 : { System.out.println("stop"); 
									   lock.lock(); try { exit = true; } finally { lock.unlock();}
									   break; }
						}
					} catch (Exception e) { e.printStackTrace();}
				}
			}
		}.start();
	}
	
	public void run() 
	{
		try {
			initLED();
			initRadio(); 
			initFrameIO();
			resetSN();
			red.on();
			// start receiver
			startReceiver(fio);
		} catch (Exception e) { e.printStackTrace(); }
	}
	
	public void startReceiver(final FrameIO fio) 
	{
		Frame frame = null; 
		frame = new Frame();
		boolean stop = false;
		Integer prevSN = -1;
		while (!stop) 
		{
			System.out.println("ready");
			try {
				// receive a frame
				radio.setState(AT86RF231.STATE_RX_AACK_ON); 
				fio.receive(frame); 
				long t2 = Time.currentTimeMillis(); 
				System.out.println("received");
				if (frame.getSequenceNumber() != prevSN) dispatch (frame,t2);
			} catch (Exception e) { e.printStackTrace();}
			prevSN = frame.getSequenceNumber();
		}
	}
		
	public static void main(String [] args ) throws Exception {
		new nodeSensor().run();
	}
}