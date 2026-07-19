package hexacloud.core.utils.network;

public enum HttpHeader {
    AUTHORIZATION("Authorization"),
    CONTENT_TYPE("Content-Type"),
    UPGRADE("Upgrade"),
    CONNECTION("Connection"),
    SEC_WEBSOCKET_KEY("Sec-WebSocket-Key"),
    SEC_WEBSOCKET_VERSION("Sec-WebSocket-Version"),
    SEC_WEBSOCKET_ACCEPT("Sec-WebSocket-Accept"),
    RETRY_AFTER("Retry-After"),
    X_CLUSTER_TOKEN("X-Cluster-Token");

    private final String value;

    HttpHeader(String value) {
        this.value = value;
    }

    public String value() {
        return this.value;
    }

    @Override
    public String toString() {
        return this.value;
    }
}
