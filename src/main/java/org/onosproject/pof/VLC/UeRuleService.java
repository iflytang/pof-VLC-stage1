package org.onosproject.pof.VLC;

import org.onosproject.net.DeviceId;
import org.onosproject.net.packet.PacketContext;

import java.util.List;

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

    /**
     * functions as basic tool
     */
    // convert ip to no colon string
    String ip2HexStr(String ip);

    // convert short num to 2B HexStr
    String short2HexStr(short shortNum);

    // convert int num to 4B HexStr
    String int2HexStr(int intNum);


    /**
     * functions for network boot
     ***/
    // add deviceId and return deviceList
    List<DeviceId> getDeviceList();

    // distribute flow table to switches when network boot
    void handleConnetionUp();

    // remove all flow tables from switches and AP when network shutdown
    void handleConnectionDown();

    // enable ports of switches and AP when network boot
    void handlePortStatus();

    /**
     * functions for processing AP's broadcast packets (Ether type as 0x0908)
     */
    // handle the AP's broadcast packets
    void handleReactivePacket(PacketContext context);

    // store UE's information and set association for UE with AP and sw
    void handleUeAssociation(String deviceId, int port, String hwaddr, String ip, short ueId, short maxLedId, byte maxSignal);

    void installForwardFlowRule(String deviceId, int tableId, String dstip, int outport, int DIP);

}
