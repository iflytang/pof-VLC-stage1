package org.onosproject.pof.VLC;

import org.onosproject.event.EventListener;

/**
 * Created by tsf on 11/7/17.
 *
 * @Description listen to network events, should implement event function.
 */
public interface NetworkListener extends EventListener<NetworkEvent> {
    // should override void event(E var1) function, which implemented in NetworkMonitor
}
