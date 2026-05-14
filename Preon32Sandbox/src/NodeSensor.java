import java.util.HashMap;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.device.at86rf231.AT86RF231RadioDriver;
import com.virtenio.preon32.node.Node;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;

public class NodeSensor {
	private int COMMON_PANID = 0xCAFE; // Personal Area Network ID
	private int BASESTATION_ADDR; // Alamat Base station (Disii setelah dapat GWINFO)
	private int BROADCAST_ADDRESS = 0xFFFF; //Alamat Broadcast

	// Configuration for each node sensor
	private final int localAddress = 0x0002; // ALAMAT NODE SENSOR
	private final String NODE_SENSOR_ID = "node_1"; // IDENTITAS NODE SENSOR
	private final String tempTopic ="9018/Temperature";
	private final String humTopic = "9018/Humidity";
	private final String airTopic = "9018/AirPressure";
	private final String accTopic = "9018/Vibration";
	 
	private long keepAliveTime; 
	private long gwLastReceive; // Last time receiving message from Gateway
	private long registerSentTime; 
	private boolean isConnected = false; // is connected to Gateway
	private int QoSCounter = 1; // QoS Message Id counter
	private int registerCounter = 1; // Register Id counter
	private int currentRegisterId = 0; // ongoing Register Id
	private HashMap<Integer, MQTTSNPacket> RegAckHashMap = new HashMap<>(); //
	
	private PublishHelper currentOngoingQoS; // MQTTSNPacket, timeSent, retry count
	private AT86RF231 radio;
	private FrameIO fio;
	private Preon32Sensor sensor;

	private int tempTopicId;
	private int humTopicId;
	private int airTopicId;
	private int accTopicId;
	
	public static void main(String [] args ) throws Exception{
		new NodeSensor().run();
	}

	public void run() throws Exception{
		sensor = new Preon32Sensor();
		sensor.init();
		setupRadio();
		runRadioReceiver();

		while(true) {
			if (BASESTATION_ADDR == 0x00) { //Kalo belum tau alamat GW, broadcast SEARCHGW ke setiap gateway
				MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
				mqttsnPacket.setSEARCHGW(0x00); //The value 0x00 means “broadcast to all nodes in the network”. 
				send(mqttsnPacket, BROADCAST_ADDRESS);
				System.out.println("Broadcasting SEARCHGW");
				Thread.sleep(5000);
			} else if (!isConnected){ // Jika address udah ada, buat koneksi dengan Gateway.
				if(!handleGatewayTimeout()){
					MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
					mqttsnPacket.setCONNECT(NODE_SENSOR_ID, true, true, MQTTSNPacket.KEEP_ALIVE_TIME);
					send(mqttsnPacket, BASESTATION_ADDR);
					System.out.println("Send CONNECT to "+BASESTATION_ADDR);
					Thread.sleep(5000);
				}
			} else if (isConnected){ //Jika sudah konek, maka akan selalu sense, terus publish
				if(!handleGatewayTimeout()){
					handleQoSTimeOut();
					sendTemperature(2);
					sendPressure(2);
					sendAcceleration(1);
					sendHumidity(0);
					Thread.sleep(2000);
				}
			} 
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

		MQTTSNPacket packet = new MQTTSNPacket();
		packet.toMQTTSN(frame.getPayload());

		switch(packet.getMsgType()){
			case MQTTSNPacket.ADVERTISE:{
				System.out.println("Received a ADVERTISE message");
				if (BASESTATION_ADDR == 0x00){
					BASESTATION_ADDR = (int) frame.getSrcAddr();
				}
				if (BASESTATION_ADDR == frame.getSrcAddr()){
					keepAliveTime = ((packet.getMsgVarPart()[1] & 0xFF) << 8) | (packet.getMsgVarPart()[2] & 0xFF);
				}
				break;
			}
			case MQTTSNPacket.GWINFO:{
				System.out.println("Received a GWINFO message: ");
				BASESTATION_ADDR = (int)frame.getSrcAddr();
				break;
			}
			case MQTTSNPacket.CONNACK:{
				keepAliveTime = MQTTSNPacket.KEEP_ALIVE_TIME; //Sesuai dengan pesan setCONNECT
				System.out.println("Received a CONNACK message");
				handleCONNACK(packet);
				break;
			}
			case MQTTSNPacket.REGACK:{
				System.out.println("Received a REGACK message");
				handleREGACK(packet);
				break;
			}
			case MQTTSNPacket.PUBACK:{
				System.out.println("Received a PUBACK message");
				handlePubAck(packet);
				break;
			}
			case MQTTSNPacket.DISCONNECT:{
				isConnected = false; 
				BASESTATION_ADDR = 0x00;
				tempTopicId = 0;
				humTopicId = 0;
				airTopicId = 0;
				accTopicId = 0;
				break;
			}
			case MQTTSNPacket.PUBREC:{
				if (currentOngoingQoS == null) break; // Sudah di acknowledge: skip aja

				int messageId = ((packet.getMsgVarPart()[0] & 0xFF) << 8) | (packet.getMsgVarPart()[1] & 0xFF);
				MQTTSNPacket res = new MQTTSNPacket();
				res.setPUBREL(messageId);
				send(res, BASESTATION_ADDR);
				System.out.println("Receiving PUBREC ("+messageId+"), Sending PUBREL.. ");

				currentOngoingQoS.mqttsnMessage = res;
				currentOngoingQoS.counter = 1;
				currentOngoingQoS.timeSent = System.currentTimeMillis();
				break;
			}
			case MQTTSNPacket.PUBCOMP:{
				System.out.println("Receiving PUBCOMP");
				currentOngoingQoS = null;
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
            e.printStackTrace();
        }
	}

	private void handleQoSTimeOut(){ 
		// Kalo Belum ada yang di publish, langsung return
		if (currentOngoingQoS == null){ 
			return;
		}

		// Kalo currentOngoingQoS udah melewati maximum retry, timeout
		if (currentOngoingQoS.counter > MQTTSNPacket.MAX_PUBACK_RETRY){
			System.out.println("Current QoS Exceed maximum PubAck retry");
			currentOngoingQoS = null;
			return;
		}

		// send ulang kalo, udah melewati waktu timeout dan masih dibawah maximum retry 
		if (System.currentTimeMillis() - currentOngoingQoS.timeSent > MQTTSNPacket.PUBACK_TIMEOUT){
			MQTTSNPacket packet = currentOngoingQoS.mqttsnMessage;	
			
			//bit modification supaya dup jadi true (untuk pesan PUBLISH)
			if(packet.getMsgType() == (MQTTSNPacket.PUBLISH & 0xFF)){
				byte flagsWithDup = (byte) (packet.getMsgVarPart()[0] | 0x80); //Dup jadi true (bit ke terujung)
				currentOngoingQoS.mqttsnMessage.getMsgVarPart()[0] = flagsWithDup;
				System.out.println("Resending Publish: Timeout");
			} else {
				System.out.println("Resending Pubrel: Timeout");
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
				System.out.println("Gateway REJECTED: CONGESTION");
				this.isConnected = false;
				break;
			case 0x02:
				System.out.println("Gateway REJECTED: INVALID TOPIC ID");
				this.isConnected = false;
				break;
			case 0x03:
				System.out.println("Gateway REJECTED: not SUPPORTED");
				this.isConnected = false;
				break;
		}
	}

	private void handleREGACK(MQTTSNPacket mqttsnPacket){
	
		int topicId = ((mqttsnPacket.getMsgVarPart()[0] & 0xFF) << 8) | (mqttsnPacket.getMsgVarPart()[1] & 0xFF);
		int messageId = ((mqttsnPacket.getMsgVarPart()[2] & 0xFF) << 8) | (mqttsnPacket.getMsgVarPart()[3] & 0xFF); 
		if (messageId != currentRegisterId){ // Artinya messageID yang sebelumnya udah di hapus, jadi ga usah di proses karena udah ga ada di Map.
			return;
		}
		int returnCode = mqttsnPacket.getMsgVarPart()[4];
		switch (returnCode){
			case 0x00:
				byte[] oldPacket = RegAckHashMap.remove(messageId).getMsgVarPart();
				byte[] topic_name = new byte[oldPacket.length-4];
				System.arraycopy(oldPacket,  4, topic_name, 0, oldPacket.length-4); // Copy topic name
				String topicNameStr = new String(topic_name);

				switch (topicNameStr){
					case tempTopic:
						tempTopicId = topicId;
						System.out.println("Temperature Registered");
						break;
					case airTopic:
						airTopicId = topicId;
						System.out.println("Air Pressure Registered");
						break;
					case humTopic:
						humTopicId = topicId;
						System.out.println("Humidity Registered");
						break;
					case accTopic:
						accTopicId = topicId;
						System.out.println("Acceleration Registered");
						break;
				}
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
				if (currentOngoingQoS != null){
					System.out.println("There's no ongoing publish with QoS 1");
					break; // Pesan QoS sudah diacknowledge
				} 
				if (pubackId == 0) {
					System.out.println("Error: Accepted Puback with messageId 0");
					break;
				}

				byte[] publishVar = currentOngoingQoS.mqttsnMessage.getMsgVarPart();
				int publishId = ((publishVar[3] & 0xFF) << 8) | (publishVar[4] & 0xFF);
				if (pubackId == publishId){
					currentOngoingQoS = null;
					System.out.println("PUBACK Accepted, clearing current PUBACK");
				} else{
					System.out.println("PUBACK Accepted, but invalid message id.");
				}
				break;
			}
			case 0x01:{
				System.out.println("Gateway REJECTED: CONGESTION");
				if (pubackId == 0) break; // Pesan dengan QoS 0

				if (currentOngoingQoS == null) {
					System.out.println("No Ongoing QoS message"); // Pesan QoS > 0, tapi currentOngoingQoS null 
					break;
				} 

				if (currentOngoingQoS.counter > MQTTSNPacket.MAX_PUBACK_RETRY){
					System.out.println("Max Puback Reached, removing from hashmap..");
					currentOngoingQoS = null;
				} else {
					if (mqttsnPacket.getMsgType() == (MQTTSNPacket.PUBLISH & 0xFF)){
						//bit modification supaya dup jadi true (khusus PUBLISH)
						byte flagsWithDup = (byte) (currentOngoingQoS.mqttsnMessage.getMsgVarPart()[0] | 0x80);
						currentOngoingQoS.mqttsnMessage.getMsgVarPart()[0] = flagsWithDup;
					}
					System.out.println("Resending ongoing QoS: "+pubackId); // Bisa kirim PUBLISH QoS 1, 2, PUBREL
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
				if (tempTopicId == topicId){
					tempTopicId = 0;
				} else if(humTopicId == topicId){
					humTopicId = 0;
				} else if(airTopicId == topicId){
					airTopicId = 0;
				} else if(accTopicId == topicId){
					accTopicId = 0;	
				}
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

	private boolean handleGatewayTimeout(){
		long time_since_receive_from_gw = ((System.currentTimeMillis() - gwLastReceive) / 1000);
		System.out.println(time_since_receive_from_gw+" seconds since receiving something from GW");
		if (time_since_receive_from_gw > keepAliveTime){
			 isConnected = false; 
			 BASESTATION_ADDR = (byte) 0x00;
			 tempTopicId = 0;
			 humTopicId = 0;
			 airTopicId = 0;
			 accTopicId = 0;
			 System.out.println("Connection Time out!");
			 return true;
		}
		return false;
	}

	private void sendPublish(String payload, int topicID, int qos){
		if ( qos > 0 && currentOngoingQoS != null){
			System.out.println("There's currently QoS message sent. Current message is dropped.");
			return;
		} 
			
		int messageId = (qos > 0) ? QoSCounter : 0; // qos -1, 0 (messageID = 0), qos 1,2
		MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
		
		mqttsnPacket.setPUBLISH(false, qos, true, 0x00, topicID, messageId, payload);
		if (qos > 0){
			currentOngoingQoS = new PublishHelper(mqttsnPacket, System.currentTimeMillis());
			increaseQoSCounter();
		} 
		send(mqttsnPacket, BASESTATION_ADDR);
	}

	private void sendTemperature(int qos){
		if (!isConnected) return;
		if (tempTopicId == 0) {
			handleRegister(tempTopic, tempTopicId);
		} else {
			sendPublish(sensor.getTemperatureValue(), tempTopicId, qos);
			System.out.println("Publishing Temperature");
		}
	}
	
	private void sendHumidity(int qos){
		if (!isConnected) return;
		if (humTopicId == 0) {
			handleRegister(humTopic, humTopicId); 
		} else {
			sendPublish(sensor.getHumidityValue(), humTopicId, qos);
			System.out.println("Publishing Humidity");
		}
	}

	private void sendAcceleration(int qos){
		if (!isConnected) return;
		if (accTopicId == 0) {
			handleRegister(accTopic, accTopicId);
		} else {
			sendPublish(sensor.getAccelValue(), accTopicId, qos);
			System.out.println("Publishing Acceleration");
		}
	}

	private void sendPressure(int qos){
		if (!isConnected)return;
		if (airTopicId == 0) {
			handleRegister(airTopic, airTopicId);
		} else {
			sendPublish(sensor.getPressureValue(), airTopicId, qos);
			System.out.println("Publishing Air Pressure");
		}
	}
	
	private void handleRegister(String topicName, int topicId){
		if (topicId != 0) return;

		if (currentRegisterId == 0){ // Kalo belum ada paket yang sedang di daftarkan
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setREGISTER(0, registerCounter, topicName); //topicId pasti 0, kalau di kirim Client (Node sensor) 
			RegAckHashMap.put(registerCounter, mqttsnPacket);
			currentRegisterId = registerCounter;

			send(mqttsnPacket, BASESTATION_ADDR);
			registerSentTime = System.currentTimeMillis();
			registerCounter++;
			System.out.println("Registering: "+topicName);
		} else{ // Kalo udah ada yang di daftar, coba cek timeout. Kalo time out, hapus dari regack map 
			if (System.currentTimeMillis() - registerSentTime > MQTTSNPacket.REGISTER_TIMEOUT){
				System.out.println("REGISTER timeout: no REGACK received");
				RegAckHashMap.remove(currentRegisterId);
				currentRegisterId = 0;
			}
		}
	}

	// MessageID maximum 65535 (2 byte)
	private void increaseQoSCounter(){
        QoSCounter++;
        if (this.QoSCounter > 65535){
                this.QoSCounter = 1;
        } 
    }
}

