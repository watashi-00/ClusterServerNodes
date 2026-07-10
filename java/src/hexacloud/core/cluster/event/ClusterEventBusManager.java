package hexacloud.core.cluster.event;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import hexacloud.core.utils.DebugUtils;

public class ClusterEventBusManager {
    private final Map<Class<? extends ClusterEvent>, List<ClusterListener>> channels = new HashMap<>();

    public <T extends ClusterEvent> void sub(Class<T> eventType, ClusterListener listener) {
        channels.computeIfAbsent(eventType, k -> new ArrayList<>()).add(listener);
    } 

    public void dispatch(ClusterEvent event) {
        
        Class<? extends ClusterEvent> eventType = event.getClass();
        List<ClusterListener> listeners = channels.get(eventType);

        DebugUtils.log("Dispatch " + event);

        if(listeners != null) {
            for(var listener : listeners) {
                listener.onClusterEvent(event);
            }
        }
    }
}
