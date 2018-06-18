package org.onosproject.pof.VLC;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tsf on 10/9/17.
 *
 * @Description define the attributes of attachment for ue, which we should collect.
 */
public class UeAssociation {

    /**
     * @param deviceId id of pof-switch (wifi, up-link)
     * @param port port of pof-switch (wifi, up-link)
     * @param ledId id of led (light, down-link)
     * @param power power of led (light, down-link)
     * @param ip ip of ue
     * @param associationHistory history of association with light-AP
     * */
    protected String deviceId;
    protected int port;
    protected short ledId;
    protected String ip;

    // initiation of up-link (attach to pof-switch with wifi)
    public UeAssociation(String deviceId, int port, String ip) {
        this.deviceId = deviceId;
        this.port = port;
        this.ledId = 0;   // attach to no one led
        this.ip = ip;
    }

    // initiation of down-link (attach to light-AP)
    public UeAssociation(short ledId, String ip) {
        this.deviceId = null;
        this.port = 0; // port starts from 1
        this.ledId = ledId;
        this.ip = ip;
    }

    // initiation of both links
    public UeAssociation(String deviceId, int port, short ledId, String ip) {
        this.deviceId = deviceId;
        this.port = port;
        this.ledId = ledId;

        this.ip = ip;
    }

    public String getDeviceId() {
        return this.deviceId;
    }

    public int getPort() {
        return this.port;
    }

    public short getLedId() {
        return this.ledId;
    }

    public String getIp() {
        return this.ip;
    }
}
