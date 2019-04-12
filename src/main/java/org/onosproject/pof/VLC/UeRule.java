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

import java.nio.ByteBuffer;
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
     * 2. VLC Header: Type(2B) + len(2B) + timeslot(1B) + LEDID(2B) + UID(2B) + SERVICEID(2B)
     * 3. when distribute id of ue, add MacField.
     */
    public static final short SIP = 12;
    public static final short DIP = 13;
    public static final short VLC = 14;   // all vlc fields
    public static final short UDP_LEN_FIELD = 15;
    public static final short UDP_CKM_FIELD = 16;
    public static final short IP_LEN_FIELD = 17;

    public static final short SERVICEID = 2;
    public static final short TIMESLOT = 3;
    public static final short UID = 4;
    public static final short LEDID = 5;
    public static final short MacField = 6;

    /* VLC header definition. move to 'Protocol' Class. */
//    final static short VLC_TYPE = 0x1918;
//    final static short VLC_LEN = 0x0b;


    protected int gloablTableId = NetworkBoot.globalTableId();

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

    // see updateGatewaySwitchFlowRule()
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
        StringBuilder ipHexStr = new StringBuilder();
        for(int i = 0; i < 4; i++) {
            /* ipArray[i] should be [0, 255], ignore condition check. */
//            if (Integer.parseInt(ipArray[i], 10) > 0xff) {
//                ipArray[i] = "255";
//            }

            tempIp[i] = Integer.toHexString(Integer.parseInt(ipArray[i], 10));
            if(tempIp[i].length() < 2) {
                tempIp[i] = "0" + tempIp[i];
            }
            ipHexStr.append(tempIp[i]);
        }

        return ipHexStr.toString();
    }

    @Override
    public String short2HexStr(short shortNum) {
        StringBuilder hex_str = new StringBuilder();
        byte[] b = new byte[2];
        b[1] = (byte) (shortNum & 0xff);
        b[0] = (byte) ((shortNum >> 8) & 0xff);

        return bytes_to_hex_str(b);
    }

    @Override
    public String int2HexStr(int intNum) {
        byte[] b = new byte[4];
        ByteBuffer buffer = ByteBuffer.allocate(b.length);
        buffer.putInt(intNum);

        return bytes_to_hex_str(buffer.array());
    }

    @Override
    public String byte2HexStr(byte byteNum) {
        String hex = Integer.toHexString(   byteNum & 0xff);
        if (hex.length() == 1) {
            hex = '0' + hex;
        }
        return hex;
    }

    @Override
    public String bytes_to_hex_str(byte[] b) {
        StringBuilder hex_str = new StringBuilder();
        for (int i = 0; i < b.length; i++) {
            String hex = Integer.toHexString(b[i] & 0xff);
            if (hex.length() == 1) {
                hex = '0' + hex;
            }
            hex_str.append(hex);
        }
        return hex_str.toString();
    }

    @Override
    public List<DeviceId> getDeviceList() {
        List<DeviceId> deviceIdList = new ArrayList<>();
        deviceIdList.add(DeviceId.deviceId("pof:0000000000000001"));  // deviceId for AP
//        deviceIdList.add(DeviceId.deviceId("pof:0000000000000002"));  // deviceId for pof-switch
//        deviceIdList.add(DeviceId.deviceId("pof:0000000000000003"));
        return deviceIdList;
    }

    @Override
    public void handleConnetionUp(ApplicationId appId) {
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

    /**
     * @desp new design to add VLC header
     * @header type + len + ts + ledID + ueID + service_flag
     */
    @Override
    public byte send_pof_flow_table(DeviceId deviceId, String table_name, ApplicationId appId) {
        byte tableId = (byte) flowTableStore.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);

        OFMatch20 srcIP = new OFMatch20();
        srcIP.setFieldId(DIP);
        srcIP.setFieldName("dstIP");
        srcIP.setOffset((short) 240);
        srcIP.setLength((short) 32);

        ArrayList<OFMatch20> match20List = new ArrayList<>();
        match20List.add(srcIP);

        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId(tableId);
        ofFlowTable.setTableName(table_name);
        ofFlowTable.setMatchFieldList(match20List);
        ofFlowTable.setMatchFieldNum((byte) 1);
        ofFlowTable.setTableSize(32);
        ofFlowTable.setTableType(OFTableType.OF_MM_TABLE);
        ofFlowTable.setCommand(null);
        ofFlowTable.setKeyLength((short) 32);

        FlowTable.Builder flowTable = DefaultFlowTable.builder()
                .withFlowTable(ofFlowTable)
                .forTable(tableId)
                .forDevice(deviceId)
                .fromApp(appId);

        flowTableService.applyFlowTables(flowTable.build());

        log.info("table<{}> applied to device<{}> successfully.", tableId, deviceId.toString());

        return tableId;
    }

    /**
     * @desp match 'MAX_UE_LED' in ue's feedback frame to avoid too much packet-in
     */
    @Override
    public byte send_pof_flow_table_to_wireless_ap(DeviceId deviceId, String table_name, ApplicationId appId) {
        byte tableId = (byte) flowTableStore.getNewGlobalFlowTableId(deviceId, OFTableType.OF_MM_TABLE);

        short LED_FIELD_ID = 20;
        short udp_payload_off = 336; // 42 * 8;
        short ue_led_off = 368;   // 336 + 4 * 8
        short ue_led_len = 32;    // (2+2) * 8

        OFMatch20 ue_led_field = new OFMatch20();
        ue_led_field.setFieldId(LED_FIELD_ID);
        ue_led_field.setFieldName("ue_led_field");
        ue_led_field.setOffset(ue_led_off);
        ue_led_field.setLength(ue_led_len);

        ArrayList<OFMatch20> match20List = new ArrayList<>();
        match20List.add(ue_led_field);

        OFFlowTable ofFlowTable = new OFFlowTable();
        ofFlowTable.setTableId(tableId);
        ofFlowTable.setTableName(table_name);
        ofFlowTable.setMatchFieldList(match20List);
        ofFlowTable.setMatchFieldNum((byte) 1);
        ofFlowTable.setTableSize(32);
        ofFlowTable.setTableType(OFTableType.OF_MM_TABLE);
        ofFlowTable.setCommand(null);
        ofFlowTable.setKeyLength(ue_led_len);

        FlowTable.Builder flowTable = DefaultFlowTable.builder()
                .withFlowTable(ofFlowTable)
                .forTable(tableId)
                .forDevice(deviceId)
                .fromApp(appId);

        flowTableService.applyFlowTables(flowTable.build());

        log.info("table<{}> applied to device<{}> (ap) successfully.", tableId, deviceId.toString());

        return tableId;
    }

    @Override
    public void remove_pof_flow_table(DeviceId deviceId, byte tableId, ApplicationId appId) {
        // remove flow rules before removing flow table
//        flowRuleService.removeFlowRulesById(appId);
        flowTableService.removeFlowTablesByTableId(deviceId, FlowTableId.valueOf(tableId));
    }

    @Override
    public void install_pof_output_entry(String deviceId, int tableId, String dstIp, int outport, int priority) {
        // match dstIP
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength(DIP, (short) 240, (short) 32, ip2HexStr(dstIp), "ffffffff"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));

        // action: only forward packets
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        OFAction action_output = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outport)
                                                .action();
        actions.add(action_output);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));

        // apply
        long newFlowEntryId = flowTableStore.getNewFlowEntryId(DeviceId.deviceId(deviceId), tableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(DeviceId.deviceId(deviceId))
                .forTable(tableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withPriority(priority)
                .withCookie(newFlowEntryId)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());
    }

    /* table_id = 0, goto_table = 1
     * 1. store the udp's 'len' into pof.metadata<off, len> to update vlc's 'len' in table1;
     * 2. goto_table1
     */
    @Override
    public void install_pof_write_metadata_from_packet_entry(DeviceId deviceId, int tableId, int next_table_id,
                                                             String dstIP, int priority) {
        // match
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength(DIP, (short) 240, (short) 32, dstIP, "ffffffff"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));

        // metadata bits
        short metadata_offset = 32;
        short udp_len_offset = 304;    // the offset of `len` field in udp
        short write_len = 16;          // the length of `len` field in udp

        // next_table_match_field (should same as next_table), here is still dstIP
        OFMatch20 next_table_match_dstIP = new OFMatch20();
        next_table_match_dstIP.setFieldId(DIP);
        next_table_match_dstIP.setFieldName("dstIP");
        next_table_match_dstIP.setOffset((short) 240);
        next_table_match_dstIP.setLength((short) 32);

        ArrayList<OFMatch20> match20List = new ArrayList<>();
        match20List.add(next_table_match_dstIP);

        byte next_table_match_field_num = 1;
        short next_table_packet_offset = 0;

        int vlc_header_len = 11;   // type(2B) + len(2B) + ts(1B) + led_id(2B) + ue_id(2B) + srv_flag(2B)
        short ip_len_off = 128;
        short ip_len_len = 16;

        // ip.checksum field, only includes ip.header
        byte ckm_type = 0;    // immediate number
        short cal_pos = 128;  // cal.header offset
        short cal_len = 160;  // ip.header length
        short cs_off = 192;   // ip.ckm offset
        short cs_len = 16;    // ip.ckm length

        // ip.len field, update the ip.len plus vlc.header (11B)
        OFMatch20 ip_len_field = new OFMatch20();
        ip_len_field.setFieldId(IP_LEN_FIELD);
        ip_len_field.setFieldName("ip_len_field");
        ip_len_field.setOffset(ip_len_off);
        ip_len_field.setLength(ip_len_len);

        // udp.len field, update the udp.len plus vlc.header (11B)
//        OFMatch20 udp_len_field = new OFMatch20();
//        udp_len_field.setFieldId(UDP_LEN_FIELD);
//        udp_len_field.setFieldName("udp_len_field");
//        udp_len_field.setOffset(udp_len_offset);
//        udp_len_field.setLength(write_len);
//        OFAction action_inc_udp_len = DefaultPofActions.modifyField(udp_len_field, vlc_header_len).action();
        OFAction action_inc_ip_len = DefaultPofActions.modifyField(ip_len_field, vlc_header_len).action();
        OFAction action_cal_ip_checksum = DefaultPofActions.calcCheckSum(ckm_type, ckm_type, cs_off, cs_len, cal_pos, cal_len).action();

        ArrayList<OFAction>  actions = new ArrayList<>();
//        actions.add(action_inc_udp_len);
        actions.add(action_inc_ip_len);
        actions.add(action_cal_ip_checksum);

        // instruction
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));  // update ip.len, ip.ckm, udp.len, update udp.ckm in install_pof_add_vlc_header_entry
        trafficTreatment.add(DefaultPofInstructions
                .writeMetadataFromPacket(metadata_offset, udp_len_offset, write_len)); // store udp.len into pof.metadata
        trafficTreatment.add(DefaultPofInstructions
                .gotoTable((byte) next_table_id, next_table_match_field_num, next_table_packet_offset, match20List));

        long newFlowEntryId = flowTableStore.getNewFlowEntryId(deviceId, tableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .forTable(tableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withPriority(priority)
                .withCookie(newFlowEntryId)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());
        log.info("install_pof_write_metadata_from_packet_entry: deviceId<{}> tableId<{}>, entryId<{}>",
                deviceId, tableId, newFlowEntryId);
    }

    /* table_id = 1
     * 1. comprise vlc header string, and update the vlc's len
     * 2. delete old flow entry before updating vlc header
     */
    @Override
    public void install_pof_add_vlc_header_entry(DeviceId deviceId, int tableId, String dstIP, int outport, int priority,
                                                 byte timeSlot, short ledId, short ueId, short serviceId) {
        // vlc header
        short type = Protocol.VLC_TYPE;
        short len = Protocol.VLC_LEN;      // type:2 + len:2 + ts:1 + ledID:2 + ueID:2 + serviceId:2 = 11
        short vlc_offset = 336;  // begin of udp payload: 42*8=336 bits
        short vlc_length = 88;   // 11 * 8 bits
        // short VLC = 0x16;        // field_id

        // metadata bits, temp stored metadata location
        short metadata_offset = 32;
        short write_len = 16;

        // vlc_header
        StringBuilder vlc_header = new StringBuilder();
        vlc_header.append(short2HexStr(type));
        vlc_header.append(short2HexStr(len));
        vlc_header.append(byte2HexStr(timeSlot));
        vlc_header.append(short2HexStr(ledId));
        vlc_header.append(short2HexStr(ueId));
        vlc_header.append(short2HexStr(serviceId));

        // match
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength(DIP, (short) 240, (short) 32, dstIP, "ffffffff"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));

        // action: add vlc header
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        OFAction action_add_vlc_field = DefaultPofActions.addField(VLC, vlc_offset, vlc_length, vlc_header.toString())
                .action();

        // used for set_field_from_metadata, to update vlc.len with the value of udp.len
        OFMatch20 metadata_vlc_len = new OFMatch20();
        metadata_vlc_len.setFieldName("metadata_vlc_len");
        metadata_vlc_len.setFieldId(OFMatch20.METADATA_FIELD_ID);
        metadata_vlc_len.setOffset((short) (vlc_offset + 16));     // the packet_field_offset
        metadata_vlc_len.setLength(write_len);                     // the packet_field_len

        // used for modify_field
        OFMatch20 vlc_len_field = new OFMatch20();
        vlc_len_field.setFieldName("vlc_len");
        vlc_len_field.setFieldId(len);
        vlc_len_field.setOffset((short) (vlc_offset + 16));
        vlc_len_field.setLength((short) 16);  //

        // udp.len field, update the udp.len plus vlc.header (11B)
        int vlc_header_len = 11;   // type(2B) + len(2B) + ts(1B) + led_id(2B) + ue_id(2B) + srv_flag(2B)
        short udp_len_offset = 304;    // the offset of `len` field in udp
        OFMatch20 udp_len_field = new OFMatch20();
        udp_len_field.setFieldId(UDP_LEN_FIELD);
        udp_len_field.setFieldName("udp_len_field");
        udp_len_field.setOffset(udp_len_offset);
        udp_len_field.setLength(write_len);

        // get existed flow rules in flow table. if the dstIp equals, then delete it
        Map<Integer, FlowRule> existedFlowRules = new HashMap<>();
        existedFlowRules = flowTableStore.getFlowEntries(deviceId, FlowTableId.valueOf(tableId));
        if(existedFlowRules != null) {
            for(Integer flowEntryId : existedFlowRules.keySet()) {
                log.info("existedFlowRules.get(flowEntryId).selector().equals(trafficSelector.build()) ==> {}",
                        existedFlowRules.get(flowEntryId).selector().equals(trafficSelector.build()));
                if(existedFlowRules.get(flowEntryId).selector().equals(trafficSelector.build())) {
                    flowTableService.removeFlowEntryByEntryId(deviceId, tableId, flowEntryId);
                }
            }
        }

        // vlc_len = vlc.header + udp.payload, so metadata minus udp.header
        short vlc_len = (short) (len - 8);

        // udp.checksum field, may set as 0x0000
        byte ckm_type = 0;    // immediate number
        short cal_pos = 320;  // cal.ckm start
        short cal_len = 104;  // cal.ckm end, include {udp.ckm(2B) + vlc.header(11B)}, cal_result is wrong.
        short cs_off = 320;   // udp.ckm offset
        short cs_len = 16;    // udp.ckm length
        short udp_ckm_off = 320;
        short udp_ckm_len = 16;

        OFAction action_set_vlc_len = DefaultPofActions.setFieldFromMetadata(metadata_vlc_len, metadata_offset)
                                      .action();
//        OFAction action_cal_udp_checksum = DefaultPofActions.calcCheckSum(ckm_type, ckm_type, cs_off, cs_len, cal_pos, cal_len)
//                                      .action();
        OFAction action_inc_vlc_len = DefaultPofActions.modifyField(vlc_len_field, vlc_len)
                                      .action();
        OFAction action_inc_udp_len = DefaultPofActions.modifyField(udp_len_field, vlc_header_len).action();
        OFAction action_reset_udp_ckm = DefaultPofActions.setField(UDP_CKM_FIELD, udp_ckm_off, udp_ckm_len, "0000", "ffff")
                                      .action();
        OFAction action_output = DefaultPofActions.output((short) 0, (short) 0, (short) 0, outport)
                                 .action();

        actions.add(action_add_vlc_field);
        actions.add(action_set_vlc_len);
        actions.add(action_inc_vlc_len);
        actions.add(action_inc_udp_len);
//        actions.add(action_cal_udp_checksum);
        actions.add(action_reset_udp_ckm);  // if udp.ckm == 0x0000, then receiver will not check udp.ckm
        actions.add(action_output);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));

        long newFlowEntryId = flowTableStore.getNewFlowEntryId(deviceId, tableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .forTable(tableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withPriority(priority)
                .withCookie(newFlowEntryId)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());

        log.info("install_pof_add_vlc_header_entry: tableId<{}>, entryId<{}>", tableId, newFlowEntryId);
    }

    /* match 'max_led_id' to avoid too much packet_in. action=drop.
    * can remove old flow entry. */
    @Override
    public void install_pof_avoid_packet_in_entry(DeviceId deviceId, int tableId, short ueId, short ledID, short oldLedId, int priority) {
        short LED_FIELD_ID = 20;
        short udp_payload_off = 336; // 42 * 8;
        short ue_led_off = 368;   // 336 + 4 * 8
        short ue_led_len = 32;    // (2+2) * 8

        String oldMatchField = short2HexStr(ueId) + short2HexStr(oldLedId);
        String matchField = short2HexStr(ueId) + short2HexStr(ledID);

        // old_match
        TrafficSelector.Builder oldtrafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> oldmatchList = new ArrayList<>();
        oldmatchList.add(Criteria.matchOffsetLength(LED_FIELD_ID, ue_led_off, ue_led_len, oldMatchField, "ffffffff"));
        oldtrafficSelector.add(Criteria.matchOffsetLength(oldmatchList));

        // new_match
        TrafficSelector.Builder trafficSelector = DefaultTrafficSelector.builder();
        ArrayList<Criterion> matchList = new ArrayList<>();
        matchList.add(Criteria.matchOffsetLength(LED_FIELD_ID, ue_led_off, ue_led_len, matchField, "ffffffff"));
        trafficSelector.add(Criteria.matchOffsetLength(matchList));

        // action: drop unnecessary packet-in to controller
        TrafficTreatment.Builder trafficTreatment = DefaultTrafficTreatment.builder();
        List<OFAction> actions = new ArrayList<>();
        OFAction action_drop = DefaultPofActions.drop(1).action();

        // remove oldMatchFiled flow entry, oldLedId means initial value
        // get existed flow rules in flow table. if equals, then delete it
        Map<Integer, FlowRule> existedFlowRules = new HashMap<>();
        existedFlowRules = flowTableStore.getFlowEntries(deviceId, FlowTableId.valueOf(tableId));
        if(existedFlowRules != null) {
            for(Integer flowEntryId : existedFlowRules.keySet()) {
                if(existedFlowRules.get(flowEntryId).selector().equals(oldtrafficSelector.build())) {
                    flowTableService.removeFlowEntryByEntryId(deviceId, tableId, flowEntryId);
                }
            }
        }

        actions.add(action_drop);
        trafficTreatment.add(DefaultPofInstructions.applyActions(actions));

        long newFlowEntryId = flowTableStore.getNewFlowEntryId(deviceId, tableId);
        FlowRule.Builder flowRule = DefaultFlowRule.builder()
                .forDevice(deviceId)
                .forTable(tableId)
                .withSelector(trafficSelector.build())
                .withTreatment(trafficTreatment.build())
                .withPriority(priority)
                .withCookie(newFlowEntryId)
                .makePermanent();
        flowRuleService.applyFlowRules(flowRule.build());

        log.info("install_pof_add_vlc_header_entry: tableId<{}>, entryId<{}>", tableId, newFlowEntryId);
    }
}