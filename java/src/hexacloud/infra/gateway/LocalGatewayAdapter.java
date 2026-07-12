package hexacloud.infra.gateway;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterManager;
import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.GatewayPort;
import hexacloud.core.server.ServerManager;
import hexacloud.infra.network.ThreadPingScheduler;
import hexacloud.core.utils.DebugUtils;

class LocalGatewayAdapter implements GatewayPort {

    private final ClusterManager clusterManager;
    private final ClusterEventBusManager clusterEventManager;
    private final ThreadPingScheduler schedulerPing;
    private ServerManager serverManager;

    public LocalGatewayAdapter(String clusterName) {
        DebugUtils.log("Creating LocalGatewayAdapter for cluster: " + clusterName);
        this.clusterEventManager = new ClusterEventBusManager();
        this.clusterManager = new ClusterManager(new Cluster(clusterName), this.clusterEventManager);
        this.schedulerPing = new ThreadPingScheduler(this.clusterEventManager);
    }

    public LocalGatewayAdapter(String clusterName, int port) {
        DebugUtils.log("Creating LocalGatewayAdapter for cluster: " + clusterName + " with pre-configured server port " + port);
        this.clusterEventManager = new ClusterEventBusManager();
        this.clusterManager = new ClusterManager(new Cluster(clusterName), this.clusterEventManager);
        this.schedulerPing = new ThreadPingScheduler(this.clusterEventManager);
        this.serverManager = new ServerManager(port, this.clusterManager.getCluster());
    }

    @Override
    public void startPingScheduler() {
        schedulerPing.startPingScheduler(() -> this.clusterManager.getClusterList());
    }
    
    @Override
    public void startPingScheduler(int intervalInSeconds) {
        schedulerPing.setInterval(intervalInSeconds);
        schedulerPing.startPingScheduler(() -> this.clusterManager.getClusterList());
    }
    
	@Override
	public void registerAllServers() {
        clusterManager.registerAllServers();
	}

    @Override
    public void registerServer(int port) {
        clusterManager.registerServer(port);
    }

    @Override
    public void registerServer(int port, NodeStatus status) {
        clusterManager.registerServer(port, status);
    }

    @Override
    public void registerServer(ServerNode node) {
        clusterManager.registerServer(node);
    }

	@Override
	public void deregisterAllServers() {
        clusterManager.deregisterAllServers();
	}

	@Override
	public void deregisterServer(String fullHost) {
        clusterManager.deregisterServer(fullHost);
	}

	@Override
	public void deregisterLastServer() {
        clusterManager.deregisterLastServer();
	}

	@Override
	public void listClusterNodes() {
        clusterManager.listClusterNodes();
	}

	@Override
	public void setPingInterval(int pingInterval) {
        schedulerPing.setInterval(pingInterval);
	}

    @Override
    public void stopPingScheduler() {
        schedulerPing.stopPingScheduler();
    }

    private void ensureServerManagerInitialized() {
        if(this.serverManager == null) {
            this.serverManager = new ServerManager(this.clusterManager.getCluster());
        }
    }

    @Override
    public GatewayPort enableTelnet(boolean enabled) {
        ensureServerManagerInitialized();
        this.serverManager.enableTelnet(enabled);
        return this;
    }

    @Override
    public GatewayPort enableHttp(boolean enabled) {
        ensureServerManagerInitialized();
        this.serverManager.enableHttp(enabled);
        return this;
    }

    @Override
    public GatewayPort enableWs(boolean enabled) {
        ensureServerManagerInitialized();
        this.serverManager.enableWs(enabled);
        return this;
    }

    @Override
    public void listen(int port) {
        ensureServerManagerInitialized();
        DebugUtils.log("LocalGatewayAdapter: Starting server listeners on port " + port);
        this.serverManager.listen(port);
    }

}
