package org.onosproject.pof.VLC;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.onosproject.event.AbstractListenerManager;

/**
 * Created by tsf on 11/7/17.
 *
 * @Description implement the NetworkEventService interface
 */

@Component(immediate = true)
@Service
public class NetworkEventManager extends AbstractListenerManager<NetworkEvent, NetworkListener> implements NetworkEventService {

    @Activate
    protected void activate() {
        eventDispatcher.addSink(NetworkEvent.class, listenerRegistry);
    }

    @Deactivate
    protected void deactivate() {
        eventDispatcher.removeSink(NetworkEvent.class);
    }

    @Override
    public void addListener(NetworkListener listener) {
        listenerRegistry.addListener(listener);
    }

    @Override
    public void removeListener(NetworkListener listener) {
        listenerRegistry.removeListener(listener);
    }

   @Override
   public void post(NetworkEvent event) {
       if(event != null && this.eventDispatcher != null) {
           this.eventDispatcher.post(event);
       }
   }
}
