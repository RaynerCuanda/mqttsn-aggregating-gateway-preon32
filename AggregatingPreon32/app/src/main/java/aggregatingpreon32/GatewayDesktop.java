package aggregatingpreon32;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.HashMap;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;
import com.virtenio.commander.io.DataConnection;
import com.virtenio.commander.toolsets.preon32.Preon32Helper;

import io.github.cdimascio.dotenv.Dotenv;


public class GatewayDesktop {
    private int gatewayId = 0x0001;
    private String gatewayAddress = "0x0001";
    private HashMap<String, Integer> nodeSensorMap = new HashMap<>();
    private HashMap<Integer, String> topicMap = new HashMap<>();
    BlockingQueue<MQTTSNPacket> sendTaskQueue = new LinkedBlockingQueue<>();
    Mqtt5BlockingClient client;
    private int topicIdIncrement = 1;

    private final String PORT_NUMBER = "COM5";
    private final int BROADCAST_INTERVAL_SECONDS = 30;
    private final int BROADCAST_ADDRESS = 0xFFFF; //ALAMAT UNTUK BROADCAST
    DataConnection conn;
    BufferedOutputStream out; //For sending message to Preon32
    BufferedInputStream in;

    public static void main(String[] args) {
        new GatewayDesktop().run();
    }

    public void run() {
        initIOStream(); 
        initConnectionBroker(); // Bikin koneksi sama broker
        runPortReader(); // Baca port 
        // runBroadcastConstantly(); // untuk send ADVERTISE
        runAggregate(); //  untuk send Publish
    }   

    private void initIOStream() {
        Preon32Helper nodeHelper = null;
        try {
            nodeHelper = new Preon32Helper(PORT_NUMBER, 115200);
            conn = nodeHelper.runModule("GatewayPreon32");
            out = new BufferedOutputStream(conn.getOutputStream());
            in = new BufferedInputStream(conn.getInputStream());
            // System.out.println("IO Stream initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void sendToGatewayPreon32(byte[] EncapsulatedMessage){
        try {
            System.out.println("sending to gateway preon32..");
            byte[] byteToSend = new byte[128];
            System.arraycopy(EncapsulatedMessage, 0, byteToSend, 0, EncapsulatedMessage.length);
            out.write(byteToSend);
            out.flush();
        } catch (Exception e) {
            // TODO Auto-generated catch block
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
        if (lenNotMQTTSN < 3 || lenNotMQTTSN > encapsulatedMessage.length) {
            System.out.println("WirelessNodeId is not hardware address");
            return;
        }
        
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
        switch (mqttsnPacket.getMsgType()){
            case MQTTSNPacket.SEARCHGW:{
                System.out.println("Gateway received a SEARCHGW message");
                response.setGWINFO(gatewayId, gatewayAddress);
                byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                sendToGatewayPreon32(packetToSend);
                break;
            }
            case MQTTSNPacket.CONNECT:{
                System.out.println("Gateway received a CONNECT message");
                int nodeLength = mqttsnPacket.getMsgVariablePart().length-4;
                byte[] tempName = new byte[nodeLength];
                System.arraycopy(mqttsnPacket.getMsgVariablePart(), 4, tempName, 0, nodeLength);
                String nodeName = new String(tempName);
                nodeSensorMap.put(nodeName, wirelessNodeId);
                response.setCONNACK( 0x00); // Accepted
                byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                sendToGatewayPreon32(packetToSend);
                break;
            }
            case MQTTSNPacket.REGISTER:{ //TO DO: Jika topik sudah pernah di register, langsung return aja jangan bikin baru (Harus bikin map value, key?)
                System.out.println("Gateway received a REGISTER message");
                int topicId = topicIdIncrement;
                topicIdIncrement++;
                int msgId = ((mqttsnPacket.getMsgVariablePart()[2] & 0xFF ) << 8) | (mqttsnPacket.getMsgVariablePart()[3] & 0xFF);

                int topicNameLength = mqttsnPacket.getMsgVariablePart().length-4;
                byte[] tempName = new byte[topicNameLength];
                System.arraycopy(mqttsnPacket.getMsgVariablePart(), 4, tempName, 0, topicNameLength);
                String topicName = new String(tempName);
                topicMap.put(topicId, topicName);
                response.setREGACK(topicId, msgId, 0x00); //Success
                byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                sendToGatewayPreon32(packetToSend);
                break;
            }
            case MQTTSNPacket.PUBLISH:{
                System.out.println("Gateway received a PUBLISH message");
                int topicId = ((mqttsnPacket.getMsgVariablePart()[1] & 0xFF ) << 8) | (mqttsnPacket.getMsgVariablePart()[2] & 0xFF);

                String topicName = topicMap.get(topicId);
                if (topicName == null){ // Ga ketemu mappingnya, jadi harus send PUBACK ke sensor
                    response.setPUBACK(topicId, 0x00, 0x02); // Topic id invalid
                    byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                    sendToGatewayPreon32(packetToSend);
                } else {
                sendTaskQueue.add(mqttsnPacket);
                break;
                }
            }
            default:{
                System.out.println("Gateway received an unsupported message type: "+mqttsnPacket.getMsgVariablePart()[0]);
                break;
            }
        }
    }



    private void initConnectionBroker(){
        Dotenv dotenv = Dotenv.load();
        final String host = dotenv.get("MQTT_HOST");
        final String username = dotenv.get("MQTT_USER");
        final String password = dotenv.get("MQTT_PASS");

        client = MqttClient.builder()
                .useMqttVersion5()
                .serverHost(host)
                .serverPort(8883)
                .sslWithDefaultConfig()
                .buildBlocking();

        client.connectWith()
                .simpleAuth()
                .username(username)
                .password(password.getBytes(UTF_8))
                .applySimpleAuth()
                .send();
        
        System.out.println("Connected to MQTT Broker");
    }

    private void runAggregate() {
        new Thread() {
            public void run() {
                while(true){
                    try{
                        MQTTSNPacket mqttsnPacket = sendTaskQueue.take();

                        int topicId = ((mqttsnPacket.getMsgVariablePart()[1] & 0xFF ) << 8) | (mqttsnPacket.getMsgVariablePart()[2] & 0xFF);
                        String topicName = topicMap.get(topicId);

                        int payloadLength = mqttsnPacket.getMsgVariablePart().length-5;
                        byte[] payloadTemp = new byte[payloadLength];
                        System.arraycopy(mqttsnPacket.getMsgVariablePart(), 5, payloadTemp, 0, payloadLength);
                        String payload = new String(payloadTemp);

                        client.publishWith()
                        .topic(topicName)
                        .payload(UTF_8.encode(payload))
                        .send();
                    } catch (InterruptedException e){
                        throw new Error("Packet failed to send to broker");
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
                        throw new Error("Failed to broadcast ADVERTISE message");
                    }
                }
            }
        }.start();
    }
}
