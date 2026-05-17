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

public class GatewayMQTTSN {
    private int gatewayId = 0x0001;
    private String gatewayAddress = "0x0001";
    private ConcurrentHashMap<String, Integer> nodeSensorMap = new ConcurrentHashMap<>(); // Client ID Key, Node Sensor Physical Address (WirelessNodeId) Value
    private ConcurrentHashMap<Integer, MQTTSNClient> nodeSensorTimerMap = new ConcurrentHashMap<>(); // Client ID Key, MQTTSNClient (WirelessNodeId, keepALiveTime, lastSeen)
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
        insertPredefinedMap();
        runAggregate(); //  untuk send Publish
        initConnectionBroker(); // Connect ke broker
        handleClientTimeout();
        initIOStream(); // Menjalankan Gateway Preon32 beserta IO-nya
        runPortReader(); // Baca port USART
        runBroadcastConstantly(60); // Parameter: interval
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
                        Iterator<Integer> mapIterator = nodeSensorTimerMap.keySet().iterator();
    
                        while(mapIterator.hasNext()){
                            Integer wirelessNodeId = mapIterator.next();
                            MQTTSNClient client = nodeSensorTimerMap.get(wirelessNodeId);
                            
                            long idleTime = (System.currentTimeMillis() - client.lastSeen) / 1000;
                            if (idleTime > client.keepAliveTime){
                                printLogging("INFO", "", "Client Timeout for client id "+wirelessNodeId+": idle for "+idleTime);
                                nodeSensorMap.values().remove(wirelessNodeId);
                                nodeSensorTimerMap.remove(wirelessNodeId);
                                System.out.println("Client Timeout: Removed Client with id: "+ wirelessNodeId);
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

        updateNodeSensorTimer(wirelessNodeId);

        MQTTSNPacket response = new MQTTSNPacket();
        switch (mqttsnPacket.getMsgType() & 0xFF){
            case MQTTSNPacket.SEARCHGW:{
                response.setGWINFO(gatewayId);
                encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                printLogging("Receive from", ""+wirelessNodeId, "SEARCHGW");
                printLogging("SEND to", ""+wirelessNodeId, "GWINFO");
                break;
            }
            case MQTTSNPacket.CONNECT:{
                printLogging("Receive from", ""+wirelessNodeId, "CONNECT");
                int nodeLength = mqttsnPacket.getMsgHeader()[0]-6;
                byte[] tempName = new byte[nodeLength];
                System.arraycopy(mqttsnPacket.getMsgVarPart(), 4, tempName, 0, nodeLength);
                String nodeName = new String(tempName);
                
                if (client.isConnected()){
                    int keepAliveTime = ((mqttsnPacket.getMsgVarPart()[2] & 0xFF ) << 8) | (mqttsnPacket.getMsgVarPart()[3] & 0xFF);
                    nodeSensorMap.put(nodeName, wirelessNodeId);
                    nodeSensorTimerMap.put(wirelessNodeId, new MQTTSNClient(wirelessNodeId, keepAliveTime));
                    response.setCONNACK( 0x00); // Accepted
                    printLogging("SEND to", ""+wirelessNodeId, "CONNACK, returnCode 0x00");
                } else {
                    response.setCONNACK(0x01); // Rejected karena belum terhubung dengan Broker.
                    printLogging("SEND to", ""+wirelessNodeId, "CONNACK, returnCode 0x01");
                }
                encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                break;
            }
            case MQTTSNPacket.REGISTER:{
                if (nodeSensorMap.containsValue(wirelessNodeId)){ 
                    int topicId;
                    int msgId = ((mqttsnPacket.getMsgVarPart()[2] & 0xFF ) << 8) | (mqttsnPacket.getMsgVarPart()[3] & 0xFF);
                    
                    int topicNameLength = mqttsnPacket.getMsgHeader()[0]-6;
                    byte[] tempName = new byte[topicNameLength];
                    System.arraycopy(mqttsnPacket.getMsgVarPart(), 4, tempName, 0, topicNameLength);
                    String topicName = new String(tempName);
                    
                    printLogging("RECEIVE from", ""+wirelessNodeId, "REGISTER with topic name: "+topicName);
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
                    printLogging("SEND to", ""+wirelessNodeId, "REGACK, topic ID: "+topicId+", msg ID:" +msgId);
                } else{ // Kalo belum ada di map, suruh DISCONNECT
                    response.setDISCONNECT();
                    encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                    printLogging("SEND to", ""+wirelessNodeId, "DISCONNECT, node sensor unidentified");
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
                        printLogging("RECEIVE from", ""+wirelessNodeId, "PUBLISH, topic not found");
                        response.setPUBACK(topicId, messageId, 0x02); // Topic id invalid
                        encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                        printLogging("SEND to", ""+wirelessNodeId, "PUBACK, returnCode 0x02");
                    } else {
                        //Kalo Messaage ID != 0, artinya butuh alamat node sensor untuk dikirimin PUBACK
                        if (qos == 1 || qos == 2) {
                            waitingQoSMap.put(mqttsnPacket, wirelessNodeId);
                        }
                        sendTaskQueue.add(mqttsnPacket);
                        printLogging("RECEIVE from", ""+wirelessNodeId, "PUBLISH, inserting to queue");
                    }
                } else if (qos == 3) { // Kalo belum ada di map, suruh DISCONNECT
                    sendTaskQueue.add(mqttsnPacket);
                } else {
                    response.setDISCONNECT();
                    encapsulateAndSendToNodeSensor(wirelessNodeId, response);
                    printLogging("SEND to", ""+wirelessNodeId, "DISCONNECT, node sensor unidentified");
                }
                break;  
            }
            case MQTTSNPacket.PUBREL:{
                try {
                    int messageId = (mqttsnPacket.getMsgVarPart()[0] & 0xFF) << 8 | mqttsnPacket.getMsgVarPart()[1] & 0xFF;
                    printLogging("RECEIVE from", ""+wirelessNodeId, "PUBREL");
                    MqttActionListener pubcompListener = new MqttActionListener() {
                        @Override
                        public void onSuccess(IMqttToken asyncActionToken) {
                            // Kalo berhasil kirim PUBREL ke BROKER dan dapet balesan PUBCOMP dari Broker, kirim ke Node Sensor
                            printLogging("RECEIVE from", "Broker", "PUBCOMP");
                            MQTTSNPacket pubcompPacket = new MQTTSNPacket();
                            pubcompPacket.setPUBCOMP(messageId);
                            encapsulateAndSendToNodeSensor(wirelessNodeId, pubcompPacket);
                            printLogging("SEND to", ""+wirelessNodeId, "PUBCOMP");
                        }
                        
                        @Override
                        public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
                            // Kalo gagal dapet balesan dari broker.
                            System.out.println("Error sending PubRel to broker: " + exception.getMessage());
                        }
                    };
                    if (globalIdMap.get(wirelessNodeId) != null){
                        client.sendPubRel(globalIdMap.remove(wirelessNodeId), pubcompListener);
                        printLogging("SEND to", "BROKER", "PUBREL");

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

    private void updateNodeSensorTimer(int wirelessNodeId){
        MQTTSNClient client = nodeSensorTimerMap.get(wirelessNodeId); 
        if (client != null){
            client.updateLastSeen();
        }
    }

    private void initConnectionBroker(){
        try {
            client = new MqttAsyncClient(host,
            clientId,
            new MemoryPersistence());

            MqttConnectionOptions mqttConnectOptions = new MqttConnectionOptions();
            mqttConnectOptions.setAutomaticReconnect(true);
            mqttConnectOptions.setUserName(username);
            mqttConnectOptions.setPassword(password.getBytes(UTF_8));

            IMqttToken connectToken = client.connect(mqttConnectOptions);
            connectToken.waitForCompletion();
            System.out.println("Connection to Broker Established.");
        } catch (MqttException e) {
            System.out.println("Failed to connect to broker.");
            e.printStackTrace();
        }

        // Testing
            // MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
        // topicMap.put(0x01, "Topic Tes");
        // mqttsnPacket.setPUBLISH(false, 2, true, 0x00, 0x01, 10, "Success");
        // waitingQoSMap.put(mqttsnPacket, 0x01);
        // sendTaskQueue.add(mqttsnPacket);

        // try {
        //     MqttActionListener pubcompListener = new MqttActionListener() {
        //         @Override
        //         public void onSuccess(IMqttToken asyncActionToken) {
        //             // Kalo dapet balesan PUBREC dari Broker, kirim ke Node Sensor
        //             System.out.println("Received PUBCOMP from Broker.");
        //         }
                
        //         @Override
        //         public void onFailure(IMqttToken asyncActionToken, Throwable exception) {
        //             System.out.println("Error sending PubRel to broker: " + exception.getMessage());
        //         }
        //     };
        //     Thread.sleep(10000);
        //     System.out.println("Sending PubRel");

        //     client.sendPubRel(globalIdMap.remove(0x01), pubcompListener);
        // } catch (Exception e) {
        // }

    }

    private void runAggregate() {
        new Thread() {
            public void run() {
                while(true){
                    try {
                        MQTTSNPacket mqttsnPacket = sendTaskQueue.take();
                        String topicName;
                        
                        int topicIdType = mqttsnPacket.getMsgVarPart()[0] & 0x03;
                        int topicId = ((mqttsnPacket.getMsgVarPart()[1] & 0xFF ) << 8) | (mqttsnPacket.getMsgVarPart()[2] & 0xFF);
                        if (topicIdType == 0b10){ //Short Topic Name
                            char first = (char) mqttsnPacket.getMsgVarPart()[1];
                            char second = (char) mqttsnPacket.getMsgVarPart()[2];
                            topicName = "" + first + second;
                        } else {
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
                                    int wirelessNodeId = waitingQoSMap.remove(mqttsnPacket);
                                    encapsulateAndSendToNodeSensor(wirelessNodeId, pubackPacket);
                                    printLogging("RECEIVE from", "Broker", "PUBACK");
                                    printLogging("SEND to", ""+wirelessNodeId, "PUBACK");
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
                                    int wirelessNodeId = waitingQoSMap.remove(mqttsnPacket);
                                    encapsulateAndSendToNodeSensor(wirelessNodeId, pubrecPacket);
                                    printLogging("RECEIVE from", "Broker", "PUBREC");
                                    printLogging("SEND to", ""+wirelessNodeId, "PUBREC");
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
                        printLogging("SEND to", "BROKER", "PUBLISH");
                    } catch (Exception e){
                        System.err.println("Packet failed to process in aggregate loop: "+e);
                    }
                }
            }
        }.start();
    }

    private void runBroadcastConstantly(int interval){
        new Thread(){
            public void run(){
                while (true){
                    try{
                        MQTTSNPacket mqttsnPacket = new MQTTSNPacket();
                        mqttsnPacket.setADVERTISE(gatewayId, MQTTSNPacket.KEEP_ALIVE_TIME);
                        byte[] packetToSend = MQTTSNPacket.toEncapsulatedMessage(BROADCAST_ADDRESS, mqttsnPacket.toBytes());
                        sendToForwarder(packetToSend);
                        printLogging("SEND to", "0", "Broadcasting ADVERTISE");
                        Thread.sleep(interval* 1000);
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

    private void printLogging(String description, String address, String message) {
        String log = String.format("[%-12s: %-6s] %s", description, address, message);
        System.out.println(log);
    }
}
