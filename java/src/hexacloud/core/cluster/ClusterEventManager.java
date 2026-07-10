package hexacloud.core.cluster;

import java.util.List;

import hexacloud.core.cluster.event.ClusterEvent;
import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.cluster.event.ClusterListener;
import hexacloud.core.cluster.event.NodeStatusChanged;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

public class ClusterEventManager implements ClusterListener {

    private final Cluster cluster;
    private final ClusterEventBusManager eventManager;

    public ClusterEventManager(Cluster cluster, ClusterEventBusManager eventManager) {
        this.cluster = cluster;
        this.eventManager = eventManager;

        this.eventManager.sub(NodeStatusChanged.class, this);
    }

    public List<ServerNode> getCluster() {
        return this.cluster.getCluster();
    }

    public void startAll() {
        this.cluster.startAll();
    }

    public void start(int port) {
        this.cluster.start(port);
    }

    public void start(ServerNode node) {
        this.cluster.start(node);
    }

	public void start(int port, boolean isExternal) {
        this.cluster.start(port, isExternal);
	}

    public void start(int port, NodeStatus status) {
        this.cluster.start(port, status);
    }

	public void start(int port, String host, boolean isExternal) {
        this.cluster.start(port, host, isExternal);
	}

	public void stopAll() {
        this.cluster.stopAll();
	}

	public void stop(int port) {
        this.cluster.stop(port);
	}

	public void stop() {
        this.cluster.stop();
	}

	public void listClusterNodes() {
        this.cluster.listClusterNodes();
	}

    @Override
    public void onClusterEvent(ClusterEvent event) {
        if(event instanceof NodeStatusChanged statusEvent) {
            System.out.println("[Received] " + statusEvent);
        }
    }

}
