public class encapsulatedMessage{
    private byte[] wirelessNodeId;
    private MQTTSNPacket packet;

    public encapsulatedMessage(byte[] wirelessNodeId, MQTTSNPacket packet){
        this.wirelessNodeId = wirelessNodeId;
        this.packet = packet;
    }

    public byte[] getWirelessNodeId(){
        return this.wirelessNodeId;
    }

    public MQTTSNPacket getMQTTSNPacket(){
        return this.packet;
    }
}
