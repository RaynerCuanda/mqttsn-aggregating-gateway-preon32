import java.io.IOException;
import java.util.HashMap;
import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.device.at86rf231.AT86RF231RadioDriver;
import com.virtenio.preon32.node.Node;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;

public class NodeSensor2 {
	private int COMMON_PANID = 0xCAFE; // Personal Area Network ID
	private int BASESTATION_ADDR = 0x0001; // Alamat Base station (Disii setelah dapat GWINFO)
	private int BROADCAST_ADDRESS = 0xFFFF; //Alamat Broadcast

	// Configuration for each node sensor
	private final int localAddress = 0x0005; // ALAMAT NODE SENSOR
	private int tempTopicId = 1; // Temperature Predefined Id
	private int humTopicId = 2; // Humidity Predefined Id
	private String airShortTopicName = "AP"; // Air Pressure Short Topic Name
	private String accShortTopicName = "AC"; // Acceleration Short Topic Name

	private AT86RF231 radio;
	private FrameIO fio;
	private Preon32Sensor sensor;
	
	public static void main(String [] args ) throws Exception{
		new NodeSensor().run();
	}

	public void run() throws Exception{
		sensor = new Preon32Sensor();
		sensor.init();
		setupRadio();
		runRadioReceiver();

		while(true) {
			sendTemperature(-1);
			sendPressure(-1); 
			sendAcceleration(-1);
			sendHumidity(-1);
			Thread.sleep(20000);
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

	private void sendTemperature(int qos){
		MQTTSNPacket mqttsnPacket = new MQTTSNPacket();	
		mqttsnPacket.setPUBLISH(false, qos, true, 0b01, tempTopicId, 0, sensor.getTemperatureValue());
		send(mqttsnPacket, BASESTATION_ADDR);
		System.out.println("Publishing Temperature");

	}
	
	private void sendHumidity(int qos){
		MQTTSNPacket mqttsnPacket = new MQTTSNPacket();	
		mqttsnPacket.setPUBLISH(false, qos, true, 0b01, humTopicId, 0, sensor.getHumidityValue());
		send(mqttsnPacket, BASESTATION_ADDR);
		System.out.println("Publishing Humidity");
	}

	private void sendAcceleration(int qos){
		MQTTSNPacket mqttsnPacket = new MQTTSNPacket();	
		mqttsnPacket.setPUBLISH(false, qos, true, 0b10, accShortTopicName, 0, sensor.getAccelValue());
		send(mqttsnPacket, BASESTATION_ADDR);
		System.out.println("Publishing Acceleration");
	}

	private void sendPressure(int qos){
		MQTTSNPacket mqttsnPacket = new MQTTSNPacket();	
		mqttsnPacket.setPUBLISH(false, qos, true, 0b10, airShortTopicName, 0, sensor.getPressureValue());
		send(mqttsnPacket, BASESTATION_ADDR);
		System.out.println("Publishing Air Pressure");
	}
}

