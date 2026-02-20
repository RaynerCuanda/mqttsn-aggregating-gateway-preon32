public class testing {
    public static void main (String[] args){
        MQTTSNPacket test = new MQTTSNPacket();
        test.setSEARCHGW(10);
        MQTTSNPacket test2 = new MQTTSNPacket();
        test2.setGWINFO(8, "dress");

        for (int i=0;i<6;i++){
            System.out.println(test);
        }
    }
}
