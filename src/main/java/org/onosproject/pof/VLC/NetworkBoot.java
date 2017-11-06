package org.onosproject.pof.VLC;

import org.apache.felix.scr.annotations.*;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
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

    private final Logger log = LoggerFactory.getLogger(getClass());
    private static int globalTableId = 0;
    private static ApplicationId appId;
    protected ReactivePacketInProcessor packetProcessor =  new ReactivePacketInProcessor();

    @Activate
    protected void activate() {

        appId = coreService.registerApplication("org.onosproject.pof-VLC");
        ueRuleService.handlePortStatus();
        try{
            Thread.sleep(1000);
        } catch (Exception e) {
            log.info("Sleep wrong in NetworkBoot before sending flow tables.");
        }
        ueRuleService.handleConnetionUp();
        packetService.addProcessor(packetProcessor, PacketProcessor.director(2));
        try{
            Thread.sleep(1000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before sending flow rules.");
        }
        // for GW (IPL219), add VLC header and forward, downlink
        ueRuleService.installGatewaySwitchFlowRule("pof:0000000000000002", "192.168.4.169", 2, 1, 10, 11, 12, 13);
        // for AP (OpenWrt132), forward, uplink
        ueRuleService.installAPFlowRule("pof:0000000000000001",0, "192.168.4.169", 1, 1);
        //ueRuleService.installDefaultFlowRule("pof:0000000000000001", "10.0.0.2", 1, 1); // test wifi association
       // for inter_SW (IPL218), remove VLC header and forward, downlink
        ueRuleService.installUeSwitchFlowRule("pof:0000000000000003", "192.168.4.169", 2, 1);  //  downlink, port2 ==> 220, port3 ==> AP
        // uncomment this for ping, uplink
//        ueRuleService.installGoToTableFlowRule("pof:0000000000000003", 0, 1);
//        ueRuleService.installForwardFlowRule("pof:0000000000000003", 1,"192.168.4.168", 1, 1);  // ue, port1 == eth4, port3 == wlan0
//        ueRuleService.installForwardFlowRule("pof:0000000000000002", 0,"192.168.4.168", 1, 1);  // gw
        try{
            Thread.sleep(1000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before updating flow rules.");
        }
        //ueRuleService.updateGatewaySwitchFlowRule("pof:0000000000000002", "10.0.0.1", 2, 1, 2, 1, 19, 8);
        try{
            Thread.sleep(1000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before updating rules.");
        }
        //ueRuleService.updateGatewaySwitchFlowRule("pof:0000000000000002", "10.0.0.1", 2, 1, 3, 2, 5, 3);
        log.info("NetwotkBoot Started, appId: {}.", appId);
    }

    @Deactivate
    protected void deactivate() {
        packetService.removeProcessor(packetProcessor);
        ueRuleService.handleConnectionDown();
        log.info("NetworkBoot Stopped, appId: {}.", appId);
    }

    public static int globalTableId() {
        return globalTableId;
    }

    public static ApplicationId appId() {
        return appId;
    }


    // deal with
    protected class ReactivePacketInProcessor implements PacketProcessor {
        @Override
        public void process(PacketContext context) {
            //ueRuleService.handleReactivePacket(context);

            //TODO ========== test packetIn message (0x0800) =========
            /*if(context.isHandled())
                return;

            InboundPacket pkt = context.inPacket();
            DeviceId deviceId = pkt.receivedFrom().deviceId();
            Ethernet packet = pkt.parsed();
            if(packet.getEtherType() == 0x0800) {
                String srcMac = pkt.parsed().getSourceMAC().toString();
                String dstMac = pkt.parsed().getDestinationMAC().toString();
                IPv4 iPv4Packet = (IPv4) packet.getPayload();
                String srcIP = Integer.toHexString(iPv4Packet.getSourceAddress());
                String dstIP = Integer.toHexString(iPv4Packet.getDestinationAddress());

                log.info("==========[packetIn packet]=========");
                log.info("srcMac: {}", srcMac);
                log.info("dstMac: {}", dstMac);
                log.info("srcIP: {}", srcIP);
                log.info("dstIP: {}", dstIP);
            }

            if(context.isHandled()) {
                return;
            }*/

            //TODO =========== handle AP's broadcast message (0x0908) =========
            // get deviceId and port, the port maybe not the WIFI port but WAN port
            InboundPacket inboundPacket = context.inPacket();
            String deviceId = inboundPacket.receivedFrom().deviceId().toString();
            int port = (int) inboundPacket.receivedFrom().port().toLong();   // through WAN to report to controller

            // get srcMAC and ip
            Ethernet ethernetPacket = inboundPacket.parsed();
            if(ethernetPacket.getEtherType() == 0x0908) {
                log.info("[== 0x0908?==> {} AP's BROADCAST ==]", Integer.toHexString(ethernetPacket.getEtherType()));
                String srcMAC = ethernetPacket.getSourceMAC().toString();   // like "01:02:03:04:05:06"
                String dstMAC = ethernetPacket.getDestinationMAC().toString();
                log.info("srcMac: {}", srcMAC);
                log.info("dstMac: {}", dstMAC);

                // ===== get data from unparsed packet =====
                byte ledId1 = inboundPacket.unparsed().get(42);   // return one byte, 0x01 in test
                byte singnal1 = inboundPacket.unparsed().get(43);  // return one byte, 0x02 in test
                byte ledId2 = inboundPacket.unparsed().get(44);  // return one byte, 0x03 in test
                byte singnal2 = inboundPacket.unparsed().get(45); // return one byte, 0x04 in test
                byte maxLedId = 0;
                byte maxSignal = singnal1 > singnal2 ? singnal1 : singnal2;
                log.info("led1: {}", Integer.toHexString(ledId1));
                log.info("signal1: {}", Integer.toHexString(singnal1));
                log.info("led2: {}", Integer.toHexString(ledId2));
                log.info("signal2: {}", Integer.toHexString(singnal2));
                log.info("maxSignal: {}", Integer.toHexString(maxSignal));

                // ==== store the ledId and maxSignal in MAP =====
                Map<Integer, Integer> LED = new HashMap<>();
                LED.put((int) ledId1, (int) singnal1);
                LED.put((int) ledId2, (int) singnal2);
                for(Integer key : LED.keySet()) {
                    if(LED.get(key).byteValue() == maxSignal) {
                        maxLedId = key.byteValue();
                        break;
                    }
                }
                log.info("maxLedId: {}", maxLedId);
            }
        }
    }


}