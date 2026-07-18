package hexacloud.infra.network;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.model.PingResult;
import hexacloud.core.ports.PingClientPort;

public class MultiProtocolPingAdapter implements PingClientPort {

    private static final ConcurrentHashMap<String, Pattern> PATTERN_CACHE =
        new ConcurrentHashMap<>();

    public MultiProtocolPingAdapter() {
    }


    @Override
    public CompletableFuture<PingResult> fetchPingAsync(
            String clusterName,
            ServerNode node
    ) {
        throw new UnsupportedOperationException(
            "fetchPingAsync is not implemented for Java 8"
        );
    }


    private CompletableFuture<PingResult> fetchWsPingAsync(
            String clusterName,
            ServerNode node,
            String uriStr
    ) {
        throw new UnsupportedOperationException(
            "WebSocket ping is not available on Java 8"
        );
    }


    private CompletableFuture<PingResult> fetchTcpPingAsync(
            String clusterName,
            ServerNode node,
            String uriStr
    ) {
        throw new UnsupportedOperationException(
            "TCP ping is not available on Java 8"
        );
    }


    private CompletableFuture<PingResult> fetchUdpPingAsync(
            String clusterName,
            ServerNode node,
            String uriStr
    ) {
        throw new UnsupportedOperationException(
            "UDP ping is not available on Java 8"
        );
    }


    private CompletableFuture<PingResult> fetchGrpcPingAsync(
            String clusterName,
            ServerNode node,
            String uriStr
    ) {
        throw new UnsupportedOperationException(
            "gRPC ping is not available on Java 8"
        );
    }


    private String extractJsonField(String json, String field) {
        throw new UnsupportedOperationException(
            "JSON extraction is not implemented on Java 8"
        );
    }


    private void setNode(ServerNode node) {
        setNode(node, 0, 0.0, 0.0);
    }


    private void setNode(
            ServerNode node,
            int latency,
            String runtime
    ) {
        setNode(node, latency, 0.0, 0.0, runtime);
    }


    private void setNode(
            ServerNode node,
            int latency,
            double cpuUsage,
            double ramUsage
    ) {
        node.setLatencyMs(latency);
        node.setCpuUsage(cpuUsage);
        node.setRamUsage(ramUsage);
    }


    private void setNode(
            ServerNode node,
            int latency,
            double cpuUsage,
            double ramUsage,
            String runtime
    ) {
        setNode(node, latency, cpuUsage, ramUsage);
        node.setRuntime(runtime);
    }
}