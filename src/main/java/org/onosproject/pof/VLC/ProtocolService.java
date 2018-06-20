package org.onosproject.pof.VLC;

import org.onlab.packet.Ethernet;
import org.onosproject.net.packet.PacketContext;

/**
 * @author tsf
 * @date 18-6-19
 * @desp provide protocol communication service for UE online
 */


public interface ProtocolService {

    /**
     * UE/CONTROLLER broadcasting message
     */
    void broadcastMsg();

    /**
     *  UE/CONTROLLER parse request message sent to controller
     */
    void parseRequest();

    /**
     * CONTROLLER/UE build reply message to ue, assign with an ueID
     */
    Ethernet buildReply(Ethernet packet, short ledID, short ueID, short outgoingMsgType);

    void sendReply(PacketContext context, Ethernet reply);

    /**
     * UE/CONTROLLER ack message sent from ue
     */
    void parseACK();

    /**
     * UE/CONTROLLER process feedback message when transmitting data flow
     */
    void parseFeedback();
}
