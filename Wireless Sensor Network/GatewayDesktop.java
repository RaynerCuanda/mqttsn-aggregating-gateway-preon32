import java.util.HashMap;
import java.util.LinkedList;
import java.util.Queue;

public class GatewayDesktop {
    private int gatewayId = 0x0001;
    private String gatewayAddress = "0x0001";
    private HashMap<String, Integer> nodeSensorMap;
    private HashMap<Integer, String> topicMap;

    private int topicIdIncrement;

    private void runPortReader() {
        new Thread() {
            public void run() {
                // READ FROM PORT
            }
        }.start();
    }

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
                
                break;
            }
        }
    }

    private void sendToGatewayPreon32(byte[] EncapsulatedMessage){

    }

    private void sendToBroker() {
        Queue<MQTTSNPacket> sendTaskQueue = new LinkedList<>(); 

        new Thread() {
            public void run() {
                //
            }
        }.start();
    }
}
