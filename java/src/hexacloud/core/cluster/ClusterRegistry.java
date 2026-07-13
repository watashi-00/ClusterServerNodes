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

    public Cluster createCluster(String name) {
        return clusters.computeIfAbsent(name, key -> {
            Cluster c = new Cluster(key, new ClusterEventBusManager());
            return c;
        });
    }

    public void registerCluster(Cluster cluster) {
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

    public void clear() {
        clusters.clear();
    }
}
