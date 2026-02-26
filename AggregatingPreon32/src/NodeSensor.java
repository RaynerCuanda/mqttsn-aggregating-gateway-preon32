import java.io.IOException;

import com.virtenio.driver.device.ADT7410;
import com.virtenio.driver.device.ADXL345;
import com.virtenio.driver.device.SHT21;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.gpio.GPIO;
import com.virtenio.driver.i2c.NativeI2C;
import com.virtenio.preon32.examples.common.RadioInit;
import com.virtenio.vm.Time;

public class NodeSensor {
	private int COMMON_CHANNEL = 24; // channel
	private int COMMON_PANID = 0xCAFE; // Personal Area Network ID
	//private int BROADCAST_ADDR = 0xFFFF;
	private String NODE_SENSOR_ID = "node_1";
	private int BASESTATION_ADDR = 0x00; //Defaultnya 0 

	private int BROADCAST_ADDRESS = 0xFFFF;
	private int id_topic = 0x00;
	
	private ADXL345 accelerationSensor; 
	private ADT7410 suhu;
	private SHT21 sht21;
	private NativeI2C i2c;
	private GPIO accelIrqPin1;
	private GPIO accelIrqPin2;
	private GPIO accelCs;
	private AT86RF231 radio;
    
	private boolean isConnected = false;
	
	// public void initRadio() throws Exception {
	// 	radio = RadioInit.initRadio();
	// 	radio.setChannel(COMMON_CHANNEL);
	// 	radio.setPANId(COMMON_PANID);
	// 	radio.setShortAddress(BASESTATION_ADDR);
	// }
	
	// public void initSensor() throws Exception{
	// 	accelerationSensor.	
	// }

	public void send(MQTTSNPacket packet, int destinationAddresss){
		byte[] packetToSend = packet.toBytes();
		
		// ....
	}

	public void run() throws Exception{
		
		//Selama node sensor dan belum terhubung, maka akan melakukan koneksi dengan Base Station
		while(true) {
			if (BASESTATION_ADDR == 0x00) {
				MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
				mqttsnPacket.setSEARCHGW(0x00);
				send(mqttsnPacket, BROADCAST_ADDRESS);
			// Jika address udah ada, tinggal konek
			} else if (!isConnected){
				MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
				mqttsnPacket.setCONNECT(NODE_SENSOR_ID, true, true);
				send(mqttsnPacket, BASESTATION_ADDR);
			//Jika sudah konek, maka akan selalu sense, terus publish
			} else if (isConnected){
				// sense();
			}
		}
	}
	
	public void handleMessage(Frame frame){
		//BUAT NANTI MENERIMA ADVERTISE, GWINFO, CONNACK, PUBACK, REGACK
	}
	
	public static void main(String [] args ) throws Exception
	{
		NodeSensor ns = new NodeSensor();
		ns.run();
	}
	
	
}
