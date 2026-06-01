package aggregatingpreon32;

public class ClientSession {
    public int wirelessNodeId;
    public long lastSeen;
    public int keepAliveTime; //ms

    public ClientSession(int wirelessNodeId, int keepAliveTime) {
        this.wirelessNodeId = wirelessNodeId;
        this.lastSeen = System.currentTimeMillis();
        this.keepAliveTime = keepAliveTime;
    }

    public void updateLastSeen(){
        this.lastSeen = System.currentTimeMillis();
    }
}
