package aggregatingpreon32;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

import com.virtenio.commander.io.DataConnection;
import com.virtenio.commander.toolsets.preon32.Preon32Helper;

// import io.github.cdimascio.dotenv.Dotenv;

public class GatewayDesktop {
    private int gatewayId = 0x0001;
    private String gatewayAddress = "0x0001";
    private HashMap<String, Integer> nodeSensorMap = new HashMap<>(); // Client ID Key, Node Sensor Physical Address (WirelessNodeId) Value
    private HashMap<Integer, String> topicMap = new HashMap<>(); //Topid ID  key, Topic name Value
    private HashMap<String, Integer> topicReverseMap = new HashMap<>(); //Topid name key, Topic ID Value
    private HashMap<MQTTSNPacket, Integer> waitingPubAckMap = new HashMap<>();
    BlockingQueue<MQTTSNPacket> sendTaskQueue = new LinkedBlockingQueue<>();

    // Mqtt5BlockingClient client;
    MqttAsyncClient client;
    IMqttToken token;
    
    private int topicIdIncrement = 1;

    private final String PORT_NUMBER = "COM5";
    private final int BROADCAST_INTERVAL_SECONDS = 30;
    private final int BROADCAST_ADDRESS = 0xFFFF; //ALAMAT UNTUK BROADCAST
    DataConnection conn;
    BufferedOutputStream out; //For sending message to Preon32
    BufferedInputStream in;

    // Dotenv dotenv = Dotenv.load();
    // final String host = dotenv.get("MQTT_HOST");
    // final String username = dotenv.get("MQTT_USER");
    // final String password = dotenv.get("MQTT_PASS");
    // final String host = "b6f0de39dbdb4fbc89413670aabed28a.s1.eu.hivemq.cloud"; 
    final String host = "ssl://b6f0de39dbdb4fbc89413670aabed28a.s1.eu.hivemq.cloud:8883"; 
    final String username = "admin" ;
    final String password = "Admin123"; 

    public static void main(String[] args) {
        new GatewayDesktop().run();
    }

    public void run() {
        initIOStream(); // Menjalankan Gateway Preon32 beserta IO-nya
        initConnectionBroker(); // Connect ke broker
        runPortReader(); // Baca port USART
        runBroadcastConstantly(); // untuk send ADVERTISE
        runAggregate(); //  untuk send Publish
    }   

    private void initIOStream() {
        Preon32Helper nodeHelper = null;
        try {
            nodeHelper = new Preon32Helper(PORT_NUMBER, 115200);
            conn = nodeHelper.runModule("GatewayPreon32");
            out = new BufferedOutputStream(conn.getOutputStream());
            in = new BufferedInputStream(conn.getInputStream());
            System.out.println("IO Stream initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendToGatewayPreon32(byte[] EncapsulatedMessage){
        try {
            // System.out.println("sending to gateway preon32..");
            byte[] byteToSend = new byte[128];
            System.arraycopy(EncapsulatedMessage, 0, byteToSend, 0, EncapsulatedMessage.length);
            out.write(byteToSend);
            out.flush();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void runPortReader() {
        new Thread() {
            public void run() {
                while (true) {
                    byte[] incomingByte = new byte[128];
                    try {
                        int byteLength = in.read(incomingByte);
                        byte[] encapsulatedMessage = new byte[byteLength];
                        
                        System.arraycopy(incomingByte, 0, encapsulatedMessage, 0, byteLength);
                        if ((encapsulatedMessage[1] & 0xFF)  == 0xFE){ // Kalo msgType = 0xFE (EncapsulatedMessage)
                            handleEncapsulatedMessage(encapsulatedMessage);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private void handleEncapsulatedMessage(byte[] encapsulatedMessage){
        int lenNotMQTTSN = (int) (encapsulatedMessage[0] & 0xFF); // Panjang pesan diluar MQTT-SN
        
        byte[] wirelessNodeIdTemp = new byte[lenNotMQTTSN - 3]; // Di kurangi length, msgType, ctrl
        System.arraycopy(encapsulatedMessage, 3, wirelessNodeIdTemp, 0, lenNotMQTTSN-3);   

        if (wirelessNodeIdTemp.length != 2){ // Hanya pake hardware address (tidak bisa MAC, dll)
            System.out.println("debug: "+java.util.Arrays.toString(encapsulatedMessage));
            return;
        } 
        int wirelessNodeId = ((wirelessNodeIdTemp[0] & 0xFF ) << 8) | (wirelessNodeIdTemp[1] & 0xFF);
        byte[] mqttsnPacketByte = new byte[encapsulatedMessage.length - lenNotMQTTSN];
        System.arraycopy(encapsulatedMessage, lenNotMQTTSN, mqttsnPacketByte, 0, encapsulatedMessage.length-lenNotMQTTSN);   


        MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
        mqttsnPacket.toMQTTSN(mqttsnPacketByte);

        // System.out.println("Received Packet: "+java.util.Arrays.toString(encapsulatedMessage)); // +++
        // System.out.println("Received packet from wireless node id: "+wirelessNodeId+", with message type: "+mqttsnPacket.getMsgType());

        MQTTSNPacket response = new MQTTSNPacket();
        switch (mqttsnPacket.getMsgType() & 0xFF){
            case MQTTSNPacket.SEARCHGW:{
                System.out.println("Gateway received a SEARCHGW message");
                response.setGWINFO(gatewayId, gatewayAddress);
                byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                sendToGatewayPreon32(packetToSend);
                break;
            }
            case MQTTSNPacket.CONNECT:{
                System.out.println("Gateway received a CONNECT message");
                int nodeLength = mqttsnPacket.getMsgHeader()[0]-6;
                byte[] tempName = new byte[nodeLength];
                System.arraycopy(mqttsnPacket.getMsgVariablePart(), 4, tempName, 0, nodeLength);
                String nodeName = new String(tempName);
                nodeSensorMap.put(nodeName, wirelessNodeId);
                response.setCONNACK( 0x00); // Accepted
                byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                sendToGatewayPreon32(packetToSend);
                break;
            }
            case MQTTSNPacket.REGISTER:{
                if (nodeSensorMap.containsValue(wirelessNodeId)){ 
                    System.out.println("Gateway received a REGISTER message");
    
                    int topicId;
                    int msgId = ((mqttsnPacket.getMsgVariablePart()[2] & 0xFF ) << 8) | (mqttsnPacket.getMsgVariablePart()[3] & 0xFF);
    
                    int topicNameLength = mqttsnPacket.getMsgHeader()[0]-6;
                    byte[] tempName = new byte[topicNameLength];
                    System.arraycopy(mqttsnPacket.getMsgVariablePart(), 4, tempName, 0, topicNameLength);
                    String topicName = new String(tempName);
    
                    if (topicMap.containsValue(topicName)){
                        topicId = topicReverseMap.get(topicName);
                    } else { // Kalo belum pernah di daftarin
                        topicId = topicIdIncrement;
                        increaseTopicId();
                        topicMap.put(topicId, topicName);
                    }
                    response.setREGACK(topicId, msgId, 0x00); //Success
                    byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                    sendToGatewayPreon32(packetToSend);
                } else{ // Kalo belum ada di map, suruh DISCONNECT
                    response.setDISCONNECT();
                    byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                    sendToGatewayPreon32(packetToSend);
                }
                break;
            }
            case MQTTSNPacket.PUBLISH:{
                int topicId = ((mqttsnPacket.getMsgVariablePart()[1] & 0xFF ) << 8) | (mqttsnPacket.getMsgVariablePart()[2] & 0xFF);
                
                String topicName = topicMap.get(topicId);
                if (topicName == null){ // Ga ketemu mappingnya, jadi harus send PUBACK ke sensor
                    System.out.println("Gateway received a PUBLISH message, but topic not found. Sending PUBACK");
                    response.setPUBACK(topicId, 0x00, 0x02); // Topic id invalid
                    byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                    sendToGatewayPreon32(packetToSend);
                } else {
                    System.out.println("Gateway received a PUBLISH message"+ topicName+" with topic id: "+topicId);
                    sendTaskQueue.add(mqttsnPacket);
                    int messageId = mqttsnPacket.getMsgVariablePart()[3] << 8 | mqttsnPacket.getMsgVariablePart()[4];
                    if (messageId != 0){
                        waitingPubAckMap.put(mqttsnPacket, wirelessNodeId);
                    }
                }
                break;
            }
            case 0x14:{
                System.out.println("unhandled: Wireless Node Id > 2"); // kode 0x14 untuk debug Preon32Gateway(Tidak sesuai spec)
            }
            case MQTTSNPacket.PUBACK:{
                // TO DO:
                System.out.println("Gateway received a PUBACK message, but currently not handled by gateway");
                break;
            }
            default:{
                System.out.println("Gateway received an unsupported message type: "+mqttsnPacket.getMsgType() + " with payload: " + new String(mqttsnPacket.getMsgVariablePart()));
                break;
            }
        }
    }



    private void initConnectionBroker(){
        try {
            client = new MqttAsyncClient(host,
            "raynercuanda",
            new MemoryPersistence());

            MqttConnectionOptions mqttConnectOptions = new MqttConnectionOptions();
            mqttConnectOptions.setUserName(username);
            mqttConnectOptions.setPassword(password.getBytes(UTF_8));

            client.connect(mqttConnectOptions);
            System.out.println("Connection to Broker Established.");
        } catch (MqttException e) {
            System.out.println("Failed to connect to broker.");
            e.printStackTrace();
        }

    }

    private void runAggregate() {
        new Thread() {
            public void run() {
                while(true){
                    try{
                        MQTTSNPacket mqttsnPacket = sendTaskQueue.take();
                        
                        int topicId = ((mqttsnPacket.getMsgVariablePart()[1] & 0xFF ) << 8) | (mqttsnPacket.getMsgVariablePart()[2] & 0xFF);
                        int messageId = ((mqttsnPacket.getMsgVariablePart()[3] & 0xFF ) << 8) | (mqttsnPacket.getMsgVariablePart()[4] & 0xFF);
                        String topicName = topicMap.get(topicId);
                        
                        int payloadLength = mqttsnPacket.getMsgHeader()[0]-7; 
                        byte[] payloadTemp = new byte[payloadLength];
                        System.arraycopy(mqttsnPacket.getMsgVariablePart(), 5, payloadTemp, 0, payloadLength);
                        String payload = new String(payloadTemp);
                        
                        int flags = mqttsnPacket.getMsgVariablePart()[0] & 0xFF;
                        int qosBits = ( flags & 0x60) >> 5; // Ambil bit QoS

                        MqttMessage message = new MqttMessage(payload.getBytes(UTF_8));
                        if (qosBits == 1){
                            message.setQos(1);
                        } else { //Tidak implementasi QoS 2
                            message.setQos(0);
                        }
                        
                        token = client.publish(topicName, message);
                        token.waitForCompletion();
                        if (qosBits == 1){
                            MQTTSNPacket pubackPacket = new MQTTSNPacket();
                            pubackPacket.setPUBACK(topicId, messageId, 0x00);
                            sendToGatewayPreon32(MQTTSNPacket.toEncapsulatedMessage(waitingPubAckMap.remove(mqttsnPacket), pubackPacket.toBytes()));
                        } else {
                            System.out.println("Message published with QoS 0, no PUBACK needed.");
                        }
                    } catch (Exception e){  
                        System.err.println("Packet failed to send to broker: "+e);
                    }
                    
                }
            }
        }.start();
    }

    private void runBroadcastConstantly(){
        new Thread(){
            public void run(){
                while (true){
                    try{
                        MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
                        mqttsnPacket.setADVERTISE(gatewayId);
                        byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(BROADCAST_ADDRESS, mqttsnPacket.toBytes());
                        sendToGatewayPreon32(packetToSend);
                        // System.out.println("Broadcasting ADVERTISE message: "+java.util.Arrays.toString(packetToSend)); 
                        Thread.sleep(BROADCAST_INTERVAL_SECONDS*1000);
                    } catch (InterruptedException e){
                        System.err.println("Failed to broadcast ADVERTISE message");
                    }
                }
            }
        }.start();
    }

    private void increaseTopicId(){
        topicIdIncrement++;
        if (this.topicIdIncrement > 65535){
                this.topicIdIncrement = 1;
        } 
    }
}
