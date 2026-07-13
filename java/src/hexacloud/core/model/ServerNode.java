package hexacloud.core.model;

/**
 * Represents a registered service node inside a cluster.
 * Contains connection coordinates, status metadata, and ping health-check configurations.
 */
public class ServerNode {
    private final String host;
    private final int port;
    private final NodeStatus status;
    private final boolean isExternal;
    private final boolean pingEnabled;
    private final String pingPath;
    private final String pingHeaderName;
    private final String pingHeaderValue;

    /**
     * Complete constructor to specify custom health check parameters.
     */
    public ServerNode(String host, int port, NodeStatus status, boolean isExternal,
                      boolean pingEnabled, String pingPath, String pingHeaderName, String pingHeaderValue) {
        this.host = host;
        this.port = port;
        this.status = status;
        this.isExternal = isExternal;
        this.pingEnabled = pingEnabled;
        this.pingPath = pingPath != null ? pingPath : "/";
        this.pingHeaderName = pingHeaderName;
        this.pingHeaderValue = pingHeaderValue;
    }

    /**
     * Constructor for default health-check settings.
     */
    public ServerNode(String host, int port, NodeStatus status, boolean isExternal) {
        this(host, port, status, isExternal, true, "/", null, null);
    }

    /**
     * Get the protocol and host (e.g. "http://localhost").
     */
    public String host() {
        return host;
    }

    /**
     * Get the service node port number.
     */
    public int port() {
        return port;
    }

    /**
     * Get the current status of the service node.
     */
    public NodeStatus status() {
        return status;
    }

    /**
     * Returns true if the node is located outside the local intranet/gateway.
     */
    public boolean isExternal() {
        return isExternal;
    }

    /**
     * Returns true if health-check ping requests are enabled for this node.
     */
    public boolean pingEnabled() {
        return pingEnabled;
    }

    /**
     * Get the health-check path endpoint (e.g. "/healthz").
     */
    public String pingPath() {
        return pingPath;
    }

    /**
     * Get the custom health-check API token header key.
     */
    public String pingHeaderName() {
        return pingHeaderName;
    }

    /**
     * Get the custom health-check API token header value.
     */
    public String pingHeaderValue() {
        return pingHeaderValue;
    }

    /**
     * Create a new immutable ServerNode instance with an updated status.
     */
    public ServerNode withStatus(NodeStatus newStatus) {
        return new ServerNode(this.host, this.port, newStatus, this.isExternal,
                this.pingEnabled, this.pingPath, this.pingHeaderName, this.pingHeaderValue);
    }

    /**
     * Return host and port formatted together (e.g. "http://localhost:8080").
     */
    public String getFullHost() {
        return this.host + ":" + this.port;
    }

    @Override
    public String toString() {
        return "ServerNode{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", status=" + status +
                ", isExternal=" + isExternal +
                ", pingEnabled=" + pingEnabled +
                ", pingPath='" + pingPath + '\'' +
                '}';
    }
}
