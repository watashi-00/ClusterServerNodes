package hexacloud.core.cluster;

import java.util.Collection;
import java.util.concurrent.ConcurrentHashMap;
import hexacloud.core.cluster.event.ClusterEventBusManager;

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
        // The constructor of Cluster automatically registers itself by calling
        // ClusterRegistry.getInstance().registerCluster(this);
        return new Cluster(name, new ClusterEventBusManager());
    }

    public synchronized void registerCluster(Cluster cluster) {
        if (cluster != null) {
            clusters.put(cluster.getClusterName(), cluster);
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
    }
}
