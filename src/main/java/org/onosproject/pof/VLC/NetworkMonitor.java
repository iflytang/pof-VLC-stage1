package org.onosproject.pof.VLC;

import org.apache.felix.scr.annotations.*;
import org.onosproject.event.EventDeliveryService;
import org.onosproject.event.ListenerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.inject.New;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by tsf on 11/7/17.
 *
 * @Description monitor the network event, then process the network event
 *              , and implement event(E var1) in the interface NetworkListener
 */

@Component(immediate = true)
public class NetworkMonitor {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected UeRuleService ueRuleService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected NetworkEventService networkEventService;

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected EventDeliveryService eventDispatcher;

    private final Logger log = LoggerFactory.getLogger(getClass());
    private final InternalNetworkEventListener listener = new InternalNetworkEventListener();
    protected Dijkstra dijkstra = new Dijkstra();

    @Activate
    public void activate() {
        networkEventService.addListener(listener);
        log.info("Network Monitor Module Started.");
        //timeScheduler();      // uncomment this to test post NetworkEvent periodically
    }

    @Deactivate
    public void deactivate() {
        networkEventService.removeListener(listener);
        log.info("Network Monitor Module Stopped.");
    }


    // used for ASSIGN ueId
    private static Map<String, Integer> Mac_UeId = new HashMap<>();    // key: MAC, value: UeId
    private Map<String, String> Mac_Ip = new HashMap<>();       // key: MAC, value: dstIP
    // used for ATTACH ue
    private Map<String, UE> ues = new HashMap<>();             // key: MAC, value: UE
    public class InternalNetworkEventListener implements NetworkListener {
        @Override
        public void event(NetworkEvent event) {
            String deviceId = event.getDeviceId();
            String hwaddr = event.getHwaddr();
            String ip = event.getIp();
            int ledId = event.getLedId();
            int ueId = event.getUeId();
            log.info("Receive Event: {} ==> ueId: {}, deviceId: {}, hwaddr: {}, ip: {}, ledId:{}.",
                    event.type(), ueId, deviceId, hwaddr, ip, ledId);

            // TODO set all time slot for one flow temporary
            List<Integer> tempTimeSlot = new ArrayList<>();
            tempTimeSlot.add(1);
            tempTimeSlot.add(2);
            tempTimeSlot.add(3);
            tempTimeSlot.add(4);
            int timeSlot = ueRuleService.toDecTimeSlot(tempTimeSlot);
            log.info("[== TimeSlot ==] time slot: {}.", timeSlot);

            if(event.type().equals(NetworkEvent.Type.UE_ASSOCIATION)) {
//                log.info("<=======================================");
//                log.info("[== Test Event Received ==] Type: {}\n UeId: {}\n DeviceId: {}\n LedId: {}\n Hwaddr: {}\n IP: {}",
//                        event.type(), event.getUeId(), event.getDeviceId(), event.getLedId(), event.getHwaddr(), event.getIp());
//                log.info("=======================================>");
                log.info("Receive {} Event ==> handleUeAssociation()", event.type());
                handleUeAssociation(hwaddr, ip, (short) ueId, (short) ledId);
            }

            if(event.type().equals(NetworkEvent.Type.VLC_HEADER)) {
                // TODO outport? timeSlot?
                log.info("Receive {} Event ==> handleVLCHeader()", event.type());
                handleVLCHeader(deviceId, ip, 2, 1, ledId, ueId, timeSlot, 0);

            }

            if(event.type().equals(NetworkEvent.Type.VLC_UPDATE)) {
                // TODO outport? timeSlot?
                log.info("Receive {} Event ==> handleVLCUpdate()", event.type());
                handleVLCUpdate(deviceId, ip, 2, 1, ledId, ueId, timeSlot, 0);
            }

        }

        // handle UeAssociation
        public void handleUeAssociation(String hwaddr, String ip, short ueId, short ledId) {
            // if ueId != 0xff has not stored yet, then assume it as fake ueId (ueId show assigned by controller)
            if(ueId != 0xff) {
                // check Mac_UeId
                boolean flag = false;
                for(String key : Mac_UeId.keySet()) {
                    if (Mac_UeId.get(key) == ueId) {
                        flag = true;
                        break;
                    }
                }
                log.info("[== handleUeAssociation ==] receive UeId: {}, check whether stored in Mac_UeId: {} ==> {}.", ueId, Mac_UeId, flag);
                // check flag, if still false then reassign ueId
                if(!flag) {
                    short tempUeId = (short) ueRuleService.ueIdGenerator(hwaddr);
                    log.info("[== handleUeAssociation ==] Mac_UeId: {} excludes ueId: {}, so assume ueId fake and reassign new ueId: {}.",
                            Mac_UeId, ueId, tempUeId);
                    ueId = tempUeId;
                }
            }

            // if ueId == 0xff, assign new ueId. Once assigned, never change until controller shutdown
            if(ueId == 0xff) {
                ueId = (short) ueRuleService.ueIdGenerator(hwaddr);
                log.info("[== handleUeAssociation ==] receive ueId 0xff, assign new ueId: {}.", ueId);
            }

            // if has put, no more operation to put
            if(Mac_UeId.putIfAbsent(hwaddr, (int) ueId) == null) {
                log.info("[== handleUeAssociation ==] put ueId: {} into Mac_UeId: {}", ueId, Mac_UeId);
            }

            // 1. bind: if Map ues don't have key (ue's MAC), then put it into ues
            // 2. update: if ues have contained key (ue's MAC), then check whether ledId changes
            if(!ues.containsKey(hwaddr)) {
                ues.put(hwaddr, new UE(ueId, ledId, hwaddr, ip));   // store in ues
                ues.get(hwaddr).setUeAssociation(new UeAssociation(ledId, ip));  // set association
                log.info("[== handleUeAssociation ==] UE [id: {}, hwaddr: {}, ip: {} connects to LED: {}.", ueId, hwaddr, ip, ledId);
                log.info("[== handleUeAssociation ==] UE_Association_Bind_First.");
            } else {
                short storedLedId = ues.get(hwaddr).getLedId();
                String storedIp = ues.get(hwaddr).getIp();
                // update if ledId changes
                if(ledId != storedLedId) {
                    log.info("[== handleUeAssociation ==] UE_Association_Update_LedId.");
                    ues.put(hwaddr, new UE(ueId, ledId, hwaddr, ip));
                    ues.get(hwaddr).setUeAssociation(new UeAssociation(ledId, ip));
                    log.info("[== handleUeAssociation ==] UE [id: {}, hwaddr: {}, ip: {}] leaves from LED: {} to LED: {}.", ueId, hwaddr, ip, storedLedId, ledId);
                }
                // update if ip changes
                if(!ip.equals(storedIp)) {
                    log.info("[== handleUeAssociation ==] UE_Association_Update_ip.");
                    ues.put(hwaddr, new UE(ueId, ledId, hwaddr, ip));
                    ues.get(hwaddr).setUeAssociation(new UeAssociation(ledId, ip));
                    log.info("[== handleUeAssociation ==] UE [id: {}, hwaddr: {}, ip: {}] connects to LED: {} with new ip: {}.", ueId, hwaddr, storedIp, ledId, ip);
                }

                if((ledId == storedLedId) && (ip.equals(storedIp))) {
                    log.info("[== handleUeAssociation ==] UE_Association_No_Update");
                }
            }
        }


        // handle VLC_header
        public void handleVLCHeader(String deviceId, String dstIp, int outPort, int DIP,
                                    int ledId, int ueId, int timeSlot, int serviceId) {
            // timeSlot value format: 0x01_01_01_01
            log.info("[==handleVLCHeader==] installGatewaySwitchFlowRule to deviceId: {}, dstIp: {}, outPort: {}, DIP: {}, ledId: {}, ueId: {}, timeSlot: {}, serviceId: {}",
                    deviceId, dstIp, outPort, DIP, ledId, ueId, timeSlot, serviceId);
            ueRuleService.installGatewaySwitchFlowRule(deviceId, dstIp, outPort, DIP,
                    ledId, ueId, timeSlot, serviceId);
        }

        // handle VLC_update
        public void handleVLCUpdate(String deviceId, String dstIp, int outPort, int DIP,
                                    int ledId, int ueId, int timeSlot, int serviceId) {
            // timeSlot value format: 0x01_01_01_01
            log.info("[==handleVLCUpdater==] updateGatewaySwitchFlowRule to deviceId: {}, dstIp: {}, outPort: {}, DIP: {}, ledId: {}, ueId: {}, timeSlot: {}, serviceId: {}",
                    deviceId, dstIp, outPort, DIP, ledId, ueId, timeSlot, serviceId);
            ueRuleService.updateGatewaySwitchFlowRule(deviceId, dstIp, outPort, DIP,
                    ledId, ueId, timeSlot, serviceId);
        }
    }

    public static Map<String, Integer> getMacUeId() {
        return Mac_UeId;
    }

    // Test Event post and receive
    private class EventTimerTask extends java.util.TimerTask {
        @Override
        public void run() {
            NetworkEvent UeAssociation = new NetworkEvent(NetworkEvent.Type.UE_ASSOCIATION, "UE_ASSOCIATION",
                    0x12,1, "pof:0000000000000001", "11:22:33:44:55:66", "192.168.109.172");
            networkEventService.post(UeAssociation);
            log.info("[== Test Event Post ==] Post event: {}", UeAssociation);

            try {
                Thread.currentThread().sleep(1 * 1000);
                NetworkEvent UeAssociation2 = new NetworkEvent(NetworkEvent.Type.UE_ASSOCIATION, "UE_ASSOCIATION2",
                        0xff, 2, "pof:0000000000000001", "01:02:03:04:05:06", "192.168.4.128");
                networkEventService.post(UeAssociation2);
                log.info("[== Test Event Post ==] Post event: {}", UeAssociation2);
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }

    // run periodically to start class EventTimerTask
    private void timeScheduler() {
        long delay = 0;
        long intervalPeriod = 10 * 1000;  // 10s
        EventTimerTask task = new EventTimerTask();
        java.util.Timer timer = new java.util.Timer();
        timer.schedule(task, delay, intervalPeriod);
        log.info("[== Test Event Post ==] Start event timer task.");
    }

}
