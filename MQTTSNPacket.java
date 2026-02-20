public class MQTTSNPacket {

    public static final byte ADVERTISE = 0x00; // Done
    public static final byte SEARCHGW = 0x01; //Done
    public static final byte GWINFO = 0x02; // Done
    public static final byte CONNECT = 0x04; // Done
    public static final byte CONNACK = 0x05; // Done
    public static final byte REGISTER = 0x0A;
    public static final byte PUBLISH = 0x0C;
    public static final byte PUBACK = 0x0D;

    // Tiga ini untuk QoS level 2
    // public static final byte PUBREC = 0x0E;
    // public static final byte PUBREL = 0x0F;
    // public static final byte PUBCOMP = 0x10;

    public static final byte SUBSCRIBE = 0x12;
    public static final byte SUBACK = 0x13;
    public static final byte UNSUBSCRIBE = 0x14;
    public static final byte UNSUBACK = 0x15;
    public static final byte PINGREQ = 0x16;
    public static final byte PINGRESP = 0x17;
    public static final byte DISCONNECT = 0x18;

    public static final int keepAliveTime = 300; // seconds

    byte[] msgHeader;
    byte[] msgVariablePart;
    
    public MQTTSNPacket() {
    }

    public void setSEARCHGW(int radius){
        int headerLength = 1 + 1; // Length (0), MsgType(1)
        int msgVariablePartLength = 1; //Radius (0)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = SEARCHGW;

        this.msgVariablePart[0] = (byte) radius;
    }

    public void setGWINFO(int gwId, String gwAddress){
        int headerLength = 1 + 1; // Length (0), MsgType(1)
        int msgVariablePartLength = 1 + gwAddress.length(); //GwID (0), GwAdd(1:n)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = GWINFO;

        this.msgVariablePart[0] = (byte) gwId;
        System.arraycopy(gwAddress.getBytes(), 0, msgVariablePart, 1, gwAddress.getBytes().length);   
    }

    public void setCONNECT(String clientID, boolean flag_cleansession, boolean flag_will) {
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        int msgVariablePartLength = 1 + 1 + 2 + clientID.length(); //Flags (0), Protocol ID (1), Duration (2 & 3), ClientID (4:n)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = CONNECT;

        //di hard code karena hanya ada pada pesan CONNECT
        if (flag_cleansession == true && flag_will == true){ // Both Enabled
            this.msgVariablePart[0] = 0x0C;
        } else if (flag_cleansession == true && flag_will == false){ // Clean session only
            this.msgVariablePart[0] = 0x04;
        } else if (flag_cleansession == false && flag_will == true){ // Will only
            this.msgVariablePart[0] = 0x08;
        } else { //Both disabled
            this.msgVariablePart[0] = 0x00;
        }

        this.msgVariablePart[1] =  (byte) 0x01; 
        this.msgVariablePart[2] = (byte) ((keepAliveTime >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[3] = (byte) (keepAliveTime & 0xFF);        // Low Byte (LSB)
        
        // msgVariablePart[4] = clientID.getBytes().length // Client ID
        // Ga bisa langsung ^^ karena .length return bytes[] tapi ingin dimasukkan ke 1 byte (msgVariablePart[4]).
        System.arraycopy(clientID.getBytes(), 0, msgVariablePart, 4, clientID.getBytes().length);   
    }

    public void setADVERTISE(byte gwId) {
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        int msgVariablePartLength = 1 + 2; //gwID (0), Duration (1:2)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = ADVERTISE;

        this.msgVariablePart[0] = gwId; 
        this.msgVariablePart[1] = (byte) ((keepAliveTime >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[2] = (byte) (keepAliveTime & 0xFF);        // Low Byte (LSB) 
    }
    
    public void setCONNACK(byte returnCode) {
        //RETURN CODE NYA LIHAT TABEL DI PDF,
        //  0x00 Accepted
        // 0x01 Rejected: congestion
        // 0x02 Rejected: invalid topic ID
        // 0x03 Rejected: not supported
        // 0x04 - 0xFF reserved
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        int msgVariablePartLength = 1; //returnCode (0)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = CONNACK;

        this.msgVariablePart[0] = returnCode; 
    }

    public void setREGISTER(int topicId, int msgId, String topicName) {
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        int msgVariablePartLength = 2 + 2 + topicName.length(); //topicId (0:1), MsgId (2:3), topicName(4:n)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = REGISTER;

        this.msgVariablePart[0] = (byte) ((topicId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[1] = (byte) (topicId & 0xFF);        // Low Byte (LSB) 
        this.msgVariablePart[2] = (byte) ((msgId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[3] = (byte) (msgId & 0xFF);        // Low Byte (LSB) 
        System.arraycopy(topicName.getBytes(), 0, msgVariablePart, 4, topicName.getBytes().length);   
    }


}