public class PublishHelper {
    public MQTTSNPacket publishPacket;
    public long timeSend;

    public PublishHelper(MQTTSNPacket publishPacket, long timeSend) {
        this.publishPacket = publishPacket;
        this. timeSend = timeSend;
    }
}
