package org.onosproject.pof.VLC;

import java.util.ArrayList;
import java.util.List;

/**
 * Created by tsf on 10/9/17.
 *
 * @Description define basic attribute of UE
 */
public class UE {

    /**
     * @param ueId user equipments' id
     * @param ledId the led that ue associated with
     * @param hwaddr the MAC address of ue
     * @param ip the ip that ue assigned
     * @param ueAssociation the attribute of ue attached with light-AP
     * @param associationHistory the history of ue's attachment
    * */
    protected short ueId;
    protected short ledId;

    protected String hwaddr;
    protected String ip;

    protected UeAssociation ueAssociation;
    protected List<UeAssociation> associationHistory;

    public UE() {
        this.ueId = 0;
        this.ledId = 0;
        this.hwaddr = "000000000000";
        this.ip = "0.0.0.0";
        this.ueAssociation = null;
        this.associationHistory = new ArrayList<>();
    }

    public UE(short ueId, short ledId, String hwaddr, String ip) {
        this.ueId = ueId;
        this.ledId = ledId;
        this.hwaddr = hwaddr;
        this.ip = ip;
        this.ueAssociation = null;
        this.associationHistory =  new ArrayList<>();
    }

    public UE(short ueId, short ledId, String hwaddr, String ip, UeAssociation ueAssociation) {
        this.ueId = ueId;
        this.ledId = ledId;
        this.hwaddr = hwaddr;
        this.ip = ip;
        this.ueAssociation = ueAssociation;
        this.associationHistory =  new ArrayList<>();
    }

    public short getUeId() {
        return this.ueId;
    }

    public short getLedId() {
        return this.ledId;
    }

    public String getHwaddr() {
        return this.hwaddr;
    }

    public String getIp() {
        return this.ip;
    }

    public void setUeId(short ueId) {
        this.ueId = ueId;
    }

    public void setLedId(short ledId) {
        this.ledId = ledId;
    }

    public void setHwaddr(String hwaddr) {
        this.hwaddr = hwaddr;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    // if current.association not null, return current association
    public UeAssociation getCurrentAssociation() {
        if(this.ueAssociation != null) {
            return this.ueAssociation;
        } else {
            return null;
        }
    }

    // if history.association not null, return previous association
    public UeAssociation getPreviousAssociation() {
        UeAssociation previousAssocition;
        if(this.associationHistory != null) {
            int index = this.associationHistory.size() - 1;
            previousAssocition = this.associationHistory.get(index);
        } else {
            previousAssocition = null;
        }
        return previousAssocition;
    }

    // store before setting ue association
    public void setUeAssociation(UeAssociation ueAssociation) {
        if(this.ueAssociation != null) {
            this.associationHistory.add(this.ueAssociation);
        }
        this.ueAssociation = ueAssociation;
    }
}
