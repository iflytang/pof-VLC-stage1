package org.onosproject.pof.VLC;

import org.apache.felix.scr.annotations.*;
import org.onosproject.event.EventDeliveryService;
import org.onosproject.event.ListenerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        timeScheduler();
    }

    @Deactivate
    public void deactivate() {
        networkEventService.removeListener(listener);
        log.info("Network Monitor Module Stopped.");
    }

    public class InternalNetworkEventListener implements NetworkListener {
        @Override
        public void event(NetworkEvent event) {
            // TODO process events
            if(event.type().equals(NetworkEvent.Type.UE_ASSOCIATION)) {
                log.info("<=======================================");
                log.info("[== Test Event Received ==] Type: {}\n LedId: {}\n Hwaddr: {}\n IP: {}", event.type(), event.getLedId(), event.getHwaddr(), event.getIp());
                log.info("=======================================>");
            }

        }
    }

    // Test Event post and receive
    private class EventTimerTask extends java.util.TimerTask {
        @Override
        public void run() {
            NetworkEvent UeAssociation = new NetworkEvent(NetworkEvent.Type.UE_ASSOCIATION, "UE_ASSOCIATION",
                    1, "11:22:33:44:55:66", "192.168.109.172");
            networkEventService.post(UeAssociation);
            log.info("[== Test Event Post ==] Post event: {}", UeAssociation);
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
