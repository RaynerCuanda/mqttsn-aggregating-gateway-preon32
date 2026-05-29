import java.util.HashMap;
import java.lang.String;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.device.at86rf231.AT86RF231RadioDriver;
import com.virtenio.preon32.node.Node;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;

public class NodeSensor {
	private final int COMMON_PANID = 0xCAFE; // Personal Area Network ID
	private int BASESTATION_ADDR; // Alamat Base station (Disii setelah dapat GWINFO)
	private final int BROADCAST_ADDRESS = 0xFFFF; //Alamat Broadcast

	// Konfigurasi untuk setiap node sensor
	private int nodeNumber = 3; // 1 digunakan untuk alamat Gateway
	private int tipeTopic = MQTTSNPacket.PREDEFINED_TOPIC_ID;
	private String tempTopic ="5"; // 9017/Temperature
	private String airTopic = "6"; // 9017/AirPressure
	private String humTopic = "7"; // 9017/Humidity
	private String vibTopic = "8"; // 9017/Vibration
	private int publish_qos = 2;
	
	private final int localAddress = 0x0000 + nodeNumber; // ALAMAT NODE SENSOR
	private final String NODE_SENSOR_ID = "node_"+nodeNumber; // IDENTITAS NODE SENSOR
	 
	private int keepAliveTime = MQTTSNPacket.KEEP_ALIVE_TIME; 
	private boolean isConnected; // is connected to Gateway
	private long gwLastReceive; // Last time receiving message from Gateway
	private long registerSentTime; 
	private int counter = 0;
	private int currentRegisterId = 0; // ongoing Register Id
	private int messageIdCounter = 1; // Register Id counter
	private HashMap<Integer, MQTTSNPacket> RegAckHashMap = new HashMap<>(); //
	private HashMap<String, Integer> topicMap = new HashMap<>(); //
	
	private PublishHelper currentOngoingQoS; // MQTTSNPacket, timeSent, retry count ~ untuk keep track dari kapan terakhir publish qos 1/2 di kirim
	private AT86RF231 radio;
	private FrameIO fio;
	private Preon32Sensor sensor;
	
	
	public static void main(String [] args ) throws Exception{
		new NodeSensor().run(); // Opsi Normal Client
		//  new NodeSensor().runVerySimpleClient(); // Opsi Very Simple Client (QoS -1)
	}

	public void run() throws Exception{
		sensor = new Preon32Sensor();
		sensor.init();
		setupRadio();
		runRadioReceiver();

		while(true) {
			if (BASESTATION_ADDR == 0x00){ //Kalo belum tau alamat GW, broadcast SEARCHGW ke setiap gateway
				MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
				mqttsnPacket.setSEARCHGW(0x00); //The value 0x00 means “broadcast to all nodes in the network”. 
				send(mqttsnPacket, BROADCAST_ADDRESS);
				printLogging("SEND to", "0", "SEARCHGW: Broadcasting to find Gateway address");
				Thread.sleep(5000);
			} else if (!isConnected){ // Jika address udah ada, buat koneksi dengan Gateway.
				if(!isGatewayTimeout()){
					MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
					mqttsnPacket.setCONNECT(NODE_SENSOR_ID, true, true, keepAliveTime);
					send(mqttsnPacket, BASESTATION_ADDR);
					printLogging("SEND to", ""+BASESTATION_ADDR, "CONNECT: Connecting to Gateway..");
					Thread.sleep(5000);
				}
			} else if (isConnected){ //Jika sudah konek, maka akan selalu sense, terus publish
				if(!isGatewayTimeout()){
					isQoSTimeOut();
					System.out.println("Batch number-:"+counter);
					counter++;
					sendPublish(tempTopic, tipeTopic, sensor.getTemperatureValue(), publish_qos);
					Thread.sleep(3000);
					sendPublish(airTopic, tipeTopic, sensor.getPressureValue(), publish_qos);
					Thread.sleep(3000);
					sendPublish(humTopic, tipeTopic, sensor.getHumidityValue(), publish_qos);
					Thread.sleep(3000);
					sendPublish(vibTopic, tipeTopic, sensor.getVibrationValue(), publish_qos);
					Thread.sleep(30000);
				}
			} 
		}
	}

	public void runVerySimpleClient() throws Exception{
		BASESTATION_ADDR = 0x0001;
		sensor = new Preon32Sensor();
		sensor.init();
		setupRadio();
		while(true) {
			
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setCONNECT(NODE_SENSOR_ID, true, true, keepAliveTime);
			send(mqttsnPacket, BASESTATION_ADDR);
			printLogging("SEND to", ""+BASESTATION_ADDR, "CONNECT: Connecting to Gateway..");

//			sendPublish("5", MQTTSNPacket.PREDEFINED_TOPIC_ID, sensor.getTemperatureValue(), publish_qos);
//			Thread.sleep(500);
//			sendPublish("6", MQTTSNPacket.PREDEFINED_TOPIC_ID,sensor.getHumidityValue(), publish_qos);
//			Thread.sleep(500);
//			sendPublish("7", MQTTSNPacket.PREDEFINED_TOPIC_ID,sensor.getPressureValue(), publish_qos);
//			Thread.sleep(500);
//			sendPublish("8", MQTTSNPacket.PREDEFINED_TOPIC_ID,sensor.getVibrationValue(), publish_qos);
			Thread.sleep(30000);
		}
	}

    private void setupRadio() {
        try {
            radio = Node.getInstance().getTransceiver();
            radio.open();
            radio.setAddressFilter(COMMON_PANID, localAddress, localAddress, false);
            RadioDriver radioDriver = new AT86RF231RadioDriver(radio);
            fio = new RadioDriverFrameIO(radioDriver);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runRadioReceiver() {
        new Thread() {
            public void run() {
                while (true) {
                    try {
                    	Frame frame = new Frame();
                        fio.receive(frame);
                        handleMessage(frame);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

	private void handleMessage(Frame frame){
		gwLastReceive = System.currentTimeMillis();
		int sender = (int) frame.getSrcAddr();
		MQTTSNPacket packet = new MQTTSNPacket();
		packet.toMQTTSN(frame.getPayload());

		switch(packet.getMsgType()){
			case MQTTSNPacket.ADVERTISE:{
				printLogging("RECEIVED from", ""+sender, "ADVERTISE: Getting Gateway address");
				if (BASESTATION_ADDR == 0x00){
					BASESTATION_ADDR =  sender;
				}
				if (BASESTATION_ADDR == sender){
					keepAliveTime = ((packet.getMsgVarPart()[1] & 0xFF) << 8) | (packet.getMsgVarPart()[2] & 0xFF);
				}
				break;
			}
			case MQTTSNPacket.GWINFO:{
				printLogging("RECEIVED from", ""+sender, "GWINFO: Getting Gateway address");
				BASESTATION_ADDR = (int)frame.getSrcAddr();
				break;
			}
			case MQTTSNPacket.CONNACK:{
				printLogging("RECEIVED from", ""+sender, "CONNACK: Accepted, Connection Established");
				handleCONNACK(packet);
				break;
			}
			case MQTTSNPacket.REGACK:{
				// logging di handleREGACK
				handleREGACK(packet);
				break;
			}
			case MQTTSNPacket.PUBACK:{
				// logging di handlePUBACK
				handlePubAck(packet);
				break;
			}
			case MQTTSNPacket.DISCONNECT:{
				printLogging("RECEIVED from", ""+sender, "DISCONNECT: Clearing session");
				isConnected = false; 
				BASESTATION_ADDR = 0x00;
				topicMap.clear();
				break;
			}
			case MQTTSNPacket.PUBREC:{
				if (currentOngoingQoS == null) break; // Sudah di acknowledge: skip aja
				
				int messageId = ((packet.getMsgVarPart()[0] & 0xFF) << 8) | (packet.getMsgVarPart()[1] & 0xFF);
				printLogging("RECEIVED from", ""+sender, "PUBREC: msgID: "+messageId);
				MQTTSNPacket res = new MQTTSNPacket();
				res.setPUBREL(messageId);
				send(res, BASESTATION_ADDR);
				printLogging("SEND to", ""+BASESTATION_ADDR, "PUBREL: msgID: "+messageId);

				currentOngoingQoS.mqttsnMessage = res;
				currentOngoingQoS.counter = 1;
				currentOngoingQoS.timeSent = System.currentTimeMillis();
				break;
			}
			case MQTTSNPacket.PUBCOMP:{
				int pubcompId = ((packet.getMsgVarPart()[0] & 0xFF) << 8) | (packet.getMsgVarPart()[1] & 0xFF);
				
				MQTTSNPacket pubrel = currentOngoingQoS.mqttsnMessage;
				int pubrelId =  ((pubrel.getMsgVarPart()[0] & 0xFF) << 8) | (pubrel.getMsgVarPart()[1] & 0xFF);

				if (pubrelId == pubcompId){
					currentOngoingQoS = null;
					printLogging("RECEIVED from", ""+sender, "PUBCOMP: msgID: "+pubcompId);
				} else {
					printLogging("RECEIVED from", ""+sender, "PUBCOMP: pubcompId: "+pubcompId+", pubrelId:"+pubrelId);
				}
				break;
			}
		}
	}

	private void send(MQTTSNPacket packet, int destinationAddresss){
		byte[] packetToSend = packet.toBytes();
        int frameControl = Frame.TYPE_DATA | Frame.DST_ADDR_16
                | Frame.INTRA_PAN | Frame.SRC_ADDR_16;

        final Frame testFrame = new Frame(frameControl);
        testFrame.setDestPanId(COMMON_PANID);
        testFrame.setSrcAddr(localAddress);
        testFrame.setDestAddr(destinationAddresss);
        testFrame.setPayload(packetToSend);

        try {
            fio.transmit(testFrame);
        } catch (Exception e) {
//            e.printStackTrace();
        }
	}

	private void isQoSTimeOut(){ 
		// Kalo Belum ada yang di publish, langsung return
		if (currentOngoingQoS == null){ 
			return;
		}

		// Kalo currentOngoingQoS udah melewati maximum retry, timeout
		if (currentOngoingQoS.counter > MQTTSNPacket.MAX_PUBACK_RETRY){
			printLogging("INFO", "", "QoS message reached maximum retry, dropping message");
			currentOngoingQoS = null;
			return;
		}

		// send ulang kalo, udah melewati waktu timeout dan masih dibawah maximum retry 
		if ((System.currentTimeMillis() - currentOngoingQoS.timeSent) / 1000 > MQTTSNPacket.PUBACK_TIMEOUT){
			MQTTSNPacket packet = currentOngoingQoS.mqttsnMessage;	
			
			//bit modification supaya dup jadi true (untuk pesan PUBLISH)
			if(packet.getMsgType() == (MQTTSNPacket.PUBLISH & 0xFF)){
				byte flagsWithDup = (byte) (packet.getMsgVarPart()[0] | 0x80); //Dup jadi true (bit ke terujung)
				currentOngoingQoS.mqttsnMessage.getMsgVarPart()[0] = flagsWithDup;
				printLogging("RESEND to", ""+BASESTATION_ADDR, "PUBLISH: Timeout");
			} else {
				printLogging("RESEND to", ""+BASESTATION_ADDR, "PUBREL: Timeout");
			}
			send(currentOngoingQoS.mqttsnMessage, BASESTATION_ADDR);
			currentOngoingQoS.timeSent = System.currentTimeMillis();
			currentOngoingQoS.counter++;
		}
	}
	
	private void handleCONNACK(MQTTSNPacket packet){
		int returnCode = packet.getMsgVarPart()[0];
		switch (returnCode){
			case 0x00:
				this.isConnected = true;
				break;
			case 0x01:
				this.isConnected = false;
				break;
			case 0x02:
				this.isConnected = false;
				break;
			case 0x03:
				this.isConnected = false;
				break;
		}
	}

	private void handleREGACK(MQTTSNPacket mqttsnPacket){
		int topicId = ((mqttsnPacket.getMsgVarPart()[0] & 0xFF) << 8) | (mqttsnPacket.getMsgVarPart()[1] & 0xFF);
		int messageId = ((mqttsnPacket.getMsgVarPart()[2] & 0xFF) << 8) | (mqttsnPacket.getMsgVarPart()[3] & 0xFF); 
		if (messageId != currentRegisterId){ // messageID yang sebelumnya udah di hapus, jadi ga usah di proses karena udah ga ada di Map.
			return;
		}
		printLogging("RECEIVE from", ""+BASESTATION_ADDR, "REGACK: topic ID: "+topicId +", message ID: "+messageId);
		int returnCode = mqttsnPacket.getMsgVarPart()[4];
		switch (returnCode){
			case 0x00:
				byte[] oldPacket = RegAckHashMap.remove(messageId).getMsgVarPart();
				byte[] topic_name = new byte[oldPacket.length-4];
				System.arraycopy(oldPacket,  4, topic_name, 0, oldPacket.length-4); // Copy topic name
				String topicNameStr = new String(topic_name);

				topicMap.put(topicNameStr, topicId);
				break;
			case 0x01:
				System.out.println("Gateway REJECTED: CONGESTION");
				RegAckHashMap.remove(messageId);
				break;
			case 0x02:
				System.out.println("Gateway REJECTED: INVALID TOPIC ID");
				RegAckHashMap.remove(messageId);
				break;
			case 0x03:
				System.out.println("Gateway REJECTED: not SUPPORTED");
				RegAckHashMap.remove(messageId);
				break;
			}
		currentRegisterId = 0;
	}

	private void handlePubAck(MQTTSNPacket mqttsnPacket){
		
		int pubackId = ((mqttsnPacket.getMsgVarPart()[2] & 0xFF) << 8) | (mqttsnPacket.getMsgVarPart()[3] & 0xFF);
		int returnCode = mqttsnPacket.getMsgVarPart()[4];
		switch(returnCode){
			case 0x00: { //  untuk QoS 1
				if (currentOngoingQoS == null){
					break; // Pesan QoS sudah diacknowledge
				} 
				if (pubackId == 0) {
					printLogging("RECEIVE from", ""+BASESTATION_ADDR, "PUBACK: Error, ACCEPTED Puback with messageId 0");
					break;
				}

				byte[] publishVar = currentOngoingQoS.mqttsnMessage.getMsgVarPart();
				int publishId = ((publishVar[3] & 0xFF) << 8) | (publishVar[4] & 0xFF);
				if (pubackId == publishId){
					printLogging("RECEIVE from", ""+BASESTATION_ADDR, "PUBACK: Accepted, ready for next QoS publish");
					currentOngoingQoS = null;
				} else{
					printLogging("RECEIVE from", ""+BASESTATION_ADDR, "PUBACK: Rejected, invalid message id");
				}
				break;
			}
			case 0x01:{
				printLogging("INFO", "", "Gateway Rejected: CONGESTION");
				if (pubackId == 0) break; // Pesan dengan QoS 0

				if (currentOngoingQoS == null) {
					break;
				} 

				if (currentOngoingQoS.counter > MQTTSNPacket.MAX_PUBACK_RETRY){
					printLogging("INFO", "", "QoS message reached maximum retry, dropping message");
					currentOngoingQoS = null;
				} else {
					if (mqttsnPacket.getMsgType() == (MQTTSNPacket.PUBLISH & 0xFF)){
						//bit modification supaya dup jadi true (khusus PUBLISH)
						byte flagsWithDup = (byte) (currentOngoingQoS.mqttsnMessage.getMsgVarPart()[0] | 0x80);
						currentOngoingQoS.mqttsnMessage.getMsgVarPart()[0] = flagsWithDup;
						printLogging("RESEND to", ""+BASESTATION_ADDR, "PUBLISH: Timeout");
					} else{
						printLogging("RESEND to", ""+BASESTATION_ADDR, "PUBREL: Timeout");

					}
					send(currentOngoingQoS.mqttsnMessage, BASESTATION_ADDR);
					currentOngoingQoS.timeSent = System.currentTimeMillis();
					currentOngoingQoS.counter++;
				}
				break;
			}
			case 0x02:{ // Invalid Topic : Qos 0, 1, 2 
				System.out.println("Gateway Rejected: Invalid Topic ID");
				// Clearing current topic ID
				int topicId = ((mqttsnPacket.getMsgVarPart()[0] & 0xFF) << 8) | (mqttsnPacket.getMsgVarPart()[1] & 0xFF);
				topicMap.values().remove(topicId);
				if (pubackId != 0){
					byte[] publishVar = currentOngoingQoS.mqttsnMessage.getMsgVarPart();
					int publishId = ((publishVar[3] & 0xFF) << 8) | (publishVar[4] & 0xFF);
					if (pubackId == publishId) {
						currentOngoingQoS = null;
					}
				}
				break;
			}
			default:
				System.out.println("Returncode Invalid");
		}
	}

	private boolean isGatewayTimeout(){
		long time_since_receive_from_gw = ((System.currentTimeMillis() - gwLastReceive) / 1000);
		if (time_since_receive_from_gw > keepAliveTime){
			isConnected = false; 
			BASESTATION_ADDR = (byte) 0x00;
			topicMap.clear();
			printLogging("INFO", "", "Connection Time out! "+time_since_receive_from_gw+" seconds since receiving something from GW");
			return true;
		}
		return false;
	}

	private void sendPublish(String topicName, int topicIdType, String payload, int qos){
		topicName = topicName.trim();

		if (qos != -1 && !isConnected) return; // Kalo koneksi tiba' terputus langsung return
		if (topicName.length() == 0){
			printLogging("ERROR", "", "topicName length 0");
			return;
		}

		if (qos > 0 && currentOngoingQoS != null){ // Kalo pesan QoS, tetapi sudah ada yang dikirim
			printLogging("INFO", "", "Failed Publishing :" + topicName + ". There's currently ongoing QoS");
			return;
		} 
		
		Integer topicId = topicMap.get(topicName); // Kalo belum ada mapping, register dulu
		if (topicId == null && topicIdType == MQTTSNPacket.NORMAL_TOPIC_ID){
			sendRegister(topicName);
			return;
		}

		int messageId = (qos > 0) ? messageIdCounter : 0; // qos -1, 0 (messageID = 0), qos 1,2
		MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
		
		switch (topicIdType){
			case MQTTSNPacket.NORMAL_TOPIC_ID:{
				mqttsnPacket.setPUBLISH(false, qos, true, MQTTSNPacket.NORMAL_TOPIC_ID, topicId, messageId, payload);
				printLogging("SEND to", ""+BASESTATION_ADDR, "PUBLISH with normal topic name: "+topicName+", payload: "+payload+", qos: "+qos);
				break;
			}
			case MQTTSNPacket.PREDEFINED_TOPIC_ID:{
				topicId = Integer.parseInt(topicName);
				mqttsnPacket.setPUBLISH(false, qos, true, MQTTSNPacket.PREDEFINED_TOPIC_ID, topicId, messageId, payload);
				printLogging("SEND to", ""+BASESTATION_ADDR, "PUBLISH with pre-defined topic id: "+topicName+", payload: "+payload+", qos: "+qos);
				break;
			}
			case MQTTSNPacket.SHORT_TOPIC_NAME:{
				mqttsnPacket.setPUBLISH(false, qos, true, MQTTSNPacket.SHORT_TOPIC_NAME, topicName, messageId, payload);
				printLogging("SEND to", ""+BASESTATION_ADDR, "PUBLISH with short topic name: "+topicName+", payload: "+payload+", qos: "+qos);
				break;
			}
			default:{
				printLogging("ERROR", "", "Invalid topic Id Type: "+topicIdType);
				return;
			}
		}

		//Untuk simpan QoS 
		if (qos > 0){
			currentOngoingQoS = new PublishHelper(mqttsnPacket, System.currentTimeMillis());
			increaseMessageIdCounter();
		} 
		
		send(mqttsnPacket, BASESTATION_ADDR);
	}

	private void sendRegister(String topicName){
		if (currentRegisterId == 0){ // Kalo belum ada paket yang sedang di daftarkan
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setREGISTER(0, messageIdCounter, topicName); //topicId pasti 0, kalau di kirim Client (Node sensor) 
			RegAckHashMap.put(messageIdCounter, mqttsnPacket);
			currentRegisterId = messageIdCounter;

			send(mqttsnPacket, BASESTATION_ADDR);
			registerSentTime = System.currentTimeMillis();
			printLogging("SEND to", ""+BASESTATION_ADDR, "REGISTER: topic name: "+topicName+", msgId: "+messageIdCounter);

			increaseMessageIdCounter();
		} else{ // Kalo udah ada yang di daftar, coba cek timeout. Kalo time out, hapus dari regack map 
			if ((System.currentTimeMillis() - registerSentTime) / 1000 > MQTTSNPacket.REGISTER_TIMEOUT){
				printLogging("INFO", "", "REGISTER timeout: no REGACK received");
				RegAckHashMap.remove(currentRegisterId);
				currentRegisterId = 0;
			}
		}
	}

	// MessageID maximum 65535 (2 byte)
	private void increaseMessageIdCounter(){
        messageIdCounter++;
        if (this.messageIdCounter > 65535){
                this.messageIdCounter = 1;
        } 
    }
	

	private void printLogging(String description, String address, String message) {
		System.out.println("[" + description + ": " + address + "] " + message);
	}
}

