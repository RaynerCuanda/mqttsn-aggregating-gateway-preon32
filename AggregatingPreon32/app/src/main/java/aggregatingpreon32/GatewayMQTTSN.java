// Cheatsheet bit modification: 
// 0x01 (bit 0), 0x02 (bit 1), 0x04 (bit 2), 0x08 (bit 3)
// 0x10 (bit 4), 0x20 (bit 5), 0x40 (bit 6), 0x80 (bit 7)

package aggregatingpreon32;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import static java.nio.charset.StandardCharsets.UTF_8;
import java.util.Iterator;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import org.eclipse.paho.mqttv5.client.IMqttToken;
import org.eclipse.paho.mqttv5.client.MqttActionListener;
import org.eclipse.paho.mqttv5.client.MqttAsyncClient;
import org.eclipse.paho.mqttv5.client.MqttConnectionOptions;
import org.eclipse.paho.mqttv5.client.persist.MemoryPersistence;
import org.eclipse.paho.mqttv5.common.MqttException;
import org.eclipse.paho.mqttv5.common.MqttMessage;

import com.virtenio.commander.io.DataConnection;
import com.virtenio.commander.toolsets.preon32.Preon32Helper;

// import io.github.cdimascio.dotenv.Dotenv;

public class GatewayMQTTSN {
    private int gatewayId = 0x0001;
    private String gatewayAddress = "0x0001";
    private ConcurrentHashMap<String, Integer> nodeSensorMap = new ConcurrentHashMap<>(); // Client ID Key, Node Sensor Physical Address (WirelessNodeId) Value
    private ConcurrentHashMap<String, MQTTSNClient> nodeSensorTimerMap = new ConcurrentHashMap<>(); // Client ID Key, MQTTSNClient (WirelessNodeId, keepALiveTime, lastSeen)
    private ConcurrentHashMap<Integer, String> topicMap = new ConcurrentHashMap<>(); //Topid ID  key, Topic name Value
    private ConcurrentHashMap<Integer, String> predefinedTopicMap = new ConcurrentHashMap<>(); //Topid ID  key, Topic name Value
    private ConcurrentHashMap<String, Integer> topicReverseMap = new ConcurrentHashMap<>(); //Topid name key, Topic ID Value
    private ConcurrentHashMap<MQTTSNPacket, Integer> waitingQoSMap = new ConcurrentHashMap<>(); // Pesan PUBLISH key, WirelessNodeId value
    
    private ConcurrentHashMap<Integer, Integer> globalIdMap = new ConcurrentHashMap<>(); // WirelessNodeId key, Global ID value
    BlockingQueue<MQTTSNPacket> sendTaskQueue = new LinkedBlockingQueue<>();

    MqttAsyncClient client;
    IMqttToken token;

    
    private int topicIdIncrement = 1;
    
    private final String PORT_NUMBER = "COM4";
    private final int BROADCAST_INTERVAL_SECONDS = 30;
    private final int BROADCAST_ADDRESS = 0xFFFF; //ALAMAT UNTUK BROADCAST
    DataConnection conn;
    BufferedOutputStream out; //For sending message to Preon32
    BufferedInputStream in;

    final String clientId = "raynercuanda";
    final String host_tcp = "tcp://localhost:1883"; 
    final String host = "ssl://b6f0de39dbdb4fbc89413670aabed28a.s1.eu.hivemq.cloud:8883"; 
    final String username = "admin" ;
    final String password = "Admin123"; 

    public static void main(String[] args) {
        new GatewayMQTTSN().run();
    }

    public void run() {
        
        runAggregate(); //  untuk send Publish
        initConnectionBroker(); // Connect ke broker
        handleClientTimeout();
        initIOStream(); // Menjalankan Gateway Preon32 beserta IO-nya
        runPortReader(); // Baca port USART
        runBroadcastConstantly(); // untuk send ADVERTISE
    }   

    private void insertPredefinedMap(){
        predefinedTopicMap.put(1, "9019/Temperature");
        predefinedTopicMap.put(2, "9019/Humidity");
    }

    private void initIOStream() {
        Preon32Helper nodeHelper;
        try {
            nodeHelper = new Preon32Helper(PORT_NUMBER, 115200);
            conn = nodeHelper.runModule("ForwarderMQTTSN");
            out = new BufferedOutputStream(conn.getOutputStream());
            in = new BufferedInputStream(conn.getInputStream());
            System.out.println("IO Stream initialized");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void encapsulateAndSendToNodeSensor(int wirelessNodeId, MQTTSNPacket packet){
        byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(wirelessNodeId, packet.toBytes());
        sendToForwarder(packetToSend);
    }

    private void sendToForwarder(byte[] EncapsulatedMessage){
        try {
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
                        if ((encapsulatedMessage[1] & 0xFF)  == (MQTTSNPacket.ENCAPSULATED_MESSAGE & 0xFF)){ // Kalo msgType = 0xFE (EncapsulatedMessage)
                            handleEncapsulated(encapsulatedMessage);
                        }

                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }.start();
    }

    private void handleClientTimeout() {
        new Thread() {
            public void run() {
                while (true) {
                    try {
                        Iterator<String> mapIterator = nodeSensorTimerMap.keySet().iterator();
    
                        while(mapIterator.hasNext()){
                            String nodeName = mapIterator.next();
                            MQTTSNClient client = nodeSensorTimerMap.get(nodeName);
                            
                            if ((System.currentTimeMillis() - client.lastSeen) / 1000 > client.keepAliveTime){
                                nodeSensorMap.remove(nodeName);
                                nodeSensorTimerMap.remove(nodeName);
                                System.out.println("Removed Client with nodename: "+ nodeName);
                            } else{
                            }
                        }
                        Thread.sleep(1000);
                    } catch (Exception e) {
                    }
                }
            }
        }.start();
    }

    private void handleEncapsulated(byte[] encapsulatedMessage){
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

        MQTTSNPacket response = new MQTTSNPacket();
        switch (mqttsnPacket.getMsgType() & 0xFF){
            case MQTTSNPacket.SEARCHGW:{
                System.out.println("Gateway received a SEARCHGW message");
                response.setGWINFO(gatewayId, gatewayAddress);
                encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                break;
            }
            case MQTTSNPacket.CONNECT:{
                int nodeLength = mqttsnPacket.getMsgHeader()[0]-6;
                byte[] tempName = new byte[nodeLength];
                System.arraycopy(mqttsnPacket.getMsgVarPart(), 4, tempName, 0, nodeLength);
                String nodeName = new String(tempName);
                System.out.println("Gateway received a CONNECT message from: "+nodeName);

                int keepAliveTime = ((mqttsnPacket.getMsgVarPart()[2] & 0xFF ) << 8) | (mqttsnPacket.getMsgVarPart()[3] & 0xFF);
                nodeSensorMap.put(nodeName, wirelessNodeId);
                nodeSensorTimerMap.put(nodeName, new MQTTSNClient(wirelessNodeId, keepAliveTime));
                response.setCONNACK( 0x00); // Accepted
                encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                break;
            }
            case MQTTSNPacket.REGISTER:{
                if (nodeSensorMap.containsValue(wirelessNodeId)){ 
                    System.out.println("Gateway received a REGISTER message");
    
                    int topicId;
                    int msgId = ((mqttsnPacket.getMsgVarPart()[2] & 0xFF ) << 8) | (mqttsnPacket.getMsgVarPart()[3] & 0xFF);
    
                    int topicNameLength = mqttsnPacket.getMsgHeader()[0]-6;
                    byte[] tempName = new byte[topicNameLength];
                    System.arraycopy(mqttsnPacket.getMsgVarPart(), 4, tempName, 0, topicNameLength);
                    String topicName = new String(tempName);
    
                    if (topicMap.containsValue(topicName)){
                        topicId = topicReverseMap.get(topicName);
                    } else { // Kalo belum pernah di daftarin
                        topicId = topicIdIncrement;
                        increaseTopicId();
                        topicMap.put(topicId, topicName);
                        topicReverseMap.put(topicName, topicId);
                    }
                    response.setREGACK(topicId, msgId, 0x00); //Success
                    encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                } else{ // Kalo belum ada di map, suruh DISCONNECT
                    response.setDISCONNECT();
                    encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                }
                break;
            }
            case MQTTSNPacket.PUBLISH:{
                int messageId = (mqttsnPacket.getMsgVarPart()[3] & 0xFF) << 8 | mqttsnPacket.getMsgVarPart()[4] & 0xFF;
                int flags = mqttsnPacket.getMsgVarPart()[0] & 0xFF;
                int qos = (flags & 0x60) >> 5;
                int topicIdType = flags & 0x03;
                
                if (nodeSensorMap.containsValue(wirelessNodeId)){
                    int topicId = (mqttsnPacket.getMsgVarPart()[1] & 0xFF ) << 8 | (mqttsnPacket.getMsgVarPart()[2] & 0xFF);
                    
                    String topicName =  (topicIdType == 0b00) ? topicMap.get(topicId) : predefinedTopicMap.get(topicId);
                    
                    if (topicName == null && topicIdType != 0b10){ // Kalau Tidak Ketemu mapping DAN topicIdType bukan Short Topic Name
                        System.out.println("Gateway received a PUBLISH message, but topic not found. Sending PUBACK");
                        response.setPUBACK(topicId, messageId, 0x02); // Topic id invalid
                        encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                    } else {
                        System.out.println("Gateway received a PUBLISH message, inserting it to queue");
                        //Kalo Messaage ID != 0, artinya butuh alamat node sensor untuk dikirimin PUBACK
                        if (qos == 1 || qos == 2) {
                            waitingQoSMap.put(mqttsnPacket, wirelessNodeId);
                        }
                        sendTaskQueue.add(mqttsnPacket);
                    }
                } else if (qos == 3) { // Kalo belum ada di map, suruh DISCONNECT
                    sendTaskQueue.add(mqttsnPacket);
                } else {
                    response.setDISCONNECT();
                    encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                }
                break;  
            }
            case MQTTSNPacket.PUBREL:{
                try {
                    int messageId = (mqttsnPacket.getMsgVarPart()[0] & 0xFF) << 8 | mqttsnPacket.getMsgVarPart()[1] & 0xFF;
                    System.out.println("Receiving PUBREL from Node Sensor, transmitting PubRel to Broker");
                    MqttActionListener pubcompListener = new MqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            // Kalo berhasil kirim PUBREL ke BROKER dan dapet balesan PUBCOMP dari Broker, kirim ke Node Sensor
                            MQTTSNPacket pubcompPacket = new MQTTSNPacket();
                            pubcompPacket.setPUBCOMP(messageId);
                            encapsulateAndSendToNodeSensor(wirelessNodeId, pubcompPacket);
                            System.out.println("Sending PUBCOMP to Node Sensor with mesage ID: "+messageId);
                        }
                        
                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            // Kalo gagal dapet balesan dari broker.
                            System.out.println("Error sending PubRel to broker: " + exception.getMessage());
                        }
                    };
                    if (globalIdMap.get(wirelessNodeId) != null){
                        client.sendPubRel(globalIdMap.remove(wirelessNodeId), pubcompListener);
                    } else{
                        System.out.println("No mapping for Global ID message.");
                    }
                    break;
                } catch (Exception e) {
                    System.out.println(e.getMessage());
                }
            }
            case 0x14:{
                System.out.println("unhandled: Wireless Node Id > 2"); // kode 0x14 untuk debug Preon32Gateway(Tidak sesuai spec)
                break;
            }
            default:{
                System.out.println("Gateway received an unsupported message type: "+mqttsnPacket.getMsgType() + " with payload: " + new String(mqttsnPacket.getMsgVarPart()));
                break;
            }
        }
    }



    private void initConnectionBroker(){
        try {
            client = new MqttAsyncClient(host_tcp,
            clientId,
            new MemoryPersistence());

            MqttConnectionOptions mqttConnectOptions = new MqttConnectionOptions();
            // mqttConnectOptions.setUserName(username);
            // mqttConnectOptions.setPassword(password.getBytes(UTF_8));

            IMqttToken connectToken = client.connect(mqttConnectOptions);
            connectToken.waitForCompletion();
            System.out.println("Connection to Broker Established.");
        } catch (MqttException e) {
            System.out.println("Failed to connect to broker.");
            e.printStackTrace();
        }

        // Testing
        MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
        topicMap.put(0x01, "Topic Tes");
        mqttsnPacket.setPUBLISH(false, 2, true, 0x00, 0x01, 10, "Success");
        waitingQoSMap.put(mqttsnPacket, 0x01);
        sendTaskQueue.add(mqttsnPacket);

        try {
            MqttActionListener pubcompListener = new MqttActionListener() {
                @Override
                public void onSuccess(IMqttToken asyncActionToken) {
                    // Kalo dapet balesan PUBREC dari Broker, kirim ke Node Sensor
                    System.out.println("Received PUBCOMP from Broker.");
                }
                
                @Override
                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                    System.out.println("Error sending PubRel to broker: " + exception.getMessage());
                }
            };
            Thread.sleep(10000);
            System.out.println("Sending PubRel");

            client.sendPubRel(globalIdMap.remove(0x01), pubcompListener);
        } catch (Exception e) {
        }

    }

    private void runAggregate() {
        new Thread() {
            public void run() {
                while(true){
                    try {
                        MQTTSNPacket mqttsnPacket = sendTaskQueue.take();
                        String topicName;

                        int topicIdType = mqttsnPacket.getMsgVarPart()[0] & 0x03;
                        if (topicIdType == 0b10){ //Short Topic Name
                            char first = (char) mqttsnPacket.getMsgVarPart()[1];
                            char second = (char) mqttsnPacket.getMsgVarPart()[2];
                            topicName = "" + first + second;
                        } else {
                            int topicId = ((mqttsnPacket.getMsgVarPart()[1] & 0xFF ) << 8) | (mqttsnPacket.getMsgVarPart()[2] & 0xFF);
                            topicName = (topicIdType == 0b00) ? topicMap.get(topicId) : predefinedTopicMap.get(topicId);
                        }

                        int messageId = ((mqttsnPacket.getMsgVarPart()[3] & 0xFF ) << 8) | (mqttsnPacket.getMsgVarPart()[4] & 0xFF);
                                                
                        int payloadLength = mqttsnPacket.getMsgHeader()[0]-7; 
                        byte[] payloadTemp = new byte[payloadLength];
                        System.arraycopy(mqttsnPacket.getMsgVarPart(), 5, payloadTemp, 0, payloadLength);
                        String payload = new String(payloadTemp);
                        
                        int flags = mqttsnPacket.getMsgVarPart()[0] & 0xFF;
                        int qosBits = ( flags & 0x60) >> 5; // Ambil bit QoS
                        
                        MqttMessage message = new MqttMessage(payload.getBytes(UTF_8));
                        if (qosBits == 1){
                            message.setQos(1);
                            MqttActionListener pubListener = new MqttActionListener() {
                                @Override
                                public void onSuccess(IMqttToken asyncActionToken) {
                                    // Kalo dapet balesan dari Broker, kirim pesan PUBACK ke Node Sensor
                                    MQTTSNPacket pubackPacket = new MQTTSNPacket();
                                    pubackPacket.setPUBACK(topicId, messageId, 0x00);
                                    encapsulateAndSendToNodeSensor(waitingQoSMap.remove(mqttsnPacket), pubackPacket);
                                }
                                
                                @Override
                                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                    // Kalo gagal dapet balesan dari broker.
                                    MQTTSNPacket pubackPacket = new MQTTSNPacket();
                                    pubackPacket.setPUBACK(topicId, messageId, 0x01);
                                    encapsulateAndSendToNodeSensor(waitingQoSMap.remove(mqttsnPacket), pubackPacket);
                                }
                            };
                            token = client.publish(topicName, message, null, pubListener);
                        } else if (qosBits == 2){
                            MqttActionListener pubrecListener = new MqttActionListener() {
                                @Override
                                public void onSuccess(IMqttToken asyncActionToken) {
                                    // Kalo dapet balesan PUBREC dari Broker, kirim ke Node Sensor
                                    MQTTSNPacket pubrecPacket = new MQTTSNPacket();
                                    pubrecPacket.setPUBREC(messageId);
                                    encapsulateAndSendToNodeSensor(waitingQoSMap.remove(mqttsnPacket), pubrecPacket);
                                    System.out.println("Received Pubrec from Broker and sending it to Node Sensor");
                                }
                                
                                @Override
                                public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                                    // Kalo gagal dapet balesan dari broker.
                                    System.out.println("Error publishing to broker: " + exception.getMessage());
                                    waitingQoSMap.remove(mqttsnPacket);
                                }
                            };
                            message.setQos(2);
                            token = client.publish(topicName, message, null, pubrecListener);
                            // Kalo udah selesai kirim PUBLISH QoS2, PUBREC masuk ke onSuccess/onFailure
                            globalIdMap.put(waitingQoSMap.get(mqttsnPacket), token.getMessageId()); //Supaya nanti PUBREL dari Node Sensor bisa diubah ke Global ID lagi
                        } 
                        else { // untuk QoS -1, 0
                            message.setQos(0);
                            client.publish(topicName, message);
                        }
                    } catch (Exception e){
                        System.err.println("Packet failed to process in aggregate loop: "+e);
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
                        sendToForwarder(packetToSend);
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
