package org.onosproject.pof.VLC;

/**
 * Created by tsf on 11/7/17.
 */
public interface NetworkEventService {

    void addListener(NetworkListener listener);

    void removeListener(NetworkListener listener);

    void post(NetworkEvent event);

}
