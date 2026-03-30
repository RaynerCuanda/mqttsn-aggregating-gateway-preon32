public class PublishHelper {
    public MQTTSNPacket mqttMessage;
    public long timeSent;

    public PublishHelper(MQTTSNPacket mqttMessage, long timeSent) {
        this.mqttMessage = mqttMessage;
        this. timeSent = timeSent;
    }
}
