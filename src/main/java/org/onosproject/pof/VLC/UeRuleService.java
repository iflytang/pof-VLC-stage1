package org.onosproject.pof.VLC;

import org.onosproject.core.ApplicationId;
import org.onosproject.net.Device;
import org.onosproject.net.DeviceId;
import org.onosproject.net.packet.PacketContext;

import java.util.List;
import java.util.Map;

/**
 * Created by tsf on 10/9/17.
 *
 * @Description the interface of pof-VLC network management for ue, which states methods to rule ue
 *
 */
public interface UeRuleService {

    /**
     * functions for ue
     */
    // install flow rules for inter switches, to instruct routing, dstIp {240, 32}
    void installInterSwitchFlowRule(String deviceId, String dstIp, int outPort, int DIP);

    // install flow rules for gateway switches, to add VLC header and forward packets to UE
    void installGatewaySwitchFlowRule(String deviceId, String dstip, int outPort, int DIP,
                                      int ledId, int ueId, int timeSlot, int serviceId);

    // update flow rules for gateway switches, to set field for VLC header
    void updateGatewaySwitchFlowRule(String deviceId, String dstip, int outport, int DIP,
                                     int ledId, int ueId, int timeSlot, int serviceId);

    // install flow rules for wifi AP, to forward UE's feedback to controller with PacketIn by default and
    // forward packets to servers after removing VLC header, dstIp {240+6*8, 32}
    void installAPFlowRule(String deviceId,int tableId, String dstip, int outport, int DIP);

    // delete VLC header and forward in test stage {288, 32}
    void installUeSwitchFlowRule(String deviceId, String dstIp, int outPort, int DIP);

    // go to table when test ping, installed in UE {288, 32}
    void installGoToTableFlowRule(String deviceId, int tableId, int goToTableId);

    void installForwardFlowRule(String deviceId, int tableId, String dstip, int outport, int DIP);

    /**
     * functions as basic tool
     */
    // convert ip to no colon string
    String ip2HexStr(String ip);

    // convert short num to 2B HexStr
    String short2HexStr(short shortNum);

    // convert int num to 4B HexStr
    String int2HexStr(int intNum);

    // convert byte to hex_str
    String byte2HexStr(byte byteNum);

    // convert bytes to hex_str
    String bytes_to_hex_str(byte[] b);

    /**
     * functions for network boot
     ***/
    // add deviceId and return deviceList
    List<DeviceId> getDeviceList();

    // distribute flow table to switches when network boot
    void handleConnetionUp(ApplicationId appId);

    // remove all flow tables from switches and AP when network shutdown
    void handleConnectionDown();

    // enable ports of switches and AP when network boot
    void handlePortStatus();
    
    // set timeSlot mask, return timeSlot int
    int toDecTimeSlot(List<Integer> timeSlotList);

    // generate ueId, return new ueId
    int ueIdGenerator(String mac);

    // remove ueId by Object, return remained ueIdList
    List<Integer> removeUeId(Integer ueId);

    /**
     * @desp new design to add VLC header
     * @header type + len + ts + ledID + ueID + service_flag
     */
    byte send_pof_flow_table(DeviceId deviceId, String table_name, ApplicationId appId);

    byte send_pof_flow_table_to_wireless_ap(DeviceId deviceId, String table_name, ApplicationId appId);

    void remove_pof_flow_table(DeviceId deviceId, byte tableId, ApplicationId appId);

    void install_pof_output_entry(String deviceId, int tableId, String dstIp, int outport, int priority);

    void install_pof_write_metadata_from_packet_entry(DeviceId deviceId, int tableId, int next_table_id,
                                                      String dstIP, int priority);

    void install_pof_add_vlc_header_entry(DeviceId deviceId, int tableId, String dstIP, int outport, int priority,
                                          byte timeSlot, short ledId, short ueId, short serviceId);

    /* match 'ue_led_id' to avoid too much packet_in. action=drop. */
    void install_pof_avoid_packet_in_entry(DeviceId deviceId, int tableId, short ueId, short ledID,
                                           short oldLedID, int priority);
}
