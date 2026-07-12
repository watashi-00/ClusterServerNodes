package hexacloud.core.cluster.event;

import hexacloud.core.event.EventBusManager;

public class ClusterEventBusManager extends EventBusManager {

    @SuppressWarnings("unchecked")
    public <T extends ClusterEvent> void sub(Class<T> eventType, ClusterListener listener) {
        super.sub(eventType, (hexacloud.core.event.EventListener<T>) listener);
    } 

    public void dispatch(ClusterEvent event) {
        super.dispatch(event);
    }
}
