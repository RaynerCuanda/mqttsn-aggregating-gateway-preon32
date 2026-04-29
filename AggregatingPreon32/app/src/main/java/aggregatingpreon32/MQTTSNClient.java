package aggregatingpreon32;

public class MQTTSNClient {
    public int wirelessNodeId;
    public long lastSeen;
    public int keepAliveTime; //ms

    public MQTTSNClient(int wirelessNodeId, int keepAliveTime) {
        this.wirelessNodeId = wirelessNodeId;
        this.lastSeen = System.currentTimeMillis();
        this.keepAliveTime = keepAliveTime;
    }
}
