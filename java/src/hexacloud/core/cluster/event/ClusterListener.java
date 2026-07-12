package hexacloud.core.cluster.event;

import hexacloud.core.event.EventListener;

@FunctionalInterface
public interface ClusterListener extends EventListener<ClusterEvent> {
    void onClusterEvent(ClusterEvent event);

    @Override
    default void onEvent(ClusterEvent event) {
        onClusterEvent(event);
    }    
}
