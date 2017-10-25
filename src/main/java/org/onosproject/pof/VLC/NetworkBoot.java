package org.onosproject.pof.VLC;

import org.apache.felix.scr.annotations.*;
import org.onosproject.core.ApplicationId;
import org.onosproject.core.CoreService;
import org.onosproject.net.DeviceId;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.device.DeviceListener;
import org.onosproject.net.flow.FlowRuleService;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketProcessor;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.table.FlowTableService;
import org.onosproject.net.table.FlowTableStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
//        packetService.addProcessor(packetProcessor, PacketProcessor.director(2));
        try{
            Thread.sleep(1000);
        } catch (Exception e) {
            log.info("sleep wrong in Network before sending flow rules.");
        }
        ueRuleService.installGatewaySwitchFlowRule("pof:0000000000000002", "192.168.2.169", 2, 1, 10, 11, 12, 13);
        ueRuleService.installAPFlowRule("pof:0000000000000001", "192.168.2.169", 1, 1);
        //ueRuleService.installDefaultFlowRule("pof:0000000000000001", "10.0.0.2", 1, 1); // test wifi association
        ueRuleService.installUeSwitchFlowRule("pof:0000000000000003", "192.168.2.169", 2, 1);
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
        //packetService.removeProcessor(packetProcessor);
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
            ueRuleService.handleReactivePacket(context);
        }
    }


}