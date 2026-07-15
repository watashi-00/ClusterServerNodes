package hexacloud.infra.gateway;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.NodeBuilderPort;

public class NodeBuilder implements NodeBuilderPort {

    private final hexacloud.core.ports.GatewayBuilderPort parent;
    private final Cluster cluster;
    private final String host;
    private final int port;
    private boolean pingEnabled = true;
    private String pingPath = "/";
    private String pingHeaderName = null;
    private String pingHeaderValue = null;
    private boolean isExternal = false;

    public NodeBuilder(hexacloud.core.ports.GatewayBuilderPort parent, Cluster cluster, String host, int port) {
        this.parent = parent;
        this.cluster = cluster;
        this.host = host;
        this.port = port;
    }

    @Override
    public NodeBuilderPort pingEnabled(boolean enabled) {
        this.pingEnabled = enabled;
        return this;
    }

    @Override
    public NodeBuilderPort pingPath(String path) {
        this.pingPath = path;
        return this;
    }

    @Override
    public NodeBuilderPort pingHeader(String name, String value) {
        this.pingHeaderName = name;
        this.pingHeaderValue = value;
        return this;
    }

    @Override
    public NodeBuilderPort external(boolean external) {
        this.isExternal = external;
        return this;
    }

    @Override
    public hexacloud.core.ports.GatewayBuilderPort register() {
        ServerNode node = new ServerNode(
            host, port, NodeStatus.OFFLINE, isExternal,
            pingEnabled, pingPath, pingHeaderName, pingHeaderValue
        );
        if (hexacloud.core.config.ClusterStatePersistence.isStateLoaded()) {
            return parent;
        }
        cluster.registerServer(node);
        return parent;
    }
}
