package org.onosproject.pof.VLC;

import org.onosproject.event.AbstractEvent;
import org.onosproject.net.packet.PacketContext;

/**
 * Created by tsf on 11/7/17.
 *
 * @Description defines the network events
 */
public class NetworkEvent extends AbstractEvent<NetworkEvent.Type, String> {

    protected int ueId;
    protected int ledId;
    protected  String deviceId;
    protected int out_port;
    protected String hwaddr;
    protected String ip;
    protected String dmac;
    protected PacketContext context;

    // network events type
    public enum Type {
        UE_ASSOCIATION,     // when ue associates with light-AP according to the broadcast frame, [store the ue message]
        UE_DISASSOCIATION,  // when ue remove from light-AP according to the broadcast frame, [remove the old ue message]
        VLC_HEADER,        // when light-AP changes VLC header, such as time slot, then [update] the gw flow rules
        VLC_UPDATE,
        DHCP_LEASE,         // when ue gets an ip, then assign ue an [id]
        PATH_CALCULATION,   // when first frame comes, [calculate path]
        PATH_UPDATE         // when handover according to broadcast frame, [update path]
    }

    // for default construction
    public NetworkEvent(NetworkEvent.Type type, String subject) {
        super(type, subject);
    }

    // for UE_ASSOCIATION or UE_DISASSOCIATION
    public NetworkEvent(NetworkEvent.Type type, String subject,int ueId, int ledId, String deviceId, int out_port, String hwaddr, String ip, PacketContext context, String dmac) {
        super(type, subject);
        this.ueId = ueId;
        this.ledId = ledId;
        this.deviceId = deviceId;
        this.out_port = out_port;
        this.hwaddr = hwaddr;
        this.ip = ip;
        this.context = context;
        this.dmac =dmac;
    }

    public NetworkEvent(NetworkEvent.Type type, String subject, PacketContext context) {
        super(type, subject);
        this.context = context;
    }

    public int getUeId() {
        return this.ueId;
    }

    public int getLedId() {
        return this.ledId;
    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public int getOutPort() {
        return this.out_port;
    }

    public String getHwaddr() {
        return this.hwaddr;
    }

    public String getIp() {
        return this.ip;
    }

    public String getDmac() {
        return this.dmac;
    }

    public PacketContext getContext() {
        return this.context;
    }

}
