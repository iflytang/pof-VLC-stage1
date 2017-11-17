package org.onosproject.pof.VLC;

import java.util.*;

/**
 * Created by tsf on 10/11/17.
 */
public class Test {

    private static int x;
    {x = 12;}

    {x = 13;}

    public static int getX() {
        return x;
    }

    public String ip2HexStr(String ip) {
        String[] ipArray = ip.split("\\.");
        String[] tempIp = new String[4];
        String ipHexStr = "";
        for(int i = 0; i < 4; i++) {
            tempIp[i] = Integer.toHexString(Integer.parseInt(ipArray[i], 10));
            if(tempIp[i].length() < 2) {
                tempIp[i] = "0" + tempIp[i];
            }
            ipHexStr += tempIp[i];
        }
        return ipHexStr;
    }

    public Map<Integer, Integer> getMaxSignal(short ledId1, byte singnal1, short ledId2, byte singnal2, short ledId3, byte singnal3) {
        // get the max signal value and its ledId
        short maxLedId = 0;
        byte maxSignal = 0;
        Map<Integer, Integer> LED = new HashMap<>();
        LED.put(Integer.valueOf(ledId1), Integer.valueOf(singnal1));
        LED.put(Integer.valueOf(ledId2), Integer.valueOf(singnal2));
        LED.put(Integer.valueOf(ledId3), Integer.valueOf(singnal3));
        byte temp = singnal1 > singnal2 ? singnal1 : singnal2;
        maxSignal = temp > singnal3 ? temp : singnal3;
        for(Integer key : LED.keySet()) {
            //System.out.println("LED.get(key).equals(maxSignal): " + LED.get(key).compareTo(Integer.valueOf(maxSignal)));
            if(LED.get(key).shortValue() == (maxSignal)) {
                maxLedId = key.shortValue();
                System.out.println("key: " + maxLedId);
                break;
            }
        }
        System.out.println("LedId: " + maxLedId + ", Signal: " + maxSignal);
        return new HashMap<>(Integer.valueOf(maxLedId), Integer.valueOf(maxSignal));
    }

    public String short2HexStr(short shortNum) {
        String hexStr = Integer.toHexString(shortNum);
        if(hexStr.length() < 2) {
            hexStr = "0" + hexStr;
        }
        return hexStr;
    }

    public String int2HexStr(int intNum) {
        String hexStr = Integer.toHexString(intNum);
        int len = hexStr.length();
        if(hexStr.length() < 4) {
            for(int i = 0; i < 4 - len;i++) {
                hexStr = "0" + hexStr;

            }
        }
        return hexStr;
    }

    public void testPriorityQueue() {
        PriorityQueue<Integer> integers = new PriorityQueue<>();
        Random random = new Random();
        for(int i = 0; i < 7; i++) {
            integers.add(random.nextInt(100));
        }
        for(int i = 0; i < 7; i++) {
            Integer integer = integers.poll();
            System.out.println("poll number" + i + ": " + integer);
        }
    }

    public String toHexTimeSlot(List<Integer> timeSlotList) {
        int timeSlot =  0x0000;
        int flag =  0x0080;
        String hextimeSlot = "00";   // 8b00 00 00 00 => hex: 0x00

        for(Integer slot : timeSlotList) {
            switch (slot) {
                case 1:
                    timeSlot += (flag >> 1); // 01 00 00 00
                    flag = 0x0080;   // reset
                    continue;
                case 2:
                    timeSlot += (flag >> 3);
                    flag = 0x0080;
                    continue;
                case 3:
                    timeSlot += (flag >> 5);
                    flag = 0x80;
                    continue;
                case 4:
                    timeSlot += (flag >> 7);
                    flag = 0x80;
                    continue;
            }
        }

        hextimeSlot = Integer.toHexString(timeSlot );

        return hextimeSlot;
    }

    public static void main(String[] args) {
        /*int num = 129;
        System.out.println(Integer.toHexString(num));*/

        Test test = new Test();
        Dijkstra dijkstra = new Dijkstra();
        /*String ip = test.ip2HexStr("109.0.0.1");
        System.out.println("ip2HexStr: " + ip);*/

//        System.out.println("static x: " + getX());

//        System.out.println("Integer.toHexString: " + Integer.toHexString(123));

        // test invert two byte into one short
        /*byte b1 = 1;
        byte b2 = 1;
        short b3 = (short) ((b1 << 8) + b2);
        short b4 = 0x0101;
        System.out.println("b3 = " + b3 + ",b4 = " + b4);*/

        /*Map<Integer, Integer> maxSignal = new HashMap<>();
        test.getMaxSignal((short) 1, (byte) 9,(short) 2,(byte) 9,(short) 9 ,(byte) 9);*/

        // test short inverted to HexString
        /*String shortHexStr = test.short2HexStr((short) 1);
        System.out.println("short2HexStr: " + shortHexStr);
        String intHexStr = test.int2HexStr(1);
        System.out.println("int2HexStr: " + intHexStr);*/

        // test compareTo
        /*Integer a = 12;
        Integer b = 13;
        System.out.println("a < b ? : " + a.compareTo(b));*/

        // test the natural order of attribute of PriorityQueue, ascending sort
        /*test.testPriorityQueue();*/

        // test dijkstra ==> [ok]
//        List<Integer> path = dijkstra.getShortestPath(1, 6);
//        System.out.println("path: " + path.toString());

        // test toHexTimeSlot ==> [ok]
        List<Integer> timeSlot = new ArrayList<>();
        timeSlot.add(1);
        timeSlot.add(2);
        timeSlot.add(3);
        timeSlot.add(4);
        String hexTimeSlot = test.toHexTimeSlot(timeSlot);
        System.out.println("time slot " + timeSlot + " ==> hex time slot [" + hexTimeSlot + "]");
    }
}
