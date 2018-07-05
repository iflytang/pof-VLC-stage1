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

    /* used for removing tables */
    private byte dp1_global_table_id_1;           // for deviceId = 1
    private byte dp1_global_table_id_2;           // for deviceId = 1

    /* used for deviceId. */
    DeviceId deviceId1 = DeviceId.deviceId("pof:0000000000000001");

    @Activate
    protected void activate() {
        /* register */
        appId = coreService.registerApplication("org.onosproject.pof-VLC");
//        ueRuleService.handlePortStatus();     // now the switch has been initiated with enabled ports, so comment it.

        /* monitor the packet_in packets. */
        packetService.addProcessor(packetProcessor, PacketProcessor.director(2));

        /* send pof_flow_table to deviceId = 1 */
        byte tableId = ueRuleService.send_pof_flow_table(deviceId1, "FirstEntryTable", appId);
        byte next_table_id = ueRuleService.send_pof_flow_table(deviceId1, "AddVlcHeaderTable", appId);
        dp1_global_table_id_1 = tableId;
        dp1_global_table_id_2 = next_table_id;


        try{
            Thread.sleep(1000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before sending flow rules.");
        }

        /* send write_metadata and add_vlc rule in deviceId = 1 */
        ueRuleService.install_pof_write_metadata_from_packet_entry(deviceId1, tableId, next_table_id, "0a000002", 12);
        ueRuleService.install_pof_add_vlc_header_entry(deviceId1, next_table_id, "0a000002", 2, 1,
                (byte) 0x01, (short) 0x0002, (short) 0x0003, (short) 0x0004);

        // for GW (IPL219), add VLC header and forward, downlink
//        ueRuleService.installGatewaySwitchFlowRule("pof:0000000000000002", "192.168.4.169", 2, 1, 10, 11, 12, 13);
        // for AP (OpenWrt132), forward, uplink
//        ueRuleService.installAPFlowRule("pof:0000000000000001",0, "192.168.4.169", 1, 1);
        // for inter_SW (IPL218), remove VLC header and forward, downlink
//        ueRuleService.installUeSwitchFlowRule("pof:0000000000000003", "192.168.4.169", 3, 1);  //  downlink, port2 ==> 220, port3 ==> AP

        // uncomment this for ping, uplink
//        ueRuleService.installGoToTableFlowRule("pof:0000000000000003", 0, 1);
//        ueRuleService.installForwardFlowRule("pof:0000000000000003", 1,"192.168.4.168", 1, 1);  // ue, port1 == eth4, port3 == wlan0
//        ueRuleService.installForwardFlowRule("pof:0000000000000002", 0,"192.168.4.168", 1, 1);  // gw
        try{
            Thread.sleep(1000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before updating flow rules.");
        }
//        ueRuleService.updateGatewaySwitchFlowRule("pof:0000000000000002", "192.168.4.169", 2, 1, 1, 2, 3, 4);
        try{
            Thread.sleep(1 * 1000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before updating rules.");
        }
        log.info("NetwotkBoot Started, appId: {}.", appId);
    }

    @Deactivate
    protected void deactivate() {
        /* remove packet monitor processor */
        packetService.removeProcessor(packetProcessor);

        /* remove tables for deviceId = 1 */
        ueRuleService.remove_pof_flow_table(deviceId1, dp1_global_table_id_1);
        ueRuleService.remove_pof_flow_table(deviceId1, dp1_global_table_id_2);

        log.info("NetworkBoot Stopped, appId: {}.", appId);
    }

    public static int globalTableId() {
        return globalTableId;
    }

    public static ApplicationId appId() {
        return appId;
    }


    protected Map<String, Integer> Mac_LedId = new HashMap<>();     // store maxLedId
    protected Map<String, Integer> Mac_UeId = new HashMap<>();      // store ueId
    protected Map<Integer, Integer> Led_Power = new HashMap<>();    // store led and its power
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
            log.info("packet in from deviceId: {}, port: {}", deviceId, port);

            /**
             * procedure: parse ETH_IPv4_UDP_PAYLOAD
             *          1. request(0x0907): raise UeAssociation event
             *          2. reply(0x0908): assign an ueID, and send back to ue
             *          3. ack1(0x0909): send reply until receiving ack
             *          4. ack2(0x090a): raise VLC_HEADER(0x1918) event
             *          5. data flow: payload does not contain 'type'
             *          6. feedback(0x090b): monitor location and raise VLC_UPDATE event if maxLedId changes
             */
            if (ethernetPacket.getEtherType() == Ethernet.TYPE_IPV4) {

                log.info("==========[ packetIn packet (0x0800)? {}]=========", Integer.toHexString(ethernetPacket.getEtherType()));
                String srcMAC = ethernetPacket.getSourceMAC().toString();   // like "01:02:03:04:05:06"
                String dstMAC = ethernetPacket.getDestinationMAC().toString();

                IPv4 ipv4Packet = (IPv4) ethernetPacket.getPayload();
                String srcIP = Ip4Address.valueOf(ipv4Packet.getSourceAddress()).toString();
                String dstIP = Ip4Address.valueOf(ipv4Packet.getDestinationAddress()).toString();

                // ignore these packets
                if(ipv4Packet.getDestinationAddress() == 0xFFFFFFFF ||
                        ipv4Packet.getDestinationAddress() == 0xE0000016 ||
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
                log.info("2 Ip4Address.valueOf: {}", Ip4Address.valueOf(ipv4Packet.getDestinationAddress()));
                log.info("2 Ip4Address.valueOf.toString: {}", Ip4Address.valueOf(ipv4Packet.getDestinationAddress()).toString());

                /* test whether can build and send reply msg, should comment when no testing */
//                Ethernet ethReply1 = protocolService.buildReply(ethernetPacket,
//                        (short) 0x01, (short) 0x02, (short) Protocol.REPLY);
//                protocolService.sendReply(context, ethReply1);
//                log.info("send reply to deviceId<{}>, port<{}> successfully.", deviceId, port);

                if (ipv4Packet.getProtocol() == IPv4.PROTOCOL_UDP) {
                    UDP udpPacket = (UDP) ipv4Packet.getPayload();

                    // this is meant for the online protocol, dst_port(?)
                    // TODO should update the value of DST_PORT and SRC_PORT in udp
                    if (udpPacket.getDestinationPort() == Protocol.DST_PORT &&
                            udpPacket.getSourcePort() == Protocol.SRC_PORT) {
                        short type = inboundPacket.unparsed().getShort(42);
                        short len = inboundPacket.unparsed().getShort(44);

                        // should send REPLY back, and raise UE_ASSOCIATION
                        if (type == Protocol.REQUEST) {
                            short ueID = inboundPacket.unparsed().getShort(46);
                            short ledID = inboundPacket.unparsed().getShort(48);
                            byte signal = inboundPacket.unparsed().get(49);

                            Mac_LedId.put(srcMAC, (int) ledID);
                            Led_Power.put((int) ledID, (int) signal);

                            // raise UE_ASSOCIATION event, we check ueId, maxLedId, srcIp in handleUeAssociation()
                            NetworkEvent UE_ASSOCIATION = new NetworkEvent(NetworkEvent.Type.UE_ASSOCIATION, "UE_ASSOCIATION",
                                    ueID, ledID, deviceId, srcMAC, srcIP);
                            networkEventService.post(UE_ASSOCIATION);
                            log.info("Post Network Event: {}", UE_ASSOCIATION);

                            // if ueID differs, then send the reply
                            int storedUeId;
                            if (NetworkMonitor.getMacUeId().get(srcMAC) != null) {
                                storedUeId = NetworkMonitor.getMacUeId().get(srcMAC);
                                if (storedUeId != ueID) {
                                    Ethernet ethReply = protocolService.buildReply(ethernetPacket,
                                            ledID, ueID, (short) Protocol.REPLY);
                                    protocolService.sendReply(context, ethReply);

                                    return;
                                }
                            }
                        }

                        // check again
                        if (NetworkMonitor.getMacUeId().get(srcMAC) == null) {
                            log.info("invalid ueID!");
                            return;
                        }
                        log.info("ueID: {} in Mac_ueID: {}", NetworkMonitor.getMacUeId().get(srcMAC),
                                                            NetworkMonitor.getMacUeId());

                        // should send ACK2 back, and raise VLC_HEADER
                        if (type == Protocol.ACK1) {
                            short ueID = inboundPacket.unparsed().getShort(46);
                            short ledID = inboundPacket.unparsed().getShort(48);
                            byte signal = inboundPacket.unparsed().get(49);

                            Mac_LedId.put(srcMAC, (int) ledID);
                            Led_Power.put((int) ledID, (int) signal);

                            // raise VLC_HEADER event, set flow rules to add VLC_header in handle_VLCHeader()
                            NetworkEvent VLC_HEADER = new NetworkEvent(NetworkEvent.Type.VLC_HEADER, "VLC_HEADER",
                                    ueID, ledID, deviceId, srcMAC, srcIP);
                            networkEventService.post(VLC_HEADER);
                            log.info("Post Network Event: {}", VLC_HEADER);

                            Ethernet ethReply = protocolService.buildReply(ethernetPacket,
                                    ledID, ueID, (short) Protocol.ACK2);
                            protocolService.sendReply(context, ethReply);
                        }

                        // should monitor whether storedLedID changes, and raise VLC_UPDATE
                        if (type == Protocol.FEEDBACK) {
                            short ueID = inboundPacket.unparsed().getShort(46);
                            short ledID = inboundPacket.unparsed().getShort(48);
                            byte signal = inboundPacket.unparsed().get(49);

                            int storedLedId, storedPower;
                            if (Mac_LedId.get(srcMAC) != null) {
                                storedLedId = Mac_LedId.get(srcMAC);
                                storedPower = Led_Power.get(storedLedId);

                                // if ledID with max power changes, update VLC_HEADER in handle_VLCUpdate
                                if ((signal > storedPower) && (ledID != storedLedId)) {
                                    NetworkEvent VLC_HEADER_UPDATE = new NetworkEvent(NetworkEvent.Type.VLC_UPDATE,
                                            "VLC_HEADER_UPDATE", ueID, ledID, deviceId, srcMAC, srcIP);
                                    networkEventService.post(VLC_HEADER_UPDATE);
                                    log.info("Post Network Event: {}", VLC_HEADER_UPDATE);

                                    Mac_LedId.put(srcMAC, (int) ledID);
                                    Led_Power.put((int) ledID, (int) signal);
                                } else {
                                    Led_Power.put((int) ledID, (int) signal);
                                }
                            }
                        }
                    }

                    // this is meant for the data flow, udp dst port = 4050
                    if (udpPacket.getDestinationPort() == Protocol.DATA_DST_PORT) {
                        // TODO if we should calculate path, then implement it here
                    }

                }
            }
        }
    }
}