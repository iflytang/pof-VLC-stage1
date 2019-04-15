package org.onosproject.pof.VLC;

import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Reference;
import org.apache.felix.scr.annotations.ReferenceCardinality;
import org.apache.felix.scr.annotations.Service;
import org.onlab.packet.*;
import org.onosproject.floodlightpof.protocol.action.OFAction;
import org.onosproject.net.ConnectPoint;
import org.onosproject.net.DeviceId;
import org.onosproject.net.PortNumber;
import org.onosproject.net.flow.DefaultTrafficTreatment;
import org.onosproject.net.flow.TrafficTreatment;
import org.onosproject.net.flow.instructions.DefaultPofActions;
import org.onosproject.net.flow.instructions.DefaultPofInstructions;
import org.onosproject.net.packet.DefaultOutboundPacket;
import org.onosproject.net.packet.PacketContext;
import org.onosproject.net.packet.PacketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * @author tsf
 * @date 18-6-19
 * @desp
 */
@Component(immediate = true)
@Service
public class ProtocolManager implements ProtocolService {

    @Reference(cardinality = ReferenceCardinality.MANDATORY_UNARY)
    protected PacketService packetService;

    private final Logger log = LoggerFactory.getLogger(getClass());

    // don't be implemented in controller
    @Override
    public void broadcastMsg() {}

    @Override
    public void parseRequest() {
        log.info("parse the request.");
    }

    // build and send 'reply' message
    @Override
    public Ethernet buildReply(Ethernet packet, short ledID, short ueID, short outgoingMsgType) {
        // parse 'packet-in' info
        MacAddress srcMAC = packet.getSourceMAC();          // ue's
        MacAddress dstMAC = packet.getDestinationMAC();     // controller's
        IPv4 ipv4Packet = (IPv4) packet.getPayload();
        String srcIP = Ip4Address.valueOf(ipv4Packet.getSourceAddress()).toString();
        String dstIP = Ip4Address.valueOf(ipv4Packet.getDestinationAddress()).toString();

        MacAddress agent_mac = MacAddress.valueOf("4C:CC:6A:37:7B:98");
        String agent_dst_ip = "192.168.1.100";
        int agent_dst_port = 9012;
        int agent_src_port = 9011;

        // build Ethernet frame, reverse the order of MAC
        Ethernet ethReply = new Ethernet();
        ethReply.setSourceMACAddress(dstMAC);         // controller's
        ethReply.setDestinationMACAddress(agent_mac);    // ue's
        ethReply.setEtherType(Ethernet.TYPE_IPV4);    // type = 0x0800

        // build IP packet
        IPv4 ipv4Reply = new IPv4();                  // default construction
        ipv4Reply.setSourceAddress(dstIP);            // controller's
        ipv4Reply.setDestinationAddress(agent_dst_ip);       // ue's
        ipv4Reply.setTtl((byte) 127);                 // ttl = 127

        // build UDP datagram TODO: update UDP port value
        UDP udpReply = new UDP();
        udpReply.setSourcePort(agent_src_port);     // controller's
        udpReply.setDestinationPort(agent_dst_port);// ue's

        // build REPLY payload
        Protocol reply = new Protocol();
        reply.setType((outgoingMsgType));             // outgoing msg type
        reply.setLength((short) Protocol.MIN_HEADER_LEN);
        reply.setTimestamp((byte) ledID);             // timestamp, not use now, TODO: 'ts' set as 'ledID'
        reply.setLedID(ledID);                        // ledID with max power
        reply.setUeID(ueID);                          // ueID assigned by controller
        reply.setMac(srcMAC.toBytes());               // ue's

        // build
        udpReply.setPayload(reply);
        ipv4Reply.setPayload(udpReply);
        ethReply.setPayload(ipv4Reply);

        return ethReply;
    }

    @Override
    public void sendReply(PacketContext context, Ethernet reply, DeviceId deviceId, PortNumber out_port) {
        if (reply != null) {
            TrafficTreatment.Builder builder = DefaultTrafficTreatment.builder();
            ConnectPoint sourcePoint = context.inPacket().receivedFrom();

            /* in VLC, the down-link is light (not wireless now).
             * we should change the `deviceId` and `out_port`, cannot still be wireless AP.
             * */
//            PortNumber out_port = sourcePoint.port();           // the default out_port
//            DeviceId deviceId = sourcePoint.deviceId();         // the default device name

            List<OFAction> actions = new ArrayList<>();
            actions.add(DefaultPofActions
                    .output((short) 0, (short) 0, (short) 0,(int) out_port.toLong()).action());
            builder.add(DefaultPofInstructions.applyActions(actions));

            context.block();
            packetService.emit(new DefaultOutboundPacket(deviceId,
                    builder.build(), ByteBuffer.wrap(reply.serialize())));

        }
    }

    // the 'request' and 'feedback' messages are almost same except the 'ueID'
    @Override
    public void parseACK() {
        parseRequest();
    }

    // the 'request' and 'feedback' messages are almost same except the 'ueID'
    @Override
    public void parseFeedback() {
        parseRequest();
    }
}
