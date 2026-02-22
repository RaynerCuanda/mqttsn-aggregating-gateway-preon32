import java.util.LinkedList;
import java.util.Queue;

// ANGGAP BROKER PASTI SAMA

public class MQTTSNAggregateConnection implements Runnable {
    @Override
    public void run() {
        Queue<MQTTSNPacket> sendTaskQueue = new LinkedList<>(); 


        while(true){
            if(sendTaskQueue.peek() != null){
                MQTTSNPacket packet = sendTaskQueue.poll();

                //CEK KONEKSI KE BROKER

                // SEND TASK KE BROKER
            }
        }
    }
}
