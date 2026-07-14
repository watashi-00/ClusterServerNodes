package hexacloud.core.model;

/**
 * Supported health-check ping protocols for service nodes.
 */
public enum PingProtocol {
    HTTP,
    WEBSOCKET,
    TCP,
    UDP,
    GRPC,
    NONE
}
