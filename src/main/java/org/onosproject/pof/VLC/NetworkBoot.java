package org.onosproject.pof.VLC;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onlab.packet.Ip4Address;
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
    protected NetworkEventService networkEventService;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static int globalTableId = 0;
    private static ApplicationId appId;
    protected ReactivePacketInProcessor packetProcessor =  new ReactivePacketInProcessor();

    @Activate
    protected void activate() {

        appId = coreService.registerApplication("org.onosproject.pof-VLC");
//        ueRuleService.handlePortStatus();     // now the switch has been initiated with enabled ports, so comment it.
        ueRuleService.handleConnetionUp();
//        packetService.addProcessor(packetProcessor, PacketProcessor.director(2));
        try{
            Thread.sleep(1000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before sending flow rules.");
        }

        // for GW (IPL219), add VLC header and forward, downlink
        ueRuleService.installGatewaySwitchFlowRule("pof:0000000000000002", "192.168.4.169", 2, 1, 10, 11, 12, 13);
        // for AP (OpenWrt132), forward, uplink
        ueRuleService.installAPFlowRule("pof:0000000000000001",0, "192.168.4.169", 1, 1);
        // for inter_SW (IPL218), remove VLC header and forward, downlink
        ueRuleService.installUeSwitchFlowRule("pof:0000000000000003", "192.168.4.169", 3, 1);  //  downlink, port2 ==> 220, port3 ==> AP

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
//        packetService.removeProcessor(packetProcessor);
        ueRuleService.handleConnectionDown();
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
             * procedure:
             *          1. receive 0x0908, then raise UeAssociation event for per packet
             *          2. receive 0x0800, then raise VLC_header event for packets which have own ueId
             *          3. receive 0x0908, then check whether maxLedId changes; if so, then raise VLC_update event
             */

            //TODO =========== handle AP's broadcast message (0x0908) =========
            // get srcMAC and srcIP
            if(ethernetPacket.getEtherType() == 0x0908) {
                log.info("==========[ packetIn packet (0x0908) ]=========");
                log.info("[==> 0x0908? {} AP's BROADCAST ==]", Integer.toHexString(ethernetPacket.getEtherType()));
                String srcMAC = ethernetPacket.getSourceMAC().toString();   // like "01:02:03:04:05:06"
                String dstMAC = ethernetPacket.getDestinationMAC().toString();
                IPv4 iPv4Packet = (IPv4) ethernetPacket.getPayload();
                String srcIP = Ip4Address.valueOf(iPv4Packet.getSourceAddress()).toString();
                String dstIP = Ip4Address.valueOf(iPv4Packet.getDestinationAddress()).toString();
                log.info("srcMac: {}, dstMac: {}", srcMAC, dstMAC);
                log.info("srcIp: {}, dstIp: {}", srcIP, dstIP);

                // ===== read payload from unparsed packet =====
                // test payload is (0x) 01 02 03 04 01 02 03 04 ...
                short ueId = inboundPacket.unparsed().get(42);      // return one byte, 0x01 in test
                short ledId1 = inboundPacket.unparsed().get(43);    // return one byte, 0x02 in test
                byte signal1 = inboundPacket.unparsed().get(44);  // return one byte, 0x03 in test
                short ledId2 = inboundPacket.unparsed().get(45);    // return one byte, 0x04 in test
                byte signal2 = inboundPacket.unparsed().get(46);  // return one byte, 0x01 in test

                short maxLedId = 0;
                byte maxSignal = signal1 > signal2 ? signal1 : signal2;
                log.info("led1: {}, signal1: {}", Integer.toHexString(ledId1), Integer.toHexString(signal1));
                log.info("led2: {}, signal2: {}", Integer.toHexString(ledId2), Integer.toHexString(signal2));

                // ==== store the ledId and maxSignal in MAP =====
                Map<Integer, Integer> LED = new HashMap<>();
                LED.put((int) ledId1, (int) signal1);
                LED.put((int) ledId2, (int) signal2);
                for(Integer key : LED.keySet()) {
                    if(LED.get(key).byteValue() == maxSignal) {
                        maxLedId = key.byteValue();
                        break;
                    }
                }
                log.info("maxLedId: {}, maxSignal: {}", Integer.toHexString(maxLedId), Integer.toHexString(maxSignal));

                // raise Ue_Association Event here, one packet one Ue_Association event
                // we check ueId, maxLedId, srcIp in handleUeAssociation()
                NetworkEvent Ue_Association = new NetworkEvent(NetworkEvent.Type.UE_ASSOCIATION, "Ue_Association",
                        ueId, maxLedId, deviceId, srcMAC, srcIP);
                networkEventService.post(Ue_Association);
                log.info("Post Network Event: {}", Ue_Association);

                // if ledId changes, raise VLC_Update Event
                int storedLedId;
                if(Mac_LedId.get(srcMAC) != null) {
                    storedLedId = Mac_LedId.get(srcMAC);
                    if(storedLedId != maxLedId) {
                        NetworkEvent VLC_update_ledId = new NetworkEvent(NetworkEvent.Type.VLC_UPDATE, "VLC_update_ledId",
                                ueId, maxLedId, deviceId, srcMAC, srcIP);
                        networkEventService.post(VLC_update_ledId);
                        log.info("Post Network Event: {}", VLC_update_ledId);
                    }
                }
                Mac_LedId.put(srcMAC, (int) maxLedId);

                // check ueId whether assigned by controller, which processed in handleUeAssociation()
                if (ueId != 0xff) {
                    // check NetworkMonitor whether storing <Mac, UeId> in Mac_UeId to avoid fake ueId
                    if(NetworkMonitor.getMacUeId(srcMAC) != null) {
                        Mac_UeId.putIfAbsent(srcMAC, (int) ueId);    // only put once, then never change
                    }
                }
            }

            //TODO ========== test packetIn message (0x0800) =========
            if(ethernetPacket.getEtherType() == 0x0800) {
                String srcMac = ethernetPacket.getSourceMAC().toString();
                String dstMac = ethernetPacket.getDestinationMAC().toString();
                IPv4 iPv4Packet = (IPv4) ethernetPacket.getPayload();
                String srcIP = Ip4Address.valueOf(iPv4Packet.getSourceAddress()).toString();
                String dstIP = Ip4Address.valueOf(iPv4Packet.getDestinationAddress()).toString();

                log.info("==========[ packetIn packet (0x0800) ]=========");
                log.info("1 srcMac: {}, dstMac: {}", srcMac, dstMac);
                log.info("1 srcIP: {}, dstIP: {}", srcIP, dstIP);
                log.info("2 ipv4: {}", iPv4Packet.getSourceAddress());
                log.info("2 Ip4Address.valueOf: {}", Ip4Address.valueOf(iPv4Packet.getDestinationAddress()));
                log.info("2 Ip4Address.valueOf.toString: {}", Ip4Address.valueOf(iPv4Packet.getDestinationAddress()).toString());
                log.info("2 srcIp: {}, dstIp: {}", srcIP, dstIP);

                short ueId = 0xff;
                if(Mac_UeId.get(srcMac) != null) {
                    ueId =  Mac_UeId.get(srcMac).shortValue();    // overwrite
                    if (ueId == 0xff) {
                        return;
                    }
                }
                log.info("ueId: {} in Mac_UeId: {}", ueId, Mac_LedId);

                short maxLedId = 0x00;
                if(Mac_LedId.get(srcMac) != null) {
                    maxLedId = Mac_LedId.get(srcMac).shortValue();  // overwrite
                    if(maxLedId == 0x00) {
                        return;
                    }
                }
                log.info("maxLed: {} in Mac_LedId: {}.", maxLedId, Mac_LedId);

                // raise VLC_header Event
                if(iPv4Packet.getDestinationAddress() == 0xFFFFFFFF ||
                        iPv4Packet.getDestinationAddress() == 0xE0000016 ||
                        iPv4Packet.getDestinationAddress() == 0xE00000FB ||
                        iPv4Packet.getDestinationAddress() == 0x08080808 ||
                        srcMac.equals("4E:4F:4F:4F:4F:4F") || dstMac.equals("4E:4F:4F:4F:4F:4F") ||
                        srcMac.equals("FF:FF:FF:FF:FF:FF") || dstMac.equals("FF:FF:FF:FF:FF:FF") ||
                        srcMac.equals("2C:30:33:F0:E1:34") || dstMac.equals("01:00:5E:00:00:FB") ||
                        srcMac.equals("90:E2:BA:28:29:61") || dstMac.equals("01:00:5E:00:00:16")) {
                    // do nothing
                } else {
                    // show useful packet (0x0800), excluding the packet in if-condition
//                    log.info("==========[ packetIn packet (0x0800) ]=========");
                    log.info("3 srcMac: {}, dstMac: {}", srcMac, dstMac);
                    log.info("3 srcIP: {}, dstIP: {}", srcIP, dstIP);

                    NetworkEvent VLC_header = new NetworkEvent(NetworkEvent.Type.VLC_HEADER, "VLC_header",
                            ueId, maxLedId, deviceId, srcMac, srcIP);
                    networkEventService.post(VLC_header);
                    log.info("Post Network Event: {}", VLC_header);
                }
            }
        }
    }
}