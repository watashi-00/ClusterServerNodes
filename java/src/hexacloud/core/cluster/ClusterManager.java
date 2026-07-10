package hexacloud.core.cluster;

import java.util.List;

import hexacloud.core.cluster.event.ClusterEvent;
import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.cluster.event.ClusterListener;
import hexacloud.core.cluster.event.NodeStatusChanged;
import hexacloud.core.contracts.ImplCluster;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.utils.DebugUtils;

public class ClusterManager implements ClusterListener, ImplCluster {

    private final Cluster cluster;
    private final ClusterEventBusManager eventManager;

    public ClusterManager(Cluster cluster, ClusterEventBusManager eventManager) {
        this.cluster = cluster;
        this.eventManager = eventManager;

        this.eventManager.sub(NodeStatusChanged.class, this);
    }

    public List<ServerNode> getCluster() {
        return this.cluster.getCluster();
    }
    
    @Override
    public void registerAllServers() {
        this.cluster.registerAllServers();
    }

    @Override
    public void registerServer(int port) {
        this.cluster.registerServer(port);
    }

    @Override
    public void registerServer(ServerNode node) {
        this.cluster.registerServer(node);
    }

    @Override
	public void registerServer(int port, boolean isExternal) {
        this.cluster.registerServer(port, isExternal);
	}

    @Override
    public void registerServer(int port, NodeStatus status) {
        this.cluster.registerServer(port, status);
    }

    @Override
	public void registerServer(int port, String host, boolean isExternal) {
        this.cluster.registerServer(port, host, isExternal);
	}

    @Override
	public void deregisterAllServers() {
        this.cluster.deregisterAllServers();
	}

    @Override
	public void deregisterServer(String fullHost) {
        this.cluster.deregisterServer(fullHost);
	}

    @Override
	public void deregisterLastServer() {
        this.cluster.deregisterLastServer();
	}

    @Override
	public void listClusterNodes() {
        this.cluster.listClusterNodes();
	}

    @Override
    public void onClusterEvent(ClusterEvent event) {
        if(event instanceof NodeStatusChanged statusEvent) {
            DebugUtils.log("Received " + statusEvent);
            this.cluster.updateStatusServer(statusEvent.host(), statusEvent.status());
        }
    }

}
