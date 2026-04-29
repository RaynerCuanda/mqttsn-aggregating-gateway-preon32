package aggregatingpreon32;

public class PublishWrapper {
    public MQTTSNPacket mqttsnMessage;
    public long pubrec_timeSent;
    public int pubrec_counter;

    public final int TIME_TIMEOUT = 15; 
    public final int COUNT_TIMEOUT = 5; 

    public PublishWrapper(MQTTSNPacket mqttsnMessage) {
        this.mqttsnMessage = mqttsnMessage;
        this.pubrec_timeSent = System.currentTimeMillis();
        this.pubrec_counter = 0;
    }
}
