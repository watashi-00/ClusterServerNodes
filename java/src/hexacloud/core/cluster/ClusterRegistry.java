package hexacloud.core.cluster;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.config.ClusterStatePersistence;

public class ClusterRegistry {
    private static final ClusterRegistry INSTANCE = new ClusterRegistry();
    private final ConcurrentHashMap<String, Cluster> clusters = new ConcurrentHashMap<>();

    private ClusterRegistry() {}

    public static ClusterRegistry getInstance() {
        return INSTANCE;
    }

    public synchronized Cluster createCluster(String name) {
        Cluster existing = clusters.get(name);
        if (existing != null) {
            return existing;
        }
        Cluster c = new Cluster(name, new ClusterEventBusManager());
        // The Cluster constructor registers itself and triggers saveState()
        return c;
    }

    public synchronized void registerCluster(Cluster cluster) {
        if (cluster != null) {
            clusters.put(cluster.getClusterName(), cluster);
            hexacloud.core.event.EventBusManager.getGlobal().dispatch(new hexacloud.core.cluster.event.ClusterEvent.ClusterRegistered(cluster.getClusterName()));
            ClusterStatePersistence.saveState();
        }
    }

    public Cluster getCluster(String name) {
        return clusters.get(name);
    }

    public Collection<Cluster> getClusters() {
        return clusters.values();
    }

    public synchronized void clear() {
        clusters.clear();
        ClusterStatePersistence.saveState();
    }
}
