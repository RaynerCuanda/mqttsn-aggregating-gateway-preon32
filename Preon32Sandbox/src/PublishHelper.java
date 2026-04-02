public class PublishHelper {
    public MQTTSNPacket mqttsnMessage;
    public long timeSent;
    public int counter;

    public PublishHelper(MQTTSNPacket mqttsnMessage, long timeSent) {
        this.mqttsnMessage = mqttsnMessage;
        this.timeSent = timeSent;
        this.counter = 0;
    }
}
