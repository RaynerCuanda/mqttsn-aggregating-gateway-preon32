// ASUMSI: PASTI MENGGUNAKAN TOPIK ID, TIDAK ADA SHORT TOPIC NAME
// NODE SENSOR HANYA BISA TERKONEKSI DENGAN 1 GATEWAY PADA 1 SAAT
// MEMUNGKINKAN UNTUK GATEWAY TERGANTI DITENGGAH KARENA TIBA-TIBA MENDAPATKAN PESAN GWINFO.
// ATAU MUNGKIN GWINFO HANYA MENGUBAH KETIKA ISCONNECTED = FALSE?
// PERUBAHAN PADA LOGIC ADVERTISE, KARENA MUNGKIN GATEWAY GAGAL HANYA SAAT MENGIRIMKAN ADVERTISE.
// PUBACK TIDAK ADA, jadi QoS Publish pasti 0 


import java.io.IOException;
import java.util.HashMap;

import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.device.at86rf231.AT86RF231RadioDriver;
import com.virtenio.preon32.node.Node;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;
import com.virtenio.vm.Time;

public class NodeSensor {
	// private int COMMON_CHANNEL = 24; // channel
	private int COMMON_PANID = 0xCAFE; // Personal Area Network ID
	private String NODE_SENSOR_ID = "node_1"; // IDENTITAS NODE SENSOR
	private int localAddress = 0x0002; // ALAMAT NODE SENSOR
	private int BASESTATION_ADDR; // ALAMAT TUJUAN BASE STATION (AWAL BELUM DI ISI NILAI SEBELUM SEARCHGW)
	private int BROADCAST_ADDRESS = 0xFFFF; //ALAMAT UNTUK BROADCAST
	
	private long timeLastReceive;
	private long durationConnectionTime;
	private long registerSentTime;
	private boolean isConnected = false;
	private HashMap<Integer, MQTTSNPacket> RegAckHashMap = new HashMap<Integer, MQTTSNPacket>();
	private HashMap<Integer, PublishHelper> pubAckHashMap = new HashMap<Integer, PublishHelper>();
	private int currentRegisterId = 0;
	private long REGISTER_TIMEOUT = 10 * 1000; // seconds
	private int registerCounter = 1;
	private int pubAckCounter = 1;
	private long PUBACK_TIMEOUT = 10 * 1000; //
	
	private AT86RF231 radio;
	private FrameIO fio;

	private final String tempTopic ="9017/Temperature";
	private final String humTopic = "9017/Humidity";
	private final String airTopic = "9017/AirPressure";
	private final String accTopic = "9017/Vibration";
	private int tempTopicId;
	private int humTopicId;
	private int airTopicId;
	private int accTopicId;
	
	public static void main(String [] args ) throws Exception
	{
		new NodeSensor().run();
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


	public void run() throws Exception{
		Preon32Sensor sensor = new Preon32Sensor();
		sensor.init();
		setupRadio();
		runRadioReceiver();

		//Kalo belum tau alamat GW, broadcast SEARCHGW ke setiap gateway
		while(true) {
			if (BASESTATION_ADDR == 0x00) {
				MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
				mqttsnPacket.setSEARCHGW(0x00);
				send(mqttsnPacket, BROADCAST_ADDRESS);
				System.out.println("send searchgw");
				 Thread.sleep(5000);
				// Jika address udah ada, buat koneksi
			} else if (!isConnected){
				handleGatewayTimeout();
				MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
				mqttsnPacket.setCONNECT(NODE_SENSOR_ID, true, true);
				send(mqttsnPacket, BASESTATION_ADDR);
				System.out.println("send connect");
				Thread.sleep(5000);
				//Jika sudah konek, maka akan selalu sense, terus publish
			} else if (isConnected){
				handleGatewayTimeout();
				handleTemperature(sensor);
				handlePressure(sensor);
				handleAcceleration(sensor);
				handleHumidity(sensor);
				handlePubAckTimeOut();
				Thread.sleep(2000);
			} 
		}
	}

	private void handleMessage(Frame frame){
		timeLastReceive = System.currentTimeMillis();

		byte[] tempPayload = frame.getPayload();
		MQTTSNPacket packet = new MQTTSNPacket();
		packet.toMQTTSN(tempPayload);
		switch(packet.getMsgType()){
			case MQTTSNPacket.ADVERTISE:
				System.out.println("Node Sensor received a ADVERTISE message");
				if (BASESTATION_ADDR == 0x00){
					BASESTATION_ADDR = (int)frame.getSrcAddr();
				}
				if (BASESTATION_ADDR == (int) frame.getSrcAddr()){ // Kalo dapet advertise dari GW yang berbeda, di ignore
					durationConnectionTime = ((packet.getMsgVariablePart()[1] & 0xFF) << 8) | (packet.getMsgVariablePart()[2] & 0xFF);
				}
				break;
			case MQTTSNPacket.GWINFO:
				System.out.println("Node Sensor received a GWINFO message");
				BASESTATION_ADDR = (int)frame.getSrcAddr();
				durationConnectionTime = 30;
				break;
				case MQTTSNPacket.CONNACK:
				System.out.println("Node Sensor received a CONNACK message");
				handleCONNACK(packet.getMsgVariablePart()[0]);
				break;
				case MQTTSNPacket.REGACK:
				System.out.println("Node Sensor received a REGACK message");
				handleREGACK(packet);
				break;
			case MQTTSNPacket.PUBACK:
				System.out.println("Node Sensor received a PUBACK message, Return Code: " + packet.getMsgVariablePart()[4]);
				//Return Code 0x02 (TopicId Invalid)
				if (packet.getMsgVariablePart()[4] == 0x02){ 
					int topicId = ((packet.getMsgVariablePart()[0] & 0xFF) << 8) | (packet.getMsgVariablePart()[1] & 0xFF);
					if(tempTopicId == topicId){
						tempTopicId = 0;
					} else if(humTopicId == topicId){
						humTopicId = 0;
					} else if(airTopicId == topicId){
						airTopicId = 0;
					} else if(accTopicId == topicId){
						accTopicId = 0;	
					}
				} else if (packet.getMsgVariablePart()[4] == 0x00){ // Return Code 0x00 (Accepted)
					System.out.println("Gateway ACCEPTED: PUBLISH");
					// Ilangin dari map, karena udah di acknowledge sama gateway
					int messageId = ((packet.getMsgVariablePart()[2] & 0xFF) << 8) | (packet.getMsgVariablePart()[3] & 0xFF);
					pubAckHashMap.remove(messageId);
				} else {
					System.out.println("Gateway REJECTED: unknown reason");
				}
				break;
			case MQTTSNPacket.DISCONNECT:
				isConnected = false; 
				BASESTATION_ADDR = 0x00;
				tempTopicId = 0;
				humTopicId = 0;
				airTopicId = 0;
				accTopicId = 0;
				break;
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
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

	private void handlePubAckTimeOut(){
		for (Integer i: pubAckHashMap.keySet()){
			PublishHelper published = pubAckHashMap.get(i);

			if (System.currentTimeMillis() - published.timeSent > PUBACK_TIMEOUT){
				send(published.mqttMessage, BASESTATION_ADDR);

				published.timeSent = System.currentTimeMillis();
			}
		}
	}

	private void handleREGACK(MQTTSNPacket mqttsnPacket){
	
		int topicId = ((mqttsnPacket.getMsgVariablePart()[0] & 0xFF) << 8) | (mqttsnPacket.getMsgVariablePart()[1] & 0xFF); // Topic Id: 0,1
		int messageId = ((mqttsnPacket.getMsgVariablePart()[2] & 0xFF) << 8) | (mqttsnPacket.getMsgVariablePart()[3] & 0xFF); // Message Id: 2,3
		if (messageId != currentRegisterId){ // Artinya messageID yang sebelumnya udah di hapus, jadi ga usah di proses karena udah ga ada di Map.
			return;
		}
		switch (mqttsnPacket.getMsgVariablePart()[4]){
			case 0x00:
				byte[] oldPacket = RegAckHashMap.remove(messageId).getMsgVariablePart();
				byte[] topic_name = new byte[oldPacket.length-4];
				System.arraycopy(oldPacket,  4, topic_name, 0, oldPacket.length-4); // Copy topic name
				String topicNameStr = new String(topic_name);

				switch (topicNameStr){
					case tempTopic:
						System.out.println("Temperature Registered");
						tempTopicId = topicId;
						break;
					case airTopic:
						System.out.println("Air Pressure Registered");
						airTopicId = topicId;
						break;
					case humTopic:
						System.out.println("Humidity Registered");
						humTopicId = topicId;
						break;
					case accTopic:
						System.out.println("Acceleration Registered");
						accTopicId = topicId;
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

	private void handleCONNACK(byte returnCode){
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

	private void handleGatewayTimeout(){
		long time_since_receive_from_gw = ((System.currentTimeMillis() - timeLastReceive) / 1000);
		System.out.println(time_since_receive_from_gw+" seconds since receiving something from GW");
		if (time_since_receive_from_gw > durationConnectionTime){
			 isConnected = false; 
			 BASESTATION_ADDR = 0x00;
			 tempTopicId = 0;
			 humTopicId = 0;
			 airTopicId = 0;
			 accTopicId = 0;
			 System.out.println("Connection Time out!");
		}	
	}


	private void handleHumidity(Preon32Sensor sensor){
		if (!isConnected)return;
		if (humTopicId == 0) {
			handleRegister(humTopic, humTopicId); // Supaya kalo gateway timeout ga register 
		} else {
			// String payload = sensor.getHumidityValue();
			// MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			// mqttsnPacket.setPUBLISH(false, 0, true, 0x00, humTopicId, 0, payload);
			// send(mqttsnPacket, BASESTATION_ADDR);
			// System.out.println("Publishing Humidity");

			//  Contoh pesan Publish dengan QoS 1
			String payload = sensor.getHumidityValue();
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setPUBLISH(false, 1, true, 0x00, humTopicId, pubAckCounter, payload);
			pubAckCounter++; // TO DO: nanti ubah jadi increasePubAckCounter biar ga overflow (Klo > 65535)

			PublishHelper pub = new PublishHelper(mqttsnPacket, System.currentTimeMillis());
			pubAckHashMap.put(pubAckCounter, pub);

			send(mqttsnPacket, BASESTATION_ADDR);
			System.out.println("Publishing Humidity");
		}
	}

	private void handleAcceleration(Preon32Sensor sensor){
		if (!isConnected)return;
		if (accTopicId == 0) {
			handleRegister(accTopic, accTopicId);
		} else {
			String payload = sensor.getAccelValue();
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setPUBLISH(false, 0, true, 0x00, accTopicId, 0, payload);
			send(mqttsnPacket, BASESTATION_ADDR);
			System.out.println("Publishing Acceleration");
		}
	}

	private void handleTemperature(Preon32Sensor sensor){
		if (!isConnected)return;
		if (tempTopicId == 0) {
			handleRegister(tempTopic, tempTopicId);
		} else {
			String payload = sensor.getTemperatureValue();
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setPUBLISH(false, 0, true, 0x00, tempTopicId, 0, payload);
			send(mqttsnPacket, BASESTATION_ADDR);
			System.out.println("Publishing Temperature");
		}
	}

	private void handlePressure(Preon32Sensor sensor){
		if (!isConnected)return;
		if (airTopicId == 0) {
			handleRegister(airTopic, airTopicId);
		} else {
			String payload = sensor.getPressureValue();
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setPUBLISH(false, 0, true, 0x00, airTopicId, 0, payload);
			send(mqttsnPacket, BASESTATION_ADDR);
			System.out.println("Publishing Air Pressure");
		}
	}
	
	private void handleRegister(String topicName, int topicId){
		if (topicId == 0){
			if (currentRegisterId == 0){ // Kalo belum ada paket yang sedang di daftarkan
				MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
				mqttsnPacket.setREGISTER(0, registerCounter, topicName); //topicId pasti 0, kalau di kirim Client (Node sensor) 
				RegAckHashMap.put(registerCounter, mqttsnPacket);
				currentRegisterId = registerCounter;
				send(mqttsnPacket, BASESTATION_ADDR);
				registerSentTime = System.currentTimeMillis();
				registerCounter++;
				System.out.println("Registering "+topicName);
			} else{ // Kalo udah ada yang di daftar, coba cek timeout. Kalo time out, hapus dari regack map 
				if (System.currentTimeMillis() - registerSentTime > REGISTER_TIMEOUT){
					System.out.println("REGISTER timeout: no REGACK received");
					RegAckHashMap.remove(currentRegisterId);
					currentRegisterId = 0;
				}
			}
		}
	}
}

