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
    private int port = 3000;

    public LocalGatewayAdapter(String clusterName) {
        DebugUtils.log("Creating LocalGatewayAdapter for cluster: " + clusterName);
        this.clusterEventManager = new ClusterEventBusManager();
        this.clusterManager = new ClusterManager(new Cluster(clusterName, this.clusterEventManager), this.clusterEventManager);
        this.schedulerPing = new ThreadPingScheduler(this.clusterEventManager);
    }

    public LocalGatewayAdapter(String clusterName, int port) {
        DebugUtils.log("Creating LocalGatewayAdapter for cluster: " + clusterName + " with pre-configured server port " + port);
        this.clusterEventManager = new ClusterEventBusManager();
        this.clusterManager = new ClusterManager(new Cluster(clusterName, this.clusterEventManager), this.clusterEventManager);
        this.schedulerPing = new ThreadPingScheduler(this.clusterEventManager);
        this.port = port;
        this.serverManager = new ServerManager(port, this.clusterManager.getCluster(), this.clusterEventManager);
    }

    @Override
    public GatewayPort port(int port) {
        this.port = port;
        return this;
    }

    @Override
    public GatewayPort pingInterval(int intervalInSeconds) {
        schedulerPing.setInterval(intervalInSeconds);
        return this;
    }

    @Override
    public GatewayPort startPingScheduler() {
        schedulerPing.startPingScheduler(() -> this.clusterManager.getClusterList());
        return this;
    }
    
    @Override
    public GatewayPort startPingScheduler(int intervalInSeconds) {
        schedulerPing.setInterval(intervalInSeconds);
        schedulerPing.startPingScheduler(() -> this.clusterManager.getClusterList());
        return this;
    }
    
	@Override
	public GatewayPort registerAllServers() {
        clusterManager.registerAllServers();
        return this;
	}

    @Override
    public GatewayPort registerServer(int port) {
        clusterManager.registerServer(port);
        return this;
    }

    @Override
    public GatewayPort registerServer(int port, NodeStatus status) {
        clusterManager.registerServer(port, status);
        return this;
    }

    @Override
    public GatewayPort registerServer(ServerNode node) {
        clusterManager.registerServer(node);
        return this;
    }

	@Override
	public GatewayPort deregisterAllServers() {
        clusterManager.deregisterAllServers();
        return this;
	}

	@Override
	public GatewayPort deregisterServer(String fullHost) {
        clusterManager.deregisterServer(fullHost);
        return this;
	}

	@Override
	public GatewayPort deregisterLastServer() {
        clusterManager.deregisterLastServer();
        return this;
	}

	@Override
	public GatewayPort listClusterNodes() {
        clusterManager.listClusterNodes();
        return this;
	}

	@Override
	public GatewayPort setPingInterval(int pingInterval) {
        schedulerPing.setInterval(pingInterval);
        return this;
	}

    @Override
    public GatewayPort stopPingScheduler() {
        schedulerPing.stopPingScheduler();
        return this;
    }

    private void ensureServerManagerInitialized() {
        if(this.serverManager == null) {
            this.serverManager = new ServerManager(this.clusterManager.getCluster(), this.clusterEventManager);
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
    public GatewayPort listen(int port) {
        this.port = port;
        ensureServerManagerInitialized();
        DebugUtils.log("LocalGatewayAdapter: Starting server listeners on port " + port);
        this.serverManager.listen(port);
        return this;
    }

    @Override
    public GatewayPort listen() {
        listen(this.port);
        return this;
    }

    @Override
    public ClusterEventBusManager eventManager() {
        return this.clusterEventManager;
    }
}
