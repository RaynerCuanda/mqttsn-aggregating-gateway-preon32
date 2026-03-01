// ASUMSI: PASTI MENGGUNAKAN TOPIK ID, TIDAK ADA SHORT TOPIC NAME

import java.io.IOException;

import com.virtenio.driver.device.ADT7410;
import com.virtenio.driver.device.ADXL345;
import com.virtenio.driver.device.SHT21;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.device.at86rf231.AT86RF231RadioDriver;
import com.virtenio.driver.gpio.GPIO;
import com.virtenio.driver.i2c.NativeI2C;
import com.virtenio.preon32.examples.common.RadioInit;
import com.virtenio.preon32.node.Node;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;
import com.virtenio.vm.Time;

public class NodeSensor {
	private int COMMON_CHANNEL = 24; // channel
	private int COMMON_PANID = 0xCAFE; // Personal Area Network ID
	private String NODE_SENSOR_ID = "node_1"; // IDENTITAS NODE SENSOR
	private int localAddress = 0x0001; // ALAMAT NODE SENSOR
	private int BASESTATION_ADDR = 0x00; // ALAMAT TUJUAN BASE STATION (AWAL 0 SEBELUM SEARCHGW)
	private int BROADCAST_ADDRESS = 0xFFFF; //ALAMAT UNTUK BROADCAST

	
	private AT86RF231 radio;
	private FrameIO fio;
	private boolean isConnected = false;

	private String tempTopic = "9017/Temperature";
	private String humTopic = "9017/Humidity";
	private String airTopic = "9017/AirPressure";
	private String accTopic = "9017/Vibration";
	private int tempTopicId = 0x00;
	private int humTopicId = 0x00;
	private int airTopicId = 0x00;
	private int accTopicId = 0x00;
	
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
                Frame frame = new Frame();
                while (true) {
                    try {
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
			// Jika address udah ada, buat koneksi
			} else if (!isConnected){
				MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
				mqttsnPacket.setCONNECT(NODE_SENSOR_ID, true, true);
				send(mqttsnPacket, BASESTATION_ADDR);
			//Jika sudah konek, maka akan selalu sense, terus publish
			} else if (isConnected){
				handleHumidity(sensor);
				handlePressure(sensor);
				handleTemperature(sensor);
				handleAcceleration(sensor);
			} 
		}
	}

	public void send(MQTTSNPacket packet, int destinationAddresss){
		byte[] packetToSend = packet.toBytes();
        int frameControl = Frame.TYPE_DATA | Frame.ACK_REQUEST | Frame.DST_ADDR_16
                | Frame.INTRA_PAN | Frame.SRC_ADDR_16;

        final Frame testFrame = new Frame(frameControl);
        testFrame.setDestPanId(COMMON_PANID);
        testFrame.setDestAddr(destinationAddresss);
        testFrame.setSrcAddr(localAddress);
        testFrame.setPayload(packetToSend);

        try {
            fio.transmit(testFrame);
        } catch (IOException e) {
            e.printStackTrace();
        }
	}

	private void handleMessage(Frame frame){
		//Frame diubah jadi bytes dulu, pisahin msgHeader, cari tipe pesan, sesuain.
		//BUAT NANTI MENERIMA ADVERTISE, GWINFO, CONNACK, PUBACK, REGACK
		// id_topic diisi sama pesan ACK yang diterima.
	}
	
	public static void main(String [] args ) throws Exception
	{
		NodeSensor ns = new NodeSensor();
		ns.run();
	}
	
	private void handleHumidity(Preon32Sensor sensor){
		if (humTopicId == 0) {
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setREGISTER(0, 0x00000000000000000000000, humTopic); //topicId pasti 0, kalau di kirim Client (Node sensor) 
			send(mqttsnPacket, BASESTATION_ADDR);
		} else {
			String payload = sensor.getHumidityValue();
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setPUBLISH(false, 0, true, 0x00, humTopicId, 0, payload);
			send(mqttsnPacket, BASESTATION_ADDR);
		}
	}

	private void handleAcceleration(Preon32Sensor sensor){
		if (accTopicId == 0) {
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setREGISTER(0, 0x00000000000000000000000, accTopic); //topicId pasti 0, kalau di kirim Client (Node sensor) 
			send(mqttsnPacket, BASESTATION_ADDR);
		} else {
			String payload = sensor.getAccelValue();
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setPUBLISH(false, 0, true, 0x00, accTopicId, 0, payload);
			send(mqttsnPacket, BASESTATION_ADDR);
		}
	}

	private void handleTemperature(Preon32Sensor sensor){
		if (tempTopicId == 0) {
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setREGISTER(0, 0x00000000000000000000000, tempTopic); //topicId pasti 0, kalau di kirim Client (Node sensor) 
			send(mqttsnPacket, BASESTATION_ADDR);
		} else {
			String payload = sensor.getTemperatureValue();
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setPUBLISH(false, 0, true, 0x00, tempTopicId, 0, payload);
			send(mqttsnPacket, BASESTATION_ADDR);
		}
	}

	private void handlePressure(Preon32Sensor sensor){
		if (airTopicId == 0) {
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setREGISTER(0, 0x00000000000000000000000, airTopic); //topicId pasti 0, kalau di kirim Client (Node sensor) 
			send(mqttsnPacket, BASESTATION_ADDR);
		} else {
			String payload = sensor.getPressureValue();
			MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
			mqttsnPacket.setPUBLISH(false, 0, true, 0x00, airTopicId, 0, payload);
			send(mqttsnPacket, BASESTATION_ADDR);
		}
	}
	
}

