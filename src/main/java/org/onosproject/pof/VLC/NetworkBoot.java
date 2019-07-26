package org.onosproject.pof.VLC;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
import org.onlab.packet.UDP;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.DefaultPofActions;
import org.onosproject.net.flow.instructions.DefaultPofInstructions;
import org.onosproject.net.packet.*;
import org.onosproject.net.table.FlowTableService;
import org.onosproject.net.table.FlowTableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tsf on 10/11/17.
 *
 * @Description boot the network: add devices, enable ports, setup flow rules
 */

@Component(immediate = true)
public class NetworkBoot {
    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected CoreService coreService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceAdminService adminService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableService flowTableService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore flowTableStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected UeRuleService ueRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected ProtocolService protocolService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkEventService networkEventService;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static int globalTableId = 0;
    private static ApplicationId appId;
    protected ReactivePacketInProcessor packetProcessor =  new ReactivePacketInProcessor();

    /* used for deviceId. */
    static public DeviceId deviceId_gw = DeviceId.deviceId("pof:0000000000000001");
    static public byte gw_table_id_0;
    static public byte gw_table_id_1;

    static public DeviceId deviceId_ap = DeviceId.deviceId("pof:0000000000000002");
    static public byte ap_table_id_0;

    /* used for inner class. */
    protected Map<String, Integer> Mac_LedId = new HashMap<>();     // store maxLedId
    protected Map<String, Integer> Mac_UeId = new HashMap<>();      // store ueId
    //    protected Map<Integer, Integer> Led_Power = new HashMap<>();    // store led and its power
    protected Map<String, Map<Integer, Integer>> Mac_led_power = new HashMap<>();

    /* used for sending back 'reply' to gw. replaced by deviceArray and portArray */
    protected PortNumber replied_port = PortNumber.portNumber(1);   // send 'reply' frame with down-link (light), cannot use wifi port
    protected DeviceId replied_deviceId = deviceId_gw;

    /* used for array[led_id]=deviceId, to update VLC header at different gw. */
    int ledNum = 10;
    public String[] deviceArray = initDeviceArray(new String[ledNum]);
    public int[] portArray = initPortArray(new int[ledNum]);

    @Activate
    protected void activate() {
        /* register */
        appId = coreService.registerApplication("org.onosproject.pof-VLC");
//        ueRuleService.handlePortStatus();     // now the switch has been initiated with enabled ports, so comment it.

        /* monitor the packet_in packets. */
        packetService.addProcessor(packetProcessor, PacketProcessor.director(2));

        /* send pof_flow_table to deviceId = gw, used for add_vlc_header */
        gw_table_id_0 = ueRuleService.send_pof_flow_table(deviceId_gw, "FirstEntryTable", appId);
        gw_table_id_1 = ueRuleService.send_pof_flow_table(deviceId_gw, "AddVlcHeaderTable", appId);

        /* send pof_flow_table to deviceId = wireless_ap, used for packet-in */
        ap_table_id_0 = ueRuleService.send_pof_flow_table_to_wireless_ap(deviceId_ap, "FirstEntryTable", appId);

        try{
            Thread.sleep(1000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before sending flow rules.");
        }

        /* send write_metadata and add_vlc rule in deviceId = gw */
        String dstIP = ueRuleService.ip2HexStr("192.168.1.100");  // 192.168.1.100
        ueRuleService.install_pof_write_metadata_from_packet_entry(deviceId_gw, gw_table_id_0, gw_table_id_1, dstIP, 12);
        ueRuleService.install_pof_add_vlc_header_entry(deviceId_gw, gw_table_id_1, dstIP, 2, 1,
                (short) 0x01, (short) 0x0001, (short) 0x0003, 0x0004);

        /* drop the broadcast frame.
        *  TODO: maybe we have to add more ACL flow rules here, which according to the experiment environment.
        * */
        ueRuleService.install_pof_drop_entry(deviceId_gw, gw_table_id_0, ueRuleService.ip2HexStr("192.168.1.2"), 0xff, 10);
        ueRuleService.install_pof_drop_entry(deviceId_gw, gw_table_id_0, ueRuleService.ip2HexStr("224.0.0.252"), 0xff, 10);

        /* send to wireless ap to test. for test only. */
        /*ueRuleService.install_pof_avoid_packet_in_entry(deviceId_ap, NetworkBoot.ap_table_id_0,
                (short) 0xff, (short) 0x12, (short) 0x11, 12);

        try{
            Thread.sleep(10000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before sending flow rules.");
        }

        ueRuleService.install_pof_avoid_packet_in_entry(deviceId_ap, NetworkBoot.ap_table_id_0,
                (short) 0xff, (short) 0x14, (short) 0x12, 12);*/
        log.info("Activated! appId: {}", appId);
    }

    @Deactivate
    protected void deactivate() {
        /* remove packet monitor processor */
        packetService.removeProcessor(packetProcessor);

        /* remove tables for deviceId = gw */
        ueRuleService.remove_pof_flow_table(deviceId_gw, gw_table_id_0, appId);
        ueRuleService.remove_pof_flow_table(deviceId_gw, gw_table_id_1, appId);

        /* remove tables for deviceId = ap */
        ueRuleService.remove_pof_flow_table(deviceId_ap, ap_table_id_0, appId);

        log.info("NetworkBoot Stopped, appId: {}.", appId);
    }

    public static int globalTableId() {
        return globalTableId;
    }

    /* 'led_id' as index. led connects device. TODO: to update. */
    private String[] initDeviceArray(String[] deviceArray) {

        deviceArray[0] = "pof:0000000000000001";
        deviceArray[1] = "pof:0000000000000001";
        deviceArray[2] = "pof:0000000000000001";
        deviceArray[3] = "pof:0000000000000001";
        deviceArray[4] = "pof:0000000000000001";

        deviceArray[5] = "pof:0000000000000001";
        deviceArray[6] = "pof:0000000000000001";
        deviceArray[7] = "pof:0000000000000001";
        deviceArray[8] = "pof:0000000000000001";
        deviceArray[9] = "pof:0000000000000001";

        return deviceArray;
    }

    /* 'led_id' as index. led connects port. TODO: to update. */
    private int[] initPortArray(int[] portArray) {

        portArray[0] = 2;
        portArray[1] = 2;
        portArray[2] = 2;
        portArray[3] = 2;
        portArray[4] = 2;

        portArray[5] = 2;
        portArray[6] = 2;
        portArray[7] = 2;
        portArray[8] = 2;
        portArray[9] = 2;

        return portArray;
    }

    /**
     *  check ueID whether is the same as the Map<String, Integer> (Mac_UeId). if not, assign new one and store
     *  in the Map; otherwise, return stored UeId.
     * @param checkedUeId to be checked
     * @param ueMAC ue's MAC (srcMAC), <key>
     * @return valid ueId (stored or assigned), <value>
     */
    protected short checkUeID(short checkedUeId, String ueMAC) {

        short ueId = checkedUeId;

        /* check whether ueId is already existing in Map.
         */
        if (Mac_UeId.containsKey(ueMAC)) { // if existed
            ueId = Mac_UeId.get(ueMAC).shortValue();
            log.info("ueId existed: ueId <{}> is valid.", ueId);
            return ueId;
        }

        ueId = (short) ueRuleService.ueIdGenerator(ueMAC); // if not existed
        log.info("assign ueId: {}", ueId);

        /* if has put, no more operation to put again.
         */
        if(Mac_UeId.putIfAbsent(ueMAC, (int) ueId) == null) {
            log.info("store ueId: {} into Mac_UeId: {}", ueId, Mac_UeId);
        }

        /* this 'ueId' is really valid.
         */
        return ueId;
    }

    private boolean TEST = true;
    private Map<String, Integer> ueId_should_reply = new HashMap<>();
    protected class ReactivePacketInProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {

            // if packet has processed, then return
            if(context.isHandled()) {
                return;
            }

            InboundPacket inboundPacket = context.inPacket();
            Ethernet ethernetPacket = inboundPacket.parsed();

            // get deviceId and port, the port maybe not the WIFI port but WAN port
            String deviceId = inboundPacket.receivedFrom().deviceId().toString();
            int port = (int) inboundPacket.receivedFrom().port().toLong();   // through WAN to report to controller
//            log.info("packet in from deviceId: {}, port: {}", deviceId, port);

//            if (TEST) {
//                return;
//            }

            /**
             * procedure: parse ETH_IPv4_UDP_PAYLOAD
             *          1. request(0x0907): check ueID first (assign ID here), then raise UeAssociation event (check whether update)
             *          2. reply(0x0908): send back to ue for every request
             *        //  3. ack1(0x0909): send reply until receiving ack (@deprecated)
             *        //  4. ack2(0x090a): raise VLC_HEADER(0x1918) event (@deprecated)
             *          5. data flow: payload contain 'type' (0x1918)
             *          6. feedback(0x090b): monitor location (raise UeAssociation) and raise VLC_UPDATE event if maxLedId changes (VLC header update)
             */
            if (ethernetPacket.getEtherType() == Ethernet.TYPE_IPV4) {

//                log.info("==========[ packetIn packet (0x0800)? {}]=========", Integer.toHexString(ethernetPacket.getEtherType()));
                String srcMAC = ethernetPacket.getSourceMAC().toString();   // like "01:02:03:04:05:06"
                String dstMAC = ethernetPacket.getDestinationMAC().toString();

                IPv4 ipv4Packet = (IPv4) ethernetPacket.getPayload();
                String srcIP = Ip4Address.valueOf(ipv4Packet.getSourceAddress()).toString();
                String dstIP = Ip4Address.valueOf(ipv4Packet.getDestinationAddress()).toString();

                // ignore these packets
                if(ipv4Packet.getDestinationAddress() == 0xFFFFFFFF ||
                        ipv4Packet.getDestinationAddress() == 0xE0000016 ||
                        ipv4Packet.getDestinationAddress() == 0xE00000FC ||
                        ipv4Packet.getDestinationAddress() == 0xE00000FB ||
                        ipv4Packet.getDestinationAddress() == 0x08080808 ||
                        srcMAC.equals("4E:4F:4F:4F:4F:4F") || dstMAC.equals("4E:4F:4F:4F:4F:4F") ||
                        srcMAC.equals("FF:FF:FF:FF:FF:FF") || dstMAC.equals("FF:FF:FF:FF:FF:FF") ||
                        srcMAC.equals("2C:30:33:F0:E1:34") || dstMAC.equals("01:00:5E:00:00:FB") ||
                        srcMAC.equals("90:E2:BA:28:29:61") || dstMAC.equals("01:00:5E:00:00:16")) {
                    // do nothing
                    return;
                }

                log.info("1 srcMac: {}, dstMac: {}", srcMAC, dstMAC);
                log.info("1 srcIP: {}, dstIP: {}", srcIP, dstIP);
                String agent_src_ip = ueRuleService.ip2HexStr("192.168.1.100");
                srcIP =  agent_src_ip;  // agent
//                log.info("2 Ip4Address.valueOf: {}", Ip4Address.valueOf(ipv4Packet.getDestinationAddress()));
//                log.info("2 Ip4Address.valueOf.toString: {}", Ip4Address.valueOf(ipv4Packet.getDestinationAddress()).toString());

                /* test whether can build and send reply msg, should comment when no testing */
//                Ethernet ethReply1 = protocolService.buildReply(ethernetPacket,
//                        (short) 0x01, (short) 0x02, (short) Protocol.REPLY);
//                protocolService.sendReply(context, ethReply1);
//                log.info("send reply to deviceId<{}>, port<{}> successfully.", deviceId, port);

                if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_UDP) {
                    UDP udpPacket = (UDP) ipv4Packet.getPayload();

                    /** @Subject this is meant for the online protocol, dst_port(?)
                     *  TODO: should update the value of DST_PORT and SRC_PORT in udp (UE broadcast frame)
                     */
                    if (udpPacket.getDestinationPort() == Protocol.DST_PORT /*&&
                            udpPacket.getSourcePort() == Protocol.SRC_PORT*/) {
                        short type = inboundPacket.unparsed().getShort(42);  // udp payload starts from 42B
                        short len = inboundPacket.unparsed().getShort(44);

                        /** @Protocol should send REPLY back, and raise UE_ASSOCIATION
                         */
                        if (type == Protocol.REQUEST) {
                            log.info("in REQUEST.");
                            short parsedUeID = inboundPacket.unparsed().getShort(46);
                            short ledID = inboundPacket.unparsed().getShort(48);
                            byte signal = inboundPacket.unparsed().get(49);

                            /* 1. check parsedUeID and decide whether assign ueID.
                             * returned 'ueId' is really valid. (assign once)
                             * */
                            short ueID = checkUeID(parsedUeID, srcMAC);

                            /* 2. store 'led' and 'max_signal' in request frame.
                             */
                            Mac_LedId.put(srcMAC, (int) ledID);            // location
                            Mac_led_power.put(srcMAC, new HashMap<>());
                            Mac_led_power.get(srcMAC).put((int) ledID, (int) signal);  // location's max power
                            ueId_should_reply.put(srcMAC, 0);

                            /* 3. raise UE_ASSOCIATION event for every request and
                             * send 'reply' to gw (down-link). however, 'packet-in' from wireless AP (up-link).
                             */
                            NetworkEvent UE_ASSOCIATION = new NetworkEvent(NetworkEvent.Type.UE_ASSOCIATION, "FIRST_ASSOCIATION",
                                    ueID, ledID, deviceArray[ledID], 0xff, srcMAC, srcIP);
                            networkEventService.post(UE_ASSOCIATION);
                            log.info("Post Network Event 1: {}", UE_ASSOCIATION);

                            Ethernet ethReply = protocolService.buildReply(ethernetPacket,
                                                                           ledID, ueID, (short) Protocol.REPLY);
                            protocolService.sendReply(context, ethReply, DeviceId.deviceId(deviceArray[ledID]),
                                                      PortNumber.portNumber(portArray[ledID]));

                            /* 4. complete 'request' stage
                             */
                            return;
                        }


                        /** @Protocol should monitor whether storedLedID changes, and raise VLC_HEADER_UPDATE or UE_ASSOCIATION
                         */
                        if (type == Protocol.FEEDBACK) {
                            log.info("in FEEDBACK.");
                            short parsedUeID = inboundPacket.unparsed().getShort(46);
                            short ledID = inboundPacket.unparsed().getShort(48);
                            byte signal = inboundPacket.unparsed().get(49);

                            /* 1. check parsedUeID and decide whether assign ueID.
                             * returned 'ueId' is really valid. (assign once). if
                             * 'ueId' != 'parsedUeId', send 'reply' frame and return.
                             * */
                            short ueID = checkUeID(parsedUeID, srcMAC);
                            if (ueID != parsedUeID) {   // in case that 'feedback' type wrong
                                Mac_LedId.put(srcMAC, (int) ledID);            // location
                                Mac_led_power.put(srcMAC, new HashMap<>());
                                Mac_led_power.get(srcMAC).put((int) ledID, (int) signal);  // location's max power

                                ueId_should_reply.put(srcMAC, 0);

                                NetworkEvent UE_ASSOCIATION = new NetworkEvent(NetworkEvent.Type.UE_ASSOCIATION, "FEEDBACK_ASSOCIATION",
                                        ueID, ledID, deviceArray[ledID], 0xff, srcMAC, srcIP);
                                networkEventService.post(UE_ASSOCIATION);
                                log.info("Post Network Event 2: {}", UE_ASSOCIATION);

                                Ethernet ethReply = protocolService.buildReply(ethernetPacket,
                                        ledID, ueID, (short) Protocol.REPLY);
                                protocolService.sendReply(context, ethReply, DeviceId.deviceId(deviceArray[ledID]),
                                                          PortNumber.portNumber(portArray[ledID]));
                                return;
                            }

                            if (ueId_should_reply.get(srcMAC) == 0) {
                                /* raise UPDATE_VLC_HEADER. update VLC header at gw and output. */
                                NetworkEvent VLC_HEADER_UPDATE = new NetworkEvent(NetworkEvent.Type.VLC_UPDATE,
                                        "VLC_HEADER_UPDATE", ueID, ledID, deviceArray[ledID], portArray[ledID], srcMAC, srcIP);
                                networkEventService.post(VLC_HEADER_UPDATE);
                                log.info("Post Network Event 4: {}", VLC_HEADER_UPDATE);
                                ueId_should_reply.put(srcMAC, 1);
                            }

                            /* 2. check whether 'led_id' changes.
                             * if so, raise UE_ASSOCIATION and VLC_HEADER_UPDATE, clear the old_led_id's power.
                             * if not, return.
                             */
                            int storedLedId = Mac_LedId.get(srcMAC);
                            int storedPower;
                            if (ledID != storedLedId) {  // if not equal
                                /* led_id changes, should clear the old record: old_led's power */
//                                Mac_led_power.get(srcMAC).put(storedLedId, 0);

                                /* put the new record. */
                                Mac_LedId.put(srcMAC, (int) ledID);
                                Mac_led_power.get(srcMAC).put((int) ledID, (int) signal);

                                /* raise UE_ASSOCIATION. update association at wireless AP. */
                                NetworkEvent UE_ASSOCIATION = new NetworkEvent(NetworkEvent.Type.UE_ASSOCIATION, "UPDATE_ASSOCIATION",
                                        ueID, ledID, deviceArray[ledID], 0xff, srcMAC, srcIP);
                                networkEventService.post(UE_ASSOCIATION);
                                log.info("Post Network Event 3: {}", UE_ASSOCIATION);

                                /* raise UPDATE_VLC_HEADER. update VLC header at gw and output. */
                                NetworkEvent VLC_HEADER_UPDATE = new NetworkEvent(NetworkEvent.Type.VLC_UPDATE,
                                        "VLC_HEADER_UPDATE", ueID, ledID, deviceArray[ledID], portArray[ledID], srcMAC, srcIP);
                                networkEventService.post(VLC_HEADER_UPDATE);
                                log.info("Post Network Event 4: {}", VLC_HEADER_UPDATE);

                            } else {
                                /* if the ledID not changes, then avoid_pkt_in with match {ueID, ledID}. */
                                ueRuleService.install_pof_avoid_packet_in_entry(DeviceId.deviceId(deviceId), NetworkBoot.ap_table_id_0,
                                        ueID, ledID, ledID, 12);
                                log.info("Avoid packet-in.");
                            }

                            /* 3. complete 'feedback' stage
                             */
                            return;
                        }
                    }

                    /** @Subjcet this is meant for the data flow, udp dst port = 4050
                     *
                     */
                    if (udpPacket.getDestinationPort() == Protocol.DATA_DST_PORT) {
                        // TODO: what should we consider for the data_flow?
                    }

                }
            }
        }
    }
}