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

    final static int SRC_PORT = 0x0000;   // reply udp
    final static int DST_PORT = 0x0000;   // reply udp
    final static int DATA_DST_PORT = 4050; // data udp

    /* handshake type */
    final static int REQUEST = 0x0907;
    final static int REPLY = 0x0908;
//    final static int ACK1 = 0x0909;       // client/server ack
//    final static int ACK2 = 0x090a;       // server/client ack
    final static int FEEDBACK = 0x090b;

    // reply msg
    protected short type;
    protected short length;
    protected byte timestamp;
    protected short ledID;
    protected short ueID;
    protected byte[] mac;

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
