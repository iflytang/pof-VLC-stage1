package org.onosproject.pof.VLC;

import org.apache.felix.scr.annotations.*;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.Ethernet;
import org.onlab.packet.IPv4;
import org.onosproject.core.ApplicationId;
import org.onosproject.floodlightpof.protocol.OFMatch20;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.floodlightpof.protocol.table.OFFlowTable;
import org.onosproject.floodlightpof.protocol.table.OFTableType;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.device.DeviceAdminService;
import org.onosproject.net.flow.*;
import org.onosproject.net.flow.criteria.Criteria;
import org.onosproject.net.flow.criteria.Criterion;
import org.onosproject.net.flow.instructions.DefaultPofActions;
import org.onosproject.net.flow.instructions.DefaultPofInstructions;
import org.onosproject.net.packet.InboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketService;
import org.onosproject.net.table.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tsf on 10/9/17.
 *
 * @Description implements UeRuleService
 */

@Component(immediate = true)
@Service
public class UeRule implements UeRuleService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableService flowTableService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowTableStore flowTableStore;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected FlowRuleService flowRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected DeviceAdminService deviceAdminService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    /**
     * Field name and field id
     * 1. DIP dst_ip
     * 2. VLC Header: LEDID + UID + TIMESLOT + SERVICEID
     */
    public static final int DIP = 1;
    public static final int SERVICEID = 2;
    public static final int TIMESLOT = 3;
    public static final int UID = 4;
    public static final int LEDID = 5;


    protected int gloablTableId = NetworkBoot.globalTableId();
    protected ApplicationId appId = NetworkBoot.appId();
    protected short lastUeId = 0;  // used for ue's id assignment

    private final Logger log = LoggerFactory.getLogger(getClass());


    @Override
    public void installInterSwitchFlowRule(String deviceId, String dstIp, int outPort, int DIP) {
        // match dstIp{240b, 32b}, no VLC header here
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 240, (short) 32, ip2HexStr(dstIp), "ffFFffFF"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installInterFlowRule==] match: {}.", matchList);

        // action: forward
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outPort).action();
        actions.add(action_outport);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));
        log.info("[==installInterFlowRule==] action: {}.", actions);

        // apply flow rule to switch, globalTableId = 0 by default
        long newFlowEntryId = flowTableStore.getNewFlowEntryId(DeviceId.deviceId(deviceId), gloablTableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId(deviceId))
                .forTable(gloablTableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withCookie(newFlowEntryId)
                .withPriority(1)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());
        log.info("[==installInterFlowRule==] applyRuleService deviceId: {} + globalTableId: {}.", deviceId, gloablTableId);
    }

    // TODO How to get UeId from handleUeAssociation()
    @Override
    public void installGatewaySwitchFlowRule(String deviceId, String dstip, int outPort, int DIP,
                                             int ledId, int ueId, int timeSlot, int serviceId) {
        // match dstIp{240b, 32b}, will add VLC header
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 240, (short) 32, ip2HexStr(dstip), "ffFFffFF"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installGWFlowRule==] match: {}.", matchList);

        // action: add VLC header(8B) before IP packets and forward to UE, now dstIP will be {288b, 32b}
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        OFAction action_add_ServiceId = DefaultPofActions.addField((short) SERVICEID, (short) 112, (short) 8, short2HexStr((short) serviceId)).action();
        OFAction action_add_TimeSlot = DefaultPofActions.addField((short) TIMESLOT, (short) 112, (short) 8, short2HexStr((short) timeSlot)).action();
        OFAction action_add_UeId = DefaultPofActions.addField((short) UID, (short) 112, (short) 16, int2HexStr(ueId)).action();
        OFAction action_add_LEDID = DefaultPofActions.addField((short) LEDID, (short) 112, (short) 16, int2HexStr(ledId)).action();
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outPort).action();
        actions.add(action_add_ServiceId);
        actions.add(action_add_TimeSlot);
        actions.add(action_add_UeId);
        actions.add(action_add_LEDID);
        actions.add(action_outport);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));
        log.info("[==installGWFlowRule==] action: {}.", actions);

        // apply flow rule to switch, globalTableId = 0 by default
        long newFlowEntryId = flowTableStore.getNewFlowEntryId(DeviceId.deviceId(deviceId), gloablTableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId(deviceId))
                .forTable(gloablTableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withPriority(1)
                .withCookie(newFlowEntryId)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());
        log.info("[==installGWFlowRule==] applyRuleService deviceId: {} + globalTableId: {}.", deviceId, gloablTableId);
    }

    @Override
    public void updateGatewaySwitchFlowRule(String deviceId, String dstip, int outPort, int DIP,
                                            int ledId, int ueId, int timeSlot, int serviceId) {
        // match dstIp{240b, 32b}, will add VLC header
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 240, (short) 32, ip2HexStr(dstip), "ffFFffFF"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));

        // action: add VLC header(8B) before IP packets and forward to UE, now dstIP will be {288b, 32b}
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        OFAction action_add_ServiceId = DefaultPofActions.addField((short) SERVICEID, (short) 112, (short) 8, short2HexStr((short) serviceId)).action();
        OFAction action_add_TimeSlot = DefaultPofActions.addField((short) TIMESLOT, (short) 112, (short) 8, short2HexStr((short) timeSlot)).action();
        OFAction action_add_UeId = DefaultPofActions.addField((short) UID, (short) 112, (short) 16, int2HexStr(ueId)).action();
        OFAction action_add_LEDID = DefaultPofActions.addField((short) LEDID, (short) 112, (short) 16, int2HexStr(ledId)).action();
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outPort).action();
        actions.add(action_add_ServiceId);
        actions.add(action_add_TimeSlot);
        actions.add(action_add_UeId);
        actions.add(action_add_LEDID);
        actions.add(action_outport);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));

        // get existed flow rules in flow table. if the dstIp equals, then delete it
        Map<Integer, FlowRule> existedFlowRules = new HashMap<>();
        existedFlowRules = flowTableStore.getFlowEntries(DeviceId.deviceId(deviceId), FlowTableId.valueOf(gloablTableId));
        if(existedFlowRules != null) {
            for(Integer flowEntryId : existedFlowRules.keySet()) {
                log.info("existedFlowRules.get(flowEntryId).selector().equals(trafficSelector.build()) ==> {}",
                        existedFlowRules.get(flowEntryId).selector().equals(trafficSelector.build()));
                if(existedFlowRules.get(flowEntryId).selector().equals(trafficSelector.build())) {
                    flowTableService.removeFlowEntryByEntryId(DeviceId.deviceId(deviceId), gloablTableId, flowEntryId);
                }
            }
        }

        // update new flow rule for switch, globalTableId = 0 by default
        long newFlowEntryId = flowTableStore.getNewFlowEntryId(DeviceId.deviceId(deviceId), gloablTableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId(deviceId))
                .forTable(gloablTableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withPriority(1)
                .withCookie(newFlowEntryId)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());

    }

    @Override
    public void installAPFlowRule(String deviceId,int tableId, String dstip, int outport, int DIP) {
        // match dstIp {240b, 32b}
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 240, (short) 32, ip2HexStr(dstip), "ffffffff"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installAPFlowRule==] match dstIP.");

        // action: (1)remove VLC Header; (2)forward to server; (3)no action to deal with the broadcast packets, so PacketIn
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
       // OFAction action_remove_VLC = DefaultPofActions.deleteField(112, 48).action(); // VLCHeader{0, 48} is 6B in the front of IP packets
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outport).action();
       // actions.add(action_remove_VLC);
        actions.add(action_outport);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));
        log.info("[==installAPFlowRule==] action: {}.", actions);

        // apply flow rules to AP, globalTableId = 0 by default
        long newFlowEntryId = flowTableStore.getNewFlowEntryId(DeviceId.deviceId(deviceId), gloablTableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId(deviceId))
                .forTable(tableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withPriority(1)
                .withCookie(newFlowEntryId)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());
        log.info("[==installAPFlowRule==] applyRuleService {} + tableId {}.",deviceId, tableId);
    }

    @Override
    public void installUeSwitchFlowRule(String deviceId, String dstIp, int outPort, int DIP) {
        // match dstIp{240b, 32b}, no VLC header here
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 288, (short) 32, ip2HexStr(dstIp), "ffFFffFF"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installInterFlowRule==] match: {}.", matchList);

        // action: forward
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        OFAction action_remove_VLC = DefaultPofActions.deleteField(112, 48).action();
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outPort).action();
        actions.add(action_remove_VLC);
        actions.add(action_outport);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));
        log.info("[==installInterFlowRule==] action: {}.", actions);

        // apply flow rule to switch, globalTableId = 0 by default
        long newFlowEntryId = flowTableStore.getNewFlowEntryId(DeviceId.deviceId(deviceId), gloablTableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId(deviceId))
                .forTable(gloablTableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withCookie(newFlowEntryId)
                .withPriority(1)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());
        log.info("[==installInterFlowRule==] applyRuleService deviceId: {} + globalTableId: {}.", deviceId, gloablTableId);
    }

    @Override
    public void installGoToTableFlowRule(String deviceId, int tableId, int goToTableId) {
        // match dstIp{240b, 32b}, no VLC header here
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 288, (short) 32, "00000000", "00000000"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installInterFlowRule==] match: {}.", matchList);

        // action: forward
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        trafficTreatment.add(DefaultPofInstructions.gotoTable((byte) goToTableId, (byte) 0, (byte) 0, new ArrayList<OFMatch20>()));
        log.info("[==installInterFlowRule==] action: {}.", actions);

        // apply flow rule to switch, globalTableId = 0 by default
        long newFlowEntryId = flowTableStore.getNewFlowEntryId(DeviceId.deviceId(deviceId), gloablTableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId(deviceId))
                .forTable(tableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withCookie(newFlowEntryId)
                .withPriority(0)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());
    }

    @Override
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

    @Override
    public String short2HexStr(short shortNum) {
        String hexStr = Integer.toHexString(shortNum);
        if(hexStr.length() < 2) {
            hexStr = "0" + hexStr;
        }
        return hexStr;
    }

    @Override
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

    @Override
    public List<DeviceId> getDeviceList() {
        List<DeviceId> deviceIdList = new ArrayList<>();
        deviceIdList.add(DeviceId.deviceId("pof:0000000000000001"));  // deviceId for AP
        deviceIdList.add(DeviceId.deviceId("pof:0000000000000002"));  // deviceId for pof-switch
        deviceIdList.add(DeviceId.deviceId("pof:0000000000000003"));
        return deviceIdList;
    }

    @Override
    public void handleConnetionUp() {
        appId = NetworkBoot.appId();
        List<DeviceId> deviceIdList = getDeviceList();
        log.info("appId ==> {}\n DIP ==> {}\n deviceList: {}", appId, DIP, deviceIdList.toString());

        //construct OFMatch20
        OFMatch20 DIP_with_VLC = new OFMatch20();
        DIP_with_VLC.setFieldName("DIP_VLC");
        DIP_with_VLC.setFieldId((short) DIP);
        DIP_with_VLC.setOffset((short) 288);
        DIP_with_VLC.setLength((short) 32);

        OFMatch20 DIP_without_VLC = new OFMatch20();
        DIP_without_VLC.setFieldName("DIP");
        DIP_without_VLC.setFieldId((short) DIP);
        DIP_without_VLC.setOffset((short) 240);
        DIP_without_VLC.setLength((short) 32);

        ArrayList<OFMatch20> match_DIP_with_VLC = new ArrayList<>();
        match_DIP_with_VLC.add(DIP_with_VLC);

        ArrayList<OFMatch20> match_DIP_without_VLC = new ArrayList<>();
        match_DIP_without_VLC.add(DIP_without_VLC);

        for(DeviceId deviceId : deviceIdList) {
            int smallTableId = flowTableStore.parseToSmallTableId(deviceId, gloablTableId);
            byte tableId = (byte) flowTableStore.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);
            byte tableId1 = (byte) flowTableStore.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);

            if(deviceId.equals(DeviceId.deviceId("pof:0000000000000003"))) {
                // =========== tableId-0 =================
                // construct OFMatch FlowTable with VLC
                OFFlowTable ofFlowTable_with_VLC = new OFFlowTable();
                ofFlowTable_with_VLC.setTableId(tableId);
                ofFlowTable_with_VLC.setTableName("FirstEntryTable");
                ofFlowTable_with_VLC.setMatchFieldNum((byte) 1);
                ofFlowTable_with_VLC.setTableSize(32);
                ofFlowTable_with_VLC.setTableType(OFTableType.OF_MM_TABLE);
                ofFlowTable_with_VLC.setCommand(null);
                ofFlowTable_with_VLC.setKeyLength((byte) 32);
                ofFlowTable_with_VLC.setMatchFieldList(match_DIP_with_VLC);

                // send flow table to AP when connected up
                FlowTable.Builder flowTable0 = DefaultFlowTable.builder()
                        .withFlowTable(ofFlowTable_with_VLC)
                        .forDevice(deviceId)
                        .forTable(tableId)  // tableId = 0
                        .fromApp(appId);
                flowTableService.applyFlowTables(flowTable0.build());
                log.info("flowTableService to pof:0000000000000003 tableId-0?-{}: {}", tableId, flowTable0.build());

                // =========== tableId-1 =================
                // construct OFMatch FlowTable without VLC
                OFFlowTable ofFlowTable_without_VLC = new OFFlowTable();
                ofFlowTable_without_VLC.setTableId(tableId1);
                ofFlowTable_without_VLC.setTableName("SecondEntryTable");
                ofFlowTable_without_VLC.setMatchFieldNum((byte) 1);
                ofFlowTable_without_VLC.setTableSize(32);
                ofFlowTable_without_VLC.setTableType(OFTableType.OF_MM_TABLE);
                ofFlowTable_without_VLC.setCommand(null);
                ofFlowTable_without_VLC.setKeyLength((byte) 32);
                ofFlowTable_without_VLC.setMatchFieldList(match_DIP_without_VLC);

                // send flow table to switches when connected up
                FlowTable.Builder flowTable1 = DefaultFlowTable.builder()
                        .withFlowTable(ofFlowTable_without_VLC)
                        .forDevice(deviceId)
                        .forTable(tableId1) // tableId = 1
                        .fromApp(appId);

                flowTableService.applyFlowTables(flowTable1.build());
                log.info("flowTableService to pof:0000000000000003 tableId-1?-{}: {}", tableId1, flowTable1.build());
            } else {
                // construct OFMatch FlowTable without VLC
                OFFlowTable ofFlowTable_without_VLC = new OFFlowTable();
                ofFlowTable_without_VLC.setTableId(tableId);
                ofFlowTable_without_VLC.setTableName("FirstEntryTable");
                ofFlowTable_without_VLC.setMatchFieldNum((byte) 1);
                ofFlowTable_without_VLC.setTableSize(32);
                ofFlowTable_without_VLC.setTableType(OFTableType.OF_MM_TABLE);
                ofFlowTable_without_VLC.setCommand(null);
                ofFlowTable_without_VLC.setKeyLength((byte) 32);
                ofFlowTable_without_VLC.setMatchFieldList(match_DIP_without_VLC);

                // send flow table to switches when connected up
                FlowTable.Builder flowTable = DefaultFlowTable.builder()
                        .withFlowTable(ofFlowTable_without_VLC)
                        .forDevice(deviceId)
                        .forTable(tableId)
                        .fromApp(appId);

                flowTableService.applyFlowTables(flowTable.build());
                log.info("flowTableService to pof:000000000000000x: {}", flowTable.build());
            }
        }
    }

    @Override
    public void handleConnectionDown() {
        List<DeviceId>  deviceIdList = getDeviceList();

        for(DeviceId deviceId : deviceIdList) {
            flowTableService.removeFlowTablesByTableId(deviceId, FlowTableId.valueOf(gloablTableId));
        }
    }

    @Override
    public void handlePortStatus() {
        List<DeviceId> deviceIdList = getDeviceList();

        for(DeviceId deviceId : deviceIdList) {
            if(deviceId.toString().equals("pof:0000000000000003")) {
                deviceAdminService.changePortState(deviceId, PortNumber.portNumber(1), true);  // for sw3
                deviceAdminService.changePortState(deviceId, PortNumber.portNumber(2), true);
                //deviceAdminService.changePortState(deviceId, PortNumber.portNumber(3), true);
                log.info("[==PORT_STATUS==] pof:0000000000000003 enables ports.");
            } else {
                deviceAdminService.changePortState(deviceId, PortNumber.portNumber(1), true); // for sw2 and AP
                deviceAdminService.changePortState(deviceId, PortNumber.portNumber(2), true);
                log.info("[==PORT_STATUS==] pof:000000000000000x enables ports.");
            }
        }
        log.info("[==PORT_STATUS==] handlePortStatus ok.");
    }

    @Override
    public void handleReactivePacket(PacketContext context) {
        if(context.isHandled()) {
            return;
        }

        // get deviceId and port, the port maybe not the WIFI port but WAN port
        InboundPacket inboundPacket = context.inPacket();
        String deviceId = inboundPacket.receivedFrom().deviceId().toString();
        int port = (int) inboundPacket.receivedFrom().port().toLong();   // through WAN to report to controller

        // get srcMAC and ip
        Ethernet ethernetPacket = inboundPacket.parsed();
        String hwaddr = ethernetPacket.getSourceMAC().toString();   // like "01:02:03:04:05:06"
        IPv4 iPv4Packet = (IPv4) ethernetPacket.getPayload();
        String ip = IPv4.fromIPv4Address(iPv4Packet.getSourceAddress()); // like "10.0.0.1"

        // get payload of IPv4, which is broadcast packet
        byte[] payload = iPv4Packet.getPayload().serialize();
        short ueId = (short) ((payload[0] << 8) + payload[1]);
        short ledId1 = (short) ((payload[2] << 8) + payload[3]);
        byte singnal1 = payload[4];
        short ledId2 = (short) ((payload[5] << 8) + payload[6]);
        byte singnal2 = payload[7];
        short ledId3 = (short) ((payload[8] << 8) + payload[9]);
        byte singnal3 = payload[10];

        // get the max signal value and its ledId. if all equals, use ledId1.
        short maxLedId = 0;
        byte maxSignal = 0;
        Map<Integer, Integer> LED = new HashMap<>();
        LED.put(Integer.valueOf(ledId1), Integer.valueOf(singnal1));
        LED.put(Integer.valueOf(ledId2), Integer.valueOf(singnal2));
        LED.put(Integer.valueOf(ledId3), Integer.valueOf(singnal3));
        byte temp = singnal1 > singnal2 ? singnal1 : singnal2;
        maxSignal = temp > singnal3 ? temp : singnal3;
        for(Integer key : LED.keySet()) {
            if(LED.get(key).shortValue() == (maxSignal)) {
                maxLedId = key.shortValue();
                break;
            }
        }

        // set the broadcast packet's ether type as 0x0908
        if(ethernetPacket.getEtherType() == 0x0908) {
            // TODO use Event Mechanism to implement handleUeAssociation() or handleHandover() later
            handleUeAssociation(deviceId, port, hwaddr, ip, ueId, maxLedId, maxSignal);
        }

    }

    @Override
    public void handleUeAssociation(String deviceId, int port, String hwaddr, String ip,
                                    short ueId, short maxLedId, byte maxSignal) {
        Map<String, UE> ues = new HashMap<>();

        // 1. update: if ues contains key, then compare with maxSignal, update MAP if different
        // 2. bind: if ues excludes key, then put it into MAP
        // Note that: new MAC, then new UeId
        if(ues.containsKey(hwaddr)) {
            short tempLedId = ues.get(hwaddr).getCurrentAssociation().getLedId();
            short tempSignal = ues.get(hwaddr).getCurrentAssociation().getPower();
            String tempDeviceId = ues.get(hwaddr).getCurrentAssociation().getDeviceId();
            int tempPort = ues.get(hwaddr).getCurrentAssociation().getPort();
            if((tempLedId == maxLedId) && (tempSignal == maxSignal)
                    && (tempDeviceId.equals(tempDeviceId) && (tempPort == port))) {
                // no update operation
            } else {
                // UPDATE-STAGE
                ues.get(hwaddr).setUeAssociation(new UeAssociation(deviceId, port, maxLedId, maxSignal, ip));
                UE logUe = ues.get(hwaddr); // used for log
                UeAssociation logAssociation = logUe.getCurrentAssociation();
                log.info("[==update info==] UE[id:{}, hwaddr: {}, ip:{}] connects to LED {}, power {}, AP {}, port {}.",
                        logUe.getUeId(), logUe.getHwaddr(), logUe.getIp(),
                        logAssociation.getLedId(), logAssociation.getPower(),
                        logAssociation.getDeviceId(), logAssociation.getPort());
            }
        } else {
            // BIND-STAGE, 1)assign new ueId; 2)raise event to installGatewayFlowRule; 3) update more information in UPDATE-STAGE
            // TODO installGatewayFlowRule
            ues.put(hwaddr, new UE(++lastUeId, maxLedId, hwaddr, ip));  //  here assign UeId for all method. ROOT_UEID.
            ues.get(hwaddr).setUeAssociation(new UeAssociation(maxLedId, maxSignal, ip));  // have associated with light-AP
            UE logUe = ues.get(hwaddr); // used for log
            UeAssociation logAssociation = logUe.getCurrentAssociation();
            log.info("[==bind info==] UE[id:{}, hwaddr: {}, ip:{}] connects to LED {}, power {}.",
                    logUe.getUeId(), logUe.getHwaddr(), logUe.getIp(),
                    logAssociation.getLedId(), logAssociation.getPower());
        }
    }

    @Override
    public void installDefaultFlowRule(String deviceId, String dstip, int outport, int DIP) {
        // match dstIp {240b, 32b}
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 240, (short) 32, ip2HexStr(dstip), "00000000"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installAPFlowRule==] match dstIP.");

        // action: (1)remove VLC Header; (2)forward to server; (3)no action to deal with the broadcast packets, so PacketIn
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        //OFAction action_remove_VLC = DefaultPofActions.deleteField(112, 48).action(); // VLCHeader{0, 48} is 6B in the front of IP packets
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outport).action();
        //actions.add(action_remove_VLC);
        actions.add(action_outport);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));
        log.info("[==installAPFlowRule==] action: {}.", actions);

        // apply flow rules to AP, globalTableId = 0 by default
        long newFlowEntryId = flowTableStore.getNewFlowEntryId(DeviceId.deviceId(deviceId), gloablTableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId(deviceId))
                .forTable(gloablTableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withPriority(1)
                .withCookie(newFlowEntryId)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());
        log.info("[==installAPFlowRule==] applyRuleService {} + globalTableId {}.",deviceId, gloablTableId);
    }
}