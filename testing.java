public class testing {
    public static void main (String[] args){

        MQTTSNPacket test2 = new MQTTSNPacket();
        test2.setCONNECT("Testing", true, true);

        MQTTSNPacket test3 = new MQTTSNPacket();
        test3.setCONNACK(0xFF);

        for(int i=0;i<5;i++){
            System.out.println("");
        }
    }
}
