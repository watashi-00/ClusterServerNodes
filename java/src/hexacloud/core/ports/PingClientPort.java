package hexacloud.core.ports;

import java.util.concurrent.CompletableFuture;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

/**
 * Port interface for service node health-check ping clients.
 * Decouples the ping execution mechanisms (HTTP, WebSocket, TCP, etc.) from core scheduling.
 */
public interface PingClientPort {
    CompletableFuture<NodeStatus> fetchPingAsync(String clusterName, ServerNode node);
}
