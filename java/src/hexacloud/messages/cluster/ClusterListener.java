package hexacloud.messages.cluster;

@FunctionalInterface
public interface ClusterListener {
    void onClusterEvent(ClusterEvent event);    
}
