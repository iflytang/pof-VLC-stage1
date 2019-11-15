package org.onosproject.pof.VLC;

import org.onlab.packet.BasePacket;
import org.onlab.packet.IPacket;

import java.nio.ByteBuffer;

import static com.google.common.base.Preconditions.checkArgument;

/**
 * @author tsf
 * @date 18-6-19
 * @desp online protocol of reply msg
 *       1. ue --> Broadcast(request) --> controller
 *       2. ue <--   Reply   <-- controller
 *    // 3. ue -->    Ack    --> controller (@deprecated)
 *       4. ue --> Broadcast(feedback) --> controller
 */

public class Protocol extends BasePacket {

    // reply length
    final static int MIN_HEADER_LEN = 15; // type:2+len:2+timestamp:1+led:2+ue:2+mac:6 = 15B

    final static int SRC_PORT = 9019;   // reply udp (UE broadcast frame)
    final static int DST_PORT = 9020;   // reply udp (UE broadcast frame)
    final static int DATA_DST_PORT = 4050; // data udp

    /* handshake type */
    final static int REQUEST = 0x0907;
    final static int REPLY = 0x0908;
    final static int CLCACHE = 0x0909;
//    final static int ACK1 = 0x0909;       // client/server ack
//    final static int ACK2 = 0x090a;       // server/client ack
    final static int FEEDBACK = 0x090b;

    //wireless_ap type
    final static short data =0x0800;
    final static short control=0x0801;


    /* data_flow type (VLC Header). */
    final static int VLC_TYPE = 0x1918;
    final static int VLC_LEN = 0x10;       // ts:2+type:2+len:2+led:2+ue:2+service:4

    /* VLC header {off,len} in bit. */
    final static short VLC_H_OFF = 96;          // eth start location, 12B * 8 = 96b
    final static short VLC_H_LEN = VLC_LEN * 8;
    final static short VLC_F_LEN_OFF = VLC_H_OFF + 32;   // ts:2 + type:2
    final static short VLC_F_LEN_LEN = 16;               // len:2B * 8 = 16b

    final static short DMAC__OFF=0;
    final static short DMAC__LEN=48;   //6B*8=48


    /* DIP filed {off,len} in bit. */
    final static short DIP_F_OFF = 240;
    final static short DIP_F_LEN = 32;
    final static short IP_F_LEN_OFF = 128;
    final static short IP_F_LEN_LEN = 16;

    /* UDP filed {off, len} in bit */
    final static short UDP_F_LEN_OFF = 304;
    final static short UDP_F_LEN_LEN = 16;
    final static short UDP_F_CKM_OFF = 320;
    final static short UDP_F_CKM_LEN = 16;

    /* UE_LED to be matched at wireless_ap to drop packet. */
    final static short AP_UELED_F_OFF = VLC_H_OFF + 32;   // type:2 + len:2 + ue:2 + led:2 + power:2
    final static short AP_UELED_F_LEN = 32;

    /*CONTROL FRAME*/
    final static short CONTROL_TYPE=12;
    final static short CONTROl_LEN=14;
    final static short CONTROL_UEID=16;
    final static short CONTROL_LEDID=18;
    final static short CONTROL_SIGNAL=19;

    // reply msg
    protected short type;
    protected short length;
    protected byte timestamp;
    protected short ledID;
    protected short ueID;
    protected byte[] mac;
    protected short clcache;

    public Protocol() {
        super();
        this.type = REPLY;
        this.length = MIN_HEADER_LEN;    // 2+2+1+2+2+6=15
    }

    public short getType() {
        return this.type;
    }

    public void setType(final short type) {
        this.type = type;
    }

    public short getClcache(){return this.clcache;}

    public void setClcache(final short clcache){ this.clcache= clcache;}

    public short getLength() {
        return this.length;
    }

    public void setLength(short length) {
        this.length = length;
    }

    public byte getTimestamp() {
        return this.timestamp;
    }

    public void setTimestamp(byte timestamp) {
        this.timestamp = timestamp;
    }

    public short getLedID() {
        return this.ledID;
    }

    public void setLedID(short ledID) {
        this.ledID = ledID;
    }

    public short getUeID() {
        return this.ledID;
    }

    public void setUeID(short ueID) {
        this.ueID = ueID;
    }

    public byte[] getMac() {
        return this.mac;
    }

    public void setMac(final byte[] mac) {
        this.mac = mac;
    }

    @Override
    public byte[] serialize() {
        // not guaranteed to retain length/exact format
        this.resetChecksum();

        final byte[] data = new byte[this.length];
        final ByteBuffer bb = ByteBuffer.wrap(data);
        bb.putShort(this.type);
        bb.putShort(this.length);
        bb.put(this.timestamp);
        bb.putShort(this.ledID);
        bb.putShort(this.ueID);
        checkArgument(this.mac.length <= 6,
                "Hardware address is too long (%s bytes)", this.mac.length);
        bb.put(this.mac);

        return data;
    }

    @Override
    public IPacket deserialize(final byte[] data, final int offset,
                               final int length) {
        final ByteBuffer bb = ByteBuffer.wrap(data, offset, length);

        if (bb.remaining() < Protocol.MIN_HEADER_LEN) {
            return this;
        }

        this.type = bb.getShort();
        this.length = bb.getShort();
        this.timestamp = bb.get();
        this.ledID = bb.getShort();
        this.ueID = bb.getShort();
        this.mac = new byte[6];     // mac len = 6
        bb.get(this.mac);

        return this;
    }
}
