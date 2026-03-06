package aggregatingpreon32;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

import com.hivemq.client.mqtt.MqttClient;
import com.hivemq.client.mqtt.mqtt5.Mqtt5BlockingClient;

import static com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL;
import static java.nio.charset.StandardCharsets.UTF_8;
import io.github.cdimascio.dotenv.Dotenv;


public class GatewayDesktop {
    private int gatewayId = 0x0001;
    private String gatewayAddress = "0x0001";
    private HashMap<String, Integer> nodeSensorMap;
    private HashMap<Integer, String> topicMap;
    Queue<MQTTSNPacket> sendTaskQueue = new LinkedList<>(); 
    Mqtt5BlockingClient client;

    public static void main(String[] args) {
        new GatewayDesktop().run();
    }

    public void run() {
        runPortReader();
        initConnectionBroker();
        sendToBroker();
    }

    private int topicIdIncrement;

    private void runPortReader() {
        new Thread() {
            public void run() {
                // READ FROM PORT
            }
        }.start();
    }


    // TO DO: ADVERTISE every x minutes.
    private void handleEncapsulatedMessage(byte[] encapsulatedMessage){
        // separate WirelessNodeID and MQTT-SN
        int lenNotMQTTSN = (byte) encapsulatedMessage[0]; // Panjang pesan diluar MQTT-SN
        byte[] wirelessNodeIdTemp = new byte[lenNotMQTTSN - 3]; // Di kurangi length, msgType, ctrl
        System.arraycopy(encapsulatedMessage, 3, wirelessNodeIdTemp, 0, lenNotMQTTSN-3);   

        if (wirelessNodeIdTemp.length != 2){ // Hanya pake hardware address (tidak bisa MAC, dll)
            System.out.println("wirelessNodeId is not hardware Address");
            return;
        } 
        int wirelessNodeId = ((wirelessNodeIdTemp[0] & 0xFF ) << 8) | (wirelessNodeIdTemp[1] & 0xFF);
        byte[] mqttsnPacketByte = new byte[encapsulatedMessage.length - lenNotMQTTSN];
        System.arraycopy(encapsulatedMessage, lenNotMQTTSN, mqttsnPacketByte, 0, encapsulatedMessage.length-lenNotMQTTSN);   


        MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
        mqttsnPacket.toMQTTSN(mqttsnPacketByte);

        MQTTSNPacket response = new MQTTSNPacket();
        switch (mqttsnPacket.getMsgType()){
            case MQTTSNPacket.SEARCHGW:{
                response.setGWINFO(gatewayId, gatewayAddress);
                byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, response.toBytes());
                sendToGatewayPreon32(packetToSend);
                break;
            }
            case MQTTSNPacket.CONNECT:{
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
                sendTaskQueue.add(mqttsnPacket);
                break;
            }
        }
    }

    private void sendToGatewayPreon32(byte[] EncapsulatedMessage){

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
                .password(UTF_8.encode(password))
                .applySimpleAuth()
                .send();
    }


        // TO DO: Check topic Id map 
    private void sendToBroker() {
        new Thread() {
            public void run() {
                while(true){
                    MQTTSNPacket mqttsnPacket = sendTaskQueue.poll();
                    
                    int topicId = ((mqttsnPacket.getMsgVariablePart()[1] & 0xFF ) << 8) | (mqttsnPacket.getMsgVariablePart()[2] & 0xFF);
                    int payloadLength = mqttsnPacket.getMsgVariablePart().length-5;
                    byte[] payloadTemp = new byte[payloadLength];
                    System.arraycopy(mqttsnPacket.getMsgVariablePart(), 5, payloadTemp, 0, payloadLength);

                    String payload = new String(payloadTemp);
                    String topicName = topicMap.get(topicId);
                    client.publishWith()
                        .topic(topicName)
                        .payload(UTF_8.encode(payload))
                        .send();
                }
            }
        }.start();
    }
}
