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

import java.util.*;

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
     * 3. when distribute id of ue, add MacField.
     */
    public static final int DIP = 1;
    public static final int SERVICEID = 2;
    public static final int TIMESLOT = 3;
    public static final int UID = 4;
    public static final int LEDID = 5;
    public static final int MacField = 6;


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
        log.info("[==installInterFlowRule==] 1. match: {}.", matchList);

        // action: forward
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outPort).action();
        actions.add(action_outport);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));
        log.info("[==installInterFlowRule==] 2. action: {}.", actions);

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
        log.info("[==installInterFlowRule==] 3. applyRuleService deviceId: {} + TableId: {}.", deviceId, gloablTableId);
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
        log.info("[==installGWFlowRule==] 1. match: {}.", matchList);

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
        log.info("[==installGWFlowRule==] 2. action: {}.", actions);

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
        log.info("[==installGWFlowRule==] 3. applyRuleService deviceId: {} + TableId: {}.", deviceId, gloablTableId);
    }

    @Override
    public void updateGatewaySwitchFlowRule(String deviceId, String dstip, int outPort, int DIP,
                                            int ledId, int ueId, int timeSlot, int serviceId) {
        // match dstIp{240b, 32b}, will add VLC header
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 240, (short) 32, ip2HexStr(dstip), "ffFFffFF"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installUpdateGWFlowRule==] 1. match: {}.", matchList);

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
        log.info("[==installUpdateGWFlowRule==] 2. action: {}.", actions);

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
        log.info("[==installUpdateGWFlowRule==] 3. applyRuleService deviceId: {} + TableId: {}.", deviceId, gloablTableId);

    }

    @Override
    public void installAPFlowRule(String deviceId,int tableId, String dstip, int outport, int DIP) {
        // match dstIp {240b, 32b}
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 240, (short) 32, ip2HexStr(dstip), "ffffffff"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installAPFlowRule==] 1. match: {}.", matchList);

        // action: (1)remove VLC Header; (2)forward to server; (3)no action to deal with the broadcast packets, so PacketIn
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
       // OFAction action_remove_VLC = DefaultPofActions.deleteField(112, 48).action(); // VLCHeader{0, 48} is 6B in the front of IP packets
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outport).action();
       // actions.add(action_remove_VLC);
        actions.add(action_outport);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));
        log.info("[==installAPFlowRule==] 2. action: {}.", actions);

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
        log.info("[==installAPFlowRule==] 3. applyRuleService {} + tableId {}.",deviceId, tableId);
    }

    @Override
    public void installUeSwitchFlowRule(String deviceId, String dstIp, int outPort, int DIP) {
        // match dstIp{240b, 32b}, no VLC header here
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 288, (short) 32, ip2HexStr(dstIp), "ffFFffFF"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installUeFlowRule==] 1. match: {}.", matchList);

        // action: forward
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        OFAction action_remove_VLC = DefaultPofActions.deleteField(112, 48).action();
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outPort).action();
//        OFAction action_outport2 = DefaultPofActions.output((short) 0, (short) 0, (short) 0, 2).action();
        actions.add(action_remove_VLC);
        actions.add(action_outport);
//        actions.add(action_outport2);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));
        log.info("[==installUeFlowRule==] 2. action: {}.", actions);

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
        log.info("[==installUeFlowRule==] 3. applyRuleService deviceId: {} + TableId: {}.", deviceId, gloablTableId);
    }

    @Override
    public void installGoToTableFlowRule(String deviceId, int tableId, int goToTableId) {
        // match dstIp{240b, 32b}, no VLC header here
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 288, (short) 32, "00000000", "00000000"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installGotoTableFlowRule==] 1. match: {}.", matchList);

        // action: forward
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        trafficTreatment.add(DefaultPofInstructions.gotoTable((byte) goToTableId, (byte) 0, (byte) 0, new ArrayList<OFMatch20>()));
        log.info("[==installGotoTableFlowRule==] 2. action: {}.", actions);

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
        log.info("[==installGotoTableFlowRule==] 3. applyRuleService deviceId: {} + TableId0: {} to TableId1: {}.",
                deviceId, tableId, goToTableId);
    }

    @Override
    public void installForwardFlowRule(String deviceId, int tableId, String dstip, int outport, int DIP) {
        // match dstIp {240b, 32b}
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength((short) DIP, (short) 240, (short) 32, ip2HexStr(dstip), "ffffffff"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));
        log.info("[==installForwardFlowRule==] 1. match: {}.", matchList);

        // action: (1)remove VLC Header; (2)forward to server; (3)no action to deal with the broadcast packets, so PacketIn
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        // OFAction action_remove_VLC = DefaultPofActions.deleteField(112, 48).action(); // VLCHeader{0, 48} is 6B in the front of IP packets
        OFAction action_outport = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outport).action();
        // actions.add(action_remove_VLC);
        actions.add(action_outport);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));
        log.info("[==installForwardFlowRule==] 2. action: {}.", actions);

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
        log.info("[==installForwardFlowRule==] 3. applyRuleService {} + tableId {}.",deviceId, tableId);
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
            //int smallTableId = flowTableStore.parseToSmallTableId(deviceId, gloablTableId);
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
        long tableId = 0;
        long tableId1 = 1;

        for(DeviceId deviceId : deviceIdList) {
            flowTableService.removeFlowTablesByTableId(deviceId, FlowTableId.valueOf(tableId));
            if(deviceId.toString().equals("pof:0000000000000003")) {
                flowTableService.removeFlowTablesByTableId(deviceId, FlowTableId.valueOf(tableId1));
            }
        }
    }

    @Override
    public void handlePortStatus() {
        List<DeviceId> deviceIdList = getDeviceList();

        for(DeviceId deviceId : deviceIdList) {
            if(deviceId.toString().equals("pof:0000000000000003")) {
                deviceAdminService.changePortState(deviceId, PortNumber.portNumber(1), true);  // for sw3
                deviceAdminService.changePortState(deviceId, PortNumber.portNumber(2), true);
                deviceAdminService.changePortState(deviceId, PortNumber.portNumber(3), true);
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
    public int toDecTimeSlot(List<Integer> timeSlotList) {
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

       hextimeSlot = Integer.toHexString(timeSlot);

       return Integer.valueOf(hextimeSlot, 16);
   }

    // generate UeId and store it in ueIdList
    protected List<Integer> ueIdList = new ArrayList<>();  // store ueId in ArrayList
    protected Map<String, Integer> Mac_UeId = new HashMap<>(); // store mac_ueId in Map
    @Override
    public int ueIdGenerator(String mac) {
       // assign UeId
       Random random = new Random();
       int ueId = random.nextInt(128) + 1;  // ueId from 1 to 128
       int randRange = 128;   // at most 128 ueId
       int i;   // index of for loop

       // if ueId have assigned, forbid to reassign
       if(Mac_UeId.get(mac) != null) {
           log.info("Ue [{}] have assigned UeId [{}], fail to reassign!", mac, Mac_UeId.get(mac));
           return Mac_UeId.get(mac);
       }

       // if ueIdList full, fail to assign ueId
       if(ueIdList.size() == randRange) {
           log.info("no more ueId! assign ueId fails, return with ueId<255>.");
           return 255;
       }

       // check in ueIdList in a traversal way
       for(i = 0; i < ueIdList.size(); i++) {
           // if assigned, reassign ueId and recheck
           Integer assignedId = ueIdList.get(i);
           if(ueId == assignedId) {
               int tempUeId = random.nextInt(128) + 1;
               log.info("Warning! ueId conflicts! There have been ueId ==> [{}], reassign ueId ==> [{}]", ueId, tempUeId);
               ueId = tempUeId;
               i = 0;
               i--;     // i-- then i++, finally i = 0
           }
       }

       // no conflicts for ueId, then store in ueIdSet
       ueIdList.add(ueId);
       Mac_UeId.put(mac, ueId);
       Collections.sort(ueIdList);     // sort ueId in ascending order
       log.info("Store ue [{}] with ueId [{}] in UeList{} and Mac_ueId: {}", mac, ueId, ueIdList, Mac_UeId);
       return ueId;
   }

    // remove ueId by Object
    @Override
    public List<Integer> removeUeId(Integer ueId) {
        ueIdList.remove(ueId);
        return ueIdList;
   }
}