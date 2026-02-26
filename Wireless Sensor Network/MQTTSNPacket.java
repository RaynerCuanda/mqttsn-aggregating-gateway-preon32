// TO DO: IMPORTANT! LEARN HOW TO PACK SEVERAL MENU to 1 BYTE  (BIT MANIPULATION)
// Asumsi: Header selalu 1 byte.
public class MQTTSNPacket {

    public static final byte ADVERTISE = 0x00; // Done Testing
    public static final byte SEARCHGW = 0x01; //Done Testing
    public static final byte GWINFO = 0x02; // Done Testing
    public static final byte CONNECT = 0x04; // Done, not yet test
    public static final byte CONNACK = 0x05; // Done Testing
    public static final byte REGISTER = 0x0A; // Done
    public static final byte PUBLISH = 0x0C; // Done, not yet test
    public static final byte PUBACK = 0x0D; // Done

    // Tiga ini untuk QoS level 2
    // public static final byte PUBREC = 0x0E;
    // public static final byte PUBREL = 0x0F;
    // public static final byte PUBCOMP = 0x10;

    public static final byte SUBSCRIBE = 0x12; // in Progress: Flags not yet implemented
    public static final byte SUBACK = 0x13; // in Progress: Flags not yet implemented
    public static final byte UNSUBSCRIBE = 0x14;
    // public static final byte UNSUBACK = 0x15;
    // public static final byte PINGREQ = 0x16;
    // public static final byte PINGRESP = 0x17;
    public static final byte DISCONNECT = 0x18;

    private static final int keepAliveTime = 300; // seconds

    private static final byte flags_topicIdType_normal      = (byte) 0x00;
    private static final byte flags_topicIdType_pre         = (byte) 0x01;
    private static final byte flags_topicIdType_short       = (byte) 0x02;
    private static final byte flags_topicIdType_reserved    = (byte) 0x03; 
    private static final byte flags_cleanSession            = (byte) 0x04; 
    private static final byte flags_will                    = (byte) 0x08; 
    private static final byte flags_retain                  = (byte) 0x10; 
    private static final byte flags_QoS_0                   = (byte) 0x00;
    private static final byte flags_QoS_1                   = (byte) 0x20;
    private static final byte flags_QoS_2                   = (byte) 0x40;
    private static final byte flags_QoS_min1                = (byte) 0x60;
    private static final byte flags_DUP                     = (byte) 0x80;

    byte[] msgHeader;
    byte[] msgVariablePart;
    
    public MQTTSNPacket() {
    }

    public byte[] getMsgHeader(){
        return this.msgHeader;
    }

    public byte[] getMsgVariablePart(){
        return this.msgVariablePart;
    }

    public void setADVERTISE(int gwId) {
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        int msgVariablePartLength = 1 + 2; //gwID (0), Duration (1:2)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = ADVERTISE;

        this.msgVariablePart[0] = (byte) gwId; 
        this.msgVariablePart[1] = (byte) ((keepAliveTime >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[2] = (byte) (keepAliveTime & 0xFF);        // Low Byte (LSB) 
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

        byte[] gwAddressBytes = gwAddress.getBytes();
        int msgVariablePartLength = 1 + gwAddressBytes.length; //GwID (0), GwAdd(1:n)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = GWINFO;

        this.msgVariablePart[0] = (byte) gwId;
        System.arraycopy(gwAddressBytes, 0, msgVariablePart, 1, gwAddressBytes.length);   
    }

    public void setCONNECT(String clientID, boolean flag_cleansession, boolean flag_will) {
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        byte[] clientIdBytes = clientID.getBytes();
        int msgVariablePartLength = 1 + 1 + 2 + clientIdBytes.length; //Flags (0), Protocol ID (1), Duration (2 & 3), ClientID (4:n)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = CONNECT;

        byte flags = (byte) 0x00;
        if (flag_will){
            flags |= flags_will;
        }

        if (flag_cleansession){
            flags |= flags_cleanSession;
        } 

        this.msgVariablePart[0] = flags;
        this.msgVariablePart[1] = (byte) 0x01; 
        this.msgVariablePart[2] = (byte) ((keepAliveTime >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[3] = (byte) (keepAliveTime & 0xFF);        // Low Byte (LSB)
        
        // msgVariablePart[4] = clientID.getBytes().length // Client ID
        // Ga bisa langsung ^^ karena .length return bytes[] tapi ingin dimasukkan ke 1 byte (msgVariablePart[4]).
        System.arraycopy(clientIdBytes, 0, msgVariablePart, 4, clientIdBytes.length);   
    }
    
    public void setCONNACK(int returnCode) {
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

        this.msgVariablePart[0] = (byte) returnCode; 
    }

    public void setREGISTER(int topicId, int msgId, String topicName) {
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        byte[] topicNameBytes = topicName.getBytes();
        int msgVariablePartLength = 2 + 2 + topicNameBytes.length; //topicId (0:1), MsgId (2:3), topicName(4:n)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = REGISTER;

        this.msgVariablePart[0] = (byte) ((topicId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[1] = (byte) (topicId & 0xFF);        // Low Byte (LSB) 
        this.msgVariablePart[2] = (byte) ((msgId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[3] = (byte) (msgId & 0xFF);        // Low Byte (LSB) 
        System.arraycopy(topicNameBytes, 0, msgVariablePart, 4, topicNameBytes.length);   
    }

    public void setPUBLISH(boolean dup, int qos, boolean retain, int topicIdType, int topicId, int msgId, String data) {
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        byte[] dataBytes = data.getBytes();
        int msgVariablePartLength = 1 + 2 + 2 + dataBytes.length; //Flags (0), topicId (1:2), msgId(3:4), data(5:n)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = PUBLISH; 
 
        byte flags = (byte) 0x00;
        if (dup){
            flags |= flags_DUP;
        }
        if (qos == 0){
            flags |= flags_QoS_0;
        } else if (qos == 1){
            flags |= flags_QoS_1;
        } else if (qos == 2){
            flags |= flags_QoS_2;
        } else if (qos == -1){
            flags |= flags_QoS_min1;
        }
        if(retain){
            flags |= flags_retain;
        }

        if (topicIdType<=3 && topicIdType>=0){
            if (topicIdType == 0){
                flags |= flags_topicIdType_normal;
            } else if (topicIdType == 1){
                flags |= flags_topicIdType_pre;
            } else if (topicIdType == 2){
                flags |= flags_topicIdType_short;
            } else {
                flags |= flags_topicIdType_reserved;
            }
        } else{
            throw new IllegalArgumentException("Invalid topicIdType");
        }

        this.msgVariablePart[0] = flags; 
        this.msgVariablePart[1] = (byte) ((topicId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[2] = (byte) (topicId & 0xFF);        // Low Byte (LSB) 
        this.msgVariablePart[3] = (byte) ((msgId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[4] = (byte) (msgId & 0xFF);        // Low Byte (LSB) 
        System.arraycopy(dataBytes, 0, msgVariablePart, 5, dataBytes.length);   
    }

    public void setPUBACK(int topicId, int msgId, int returnCode){
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        int msgVariablePartLength = 2 + 2 + 1 ; //topicId (0:1), msgId (2:3), returnCode(4)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = PUBACK;

        this.msgVariablePart[0] = (byte) ((topicId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[1] = (byte) (topicId & 0xFF);        // Low Byte (LSB)
        this.msgVariablePart[2] = (byte) ((msgId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[3] = (byte) (msgId & 0xFF);        // Low Byte (LSB)
        this.msgVariablePart[4] = (byte) returnCode; 
    }

    public void setSUBSCRIBE(boolean dup, int qos, int topicIdType, int msgId, int topicId){
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        int msgVariablePartLength = 1 + 2 + 2 ; // flags (0), msgId (1:2), topicId(3:4)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = SUBSCRIBE;

        byte flags = (byte)0x00;
        if(dup){
            flags |= flags_DUP;
        }
        if (qos == 0){
            flags |= flags_QoS_0;
        } else if (qos == 1){
            flags |= flags_QoS_1;
        } else if (qos == 2){
            flags |= flags_QoS_2;
        } else if (qos == -1){
            flags |= flags_QoS_min1;
        }
        
        if (topicIdType<=3 && topicIdType>=0){
            if (topicIdType == 0){
                flags |= flags_topicIdType_normal;
            } else if (topicIdType == 1){
                flags |= flags_topicIdType_pre;
            } else if (topicIdType == 2){
                flags |= flags_topicIdType_short;
            } else {
                flags |= flags_topicIdType_reserved;
            }
        } else{
            throw new IllegalArgumentException("Invalid topicIdType");
        }
        this.msgVariablePart[0] = flags; 
        this.msgVariablePart[1] = (byte) ((msgId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[2] = (byte) (msgId & 0xFF);        // Low Byte (LSB)
        this.msgVariablePart[3] = (byte) ((topicId >> 8) & 0xFF);
        this.msgVariablePart[4] = (byte) (topicId & 0xFF);
    }

    public void setSUBSCRIBE(boolean dup, int qos, int topicIdType, int msgId, String topicName){
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        byte[] topicNameBytes = topicName.getBytes();
        int msgVariablePartLength = 1 + 2 + topicNameBytes.length ; // flags (0), msgId (1:2), topicName(3:n)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = SUBSCRIBE;

        byte flags = (byte) 0x00;
        if(dup){
            flags |= flags_DUP;
        }
        if (qos == 0){
            flags |= flags_QoS_0;
        } else if (qos == 1){
            flags |= flags_QoS_1;
        } else if (qos == 2){
            flags |= flags_QoS_2;
        } else if (qos == -1){
            flags |= flags_QoS_min1;
        }
        
        if (topicIdType<=3){
            byte tempTopicIdType = (byte) topicIdType;
            flags |= tempTopicIdType;
        } else{
            throw new IllegalArgumentException("Invalid topicIdType");
        }
        this.msgVariablePart[0] = flags;
        this.msgVariablePart[1] = (byte) ((msgId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[2] = (byte) (msgId & 0xFF);        // Low Byte (LSB)
        System.arraycopy(topicNameBytes, 0, msgVariablePart, 3, topicNameBytes.length);  
    }

    public void setSUBACK(int qos, int topicId, int msgId, int returnCode){
        int headerLength = 1 + 1; // Length (0), MsgType (1)
        int msgVariablePartLength = 1 + 2 + 2 + 1 ; // flags (0), topicId (1:2), msgId(3:4), returnCode(5)
        
        this.msgHeader = new byte[headerLength];
        this.msgVariablePart = new byte[msgVariablePartLength];
        
        this.msgHeader[0] = (byte) (msgVariablePartLength + headerLength);
        this.msgHeader[1] = SUBACK;

        byte flags = (byte) 0x00;

        if (qos == 0){
            flags |= flags_QoS_0;
        } else if (qos == 1){
            flags |= flags_QoS_1;
        } else if (qos == 2){
            flags |= flags_QoS_2;
        } else if (qos == -1){
            flags |= flags_QoS_min1;
        }

        this.msgVariablePart[0] = flags; 
        this.msgVariablePart[1] = (byte) ((topicId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[2] = (byte) (topicId & 0xFF);        // Low Byte (LSB)
        this.msgVariablePart[3] = (byte) ((msgId >> 8) & 0xFF); // High Byte (MSB)
        this.msgVariablePart[4] = (byte) (msgId & 0xFF);        // Low Byte (LSB)
        this.msgVariablePart[5] = (byte) returnCode;
    }
}