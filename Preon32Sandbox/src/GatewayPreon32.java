import java.io.IOException;
import java.io.OutputStream;

import com.virtenio.driver.device.at86rf231.AT86RF231;
import com.virtenio.driver.device.at86rf231.AT86RF231RadioDriver;
import com.virtenio.driver.usart.NativeUSART;
import com.virtenio.driver.usart.USART;
import com.virtenio.driver.usart.USARTParams;
import com.virtenio.preon32.examples.common.USARTConstants;
import com.virtenio.preon32.node.Node;
import com.virtenio.radio.ieee_802_15_4.Frame;
import com.virtenio.radio.ieee_802_15_4.FrameIO;
import com.virtenio.radio.ieee_802_15_4.RadioDriver;
import com.virtenio.radio.ieee_802_15_4.RadioDriverFrameIO;

public class GatewayPreon32{
    private USART usart;
    private OutputStream out;

    private FrameIO fio;
    private AT86RF231 radio;

	// private final int COMMON_CHANNEL = 24; // channel
	private  int COMMON_PANID = 0xCAFE; // Personal Area Network ID
	// private  String NODE_SENSOR_ID = "gateway"; // IDENTITAS NODE SENSOR
	private  int LOCAL_ADDRESS = 0x0001; // ALAMAT GATEWAY;
	//  private int BROADCAST_ADDRESS = 0xFFFF; //ALAMAT UNTUK BROADCAST

	public static void main(String [] args ) throws Exception
	{
		new GatewayPreon32().run();
	}

    private void run() throws Exception{
        useUSART();
        setupRadio();
        runUSARTReceiver();
        runRadioReceiver();
    //    byte[] mqttSnPacket = new byte[]{9, 2, 1, 48, 120, 48, 48, 48, 49}; // Contoh MQTT-SN packet advertise
    //    sendToNodeSensor(mqttSnPacket,2);
    }

    private void useUSART() {
        usart = configUSART();
        try {
            out = usart.getOutputStream();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    private static USART configUSART() {
        int instanceID = 0;
        USARTParams params = new USARTParams(115200, USART.DATA_BITS_8,
                USART.STOP_BITS_1, USART.PARITY_NONE);
        NativeUSART usart = NativeUSART.getInstance(instanceID);
        try {
            usart.close();
            usart.open(params);
            return usart;
        } catch (Exception e) {
            return null;
        }
    }

    private void runUSARTReceiver() {
        new Thread() {
            public void run() {
                while (true) {
                    byte[] incomingByte = new byte[128]; // dari USB Port

                    try {
                        int byteLength = usart.readFully(incomingByte);
                        
                        byte[] encapsulatedMessage = new byte[byteLength];
                        System.arraycopy(incomingByte, 0, encapsulatedMessage, 0, byteLength);
                       if ((encapsulatedMessage[1] & 0xFF) == 0xFE){ // Kalo msgType = 0xFE (EncapsulatedMessage)
                            handleEncapsulatedMessage(encapsulatedMessage);
                       }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private void setupRadio() {
        try {
            radio = Node.getInstance().getTransceiver();
            radio.open();
            radio.setAddressFilter(COMMON_PANID, LOCAL_ADDRESS, LOCAL_ADDRESS, false);
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
                        handleMessageMQTTSN(frame);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private void handleMessageMQTTSN(Frame frame){
        byte[] mqttsnPacket = frame.getPayload();
        int sender = (int) frame.getSrcAddr();
        byte[] encapsulatedMessage = MQTTSNPacket.toEncapsulatedMessage(sender, mqttsnPacket);
        sendToPC(encapsulatedMessage);
    }

    private void sendToPC(byte[] EncapsulatedMessage){
        try {
            byte[] b = new byte[128];
            System.arraycopy(EncapsulatedMessage, 0, b, 0, EncapsulatedMessage.length);
            out.write(b);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        } 
    }

    private void sendToNodeSensor(byte[] mqttSnPacket, int destinationAddresss){
        int frameControl = Frame.TYPE_DATA | Frame.DST_ADDR_16
                | Frame.INTRA_PAN | Frame.SRC_ADDR_16;

        final Frame testFrame = new Frame(frameControl);
        testFrame.setDestPanId(COMMON_PANID);
        testFrame.setSrcAddr(LOCAL_ADDRESS);
        testFrame.setDestAddr(destinationAddresss);
        testFrame.setPayload(mqttSnPacket);

        try {
            fio.transmit(testFrame);
        } catch (IOException e) {
            // e.printStackTrace();
        }
	}

    private void handleEncapsulatedMessage(byte[] encapsulatedMessage){
        int lenNotMQTTSN = encapsulatedMessage[0] & 0xFF; // Panjang pesan diluar MQTT-SN

        byte[] wirelessNodeId = new byte[lenNotMQTTSN - 3]; // Di kurangi length, msgType, ctrl
        System.arraycopy(encapsulatedMessage, 3, wirelessNodeId, 0, lenNotMQTTSN-3);   
        if (wirelessNodeId.length != 2){ // Hanya pake hardware address (tidak bisa MAC, dll)
            sendToPC(new byte[]{5, (byte)0xFe, 0, 0, 2, 3, (byte)0x14, 2});
            return;
        } 
        int wirelessNodeIdInt = ((wirelessNodeId[0] & 0xFF ) << 8) | (wirelessNodeId[1] & 0xFF);
        byte[] mqttsnPacket = new byte[encapsulatedMessage[lenNotMQTTSN]];
        System.arraycopy(encapsulatedMessage, lenNotMQTTSN, mqttsnPacket, 0, encapsulatedMessage[lenNotMQTTSN]);  
        sendToNodeSensor(mqttsnPacket, wirelessNodeIdInt); 
    }
}
