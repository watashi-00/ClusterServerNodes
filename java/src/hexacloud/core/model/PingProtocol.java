package hexacloud.core.model;

/**
 * Supported health-check ping protocols for service nodes.
 */
public enum PingProtocol {
    HTTP("HTTP"),
    WEBSOCKET("WebSocket"),
    TCP("TCP"),
    UDP("UDP"),
    GRPC("gRPC"),
    NONE("None");

    private final String friendlyName;

    PingProtocol(String friendlyName) {
        this.friendlyName = friendlyName;
    }

    public String getFriendlyName() {
        return friendlyName;
    }
}
