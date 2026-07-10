package hexacloud.core.cluster.event;

@FunctionalInterface
public interface ClusterListener {
    void onClusterEvent(ClusterEvent event);    
}
