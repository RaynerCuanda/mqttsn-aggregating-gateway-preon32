public class MQTTSNPacket {
    public static final byte SEARCHGW = 0x01; //Done
    public static final byte GWINFO = 0x02;
    public static final byte CONNECT = 0x04; // Done
    public static final byte CONNACK = 0x05;
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
    
    public MQTTSNPacket(byte[] msgHeader, byte[] msgVariablePart) {
        this.msgHeader = msgHeader;
        this.msgVariablePart = msgVariablePart;
    }

    public MQTTSNPacket createSEARCHGW(int radius){
        int headerLength = 1 + 1; // Length (0), MsgType(1)
        int msgVariablePartLength = 1; //Radius (0)
        
        byte[] msgHeader = new byte[headerLength];
        byte[] msgVariablePart = new byte[msgVariablePartLength];
        
        msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        msgHeader[1] = SEARCHGW;

        msgVariablePart[0] = (byte) radius;

        return new MQTTSNPacket(msgHeader, msgVariablePart);
    }

    public MQTTSNPacket createGWINFO(byte gwId, byte[] gwAddress){
        int headerLength = 1 + 1; // Length (0), MsgType(1)
        int msgVariablePartLength = 1 + gwAddress.length; //GwID (0), GwAdd(1:n)
        
        byte[] msgHeader = new byte[headerLength];
        byte[] msgVariablePart = new byte[msgVariablePartLength];
        
        msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        msgHeader[1] = GWINFO;

        msgVariablePart[0] = gwId;
        System.arraycopy(gwAddress, 0, msgVariablePart, 1, gwAddress.length);

        return new MQTTSNPacket(msgHeader, msgVariablePart);
    }

    public MQTTSNPacket createCONNECT(String clientID, boolean flag_cleansession, boolean flag_will) {
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        int msgVariablePartLength = 1 + 1 + 2 + clientID.length(); //Flags (0), Protocol ID (1), Duration (2 & 3), ClientID (4:n)
        
        byte[] msgHeader = new byte[headerLength];
        byte[] msgVariablePart = new byte[msgVariablePartLength];
        
        msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        msgHeader[1] = CONNECT;

        //di hard code karena hanya ada pada pesan CONNECT
        if (flag_cleansession == true && flag_will == true){ // Both Enabled
                msgVariablePart[0] = 0x0C;
            } else if (flag_cleansession == true && flag_will == false){ // Clean session only
                msgVariablePart[0] = 0x04;
            } else if (flag_cleansession == false && flag_will == true){ // Will only
                msgVariablePart[0] = 0x08;
            } else { //Both disabled
                msgVariablePart[0] = 0x00;
            }

        msgVariablePart[1] =  0x01; 
        msgVariablePart[2] = (byte) ((keepAliveTime >> 8) & 0xFF); // High Byte (MSB)
        msgVariablePart[3] = (byte) (keepAliveTime & 0xFF);        // Low Byte (LSB)
        
        // msgVariablePart[4] = clientID.getBytes().length // Client ID
        // Ga bisa langsung ^^ karena .length return bytes[] tapi ingin dimasukkan ke 1 byte (byte ke-4).
        System.arraycopy(clientID.getBytes(), 0, msgVariablePart, 4, clientID.getBytes().length);   

        return new MQTTSNPacket(msgHeader, msgVariablePart);
    }
}