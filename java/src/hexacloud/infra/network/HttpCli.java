package hexacloud.infra.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.ThreadManager;
import hexacloud.core.config.ClusterConfig;

class HttpCli {

    private final HttpClient client;

    public HttpCli() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(ClusterConfig.HTTP_CONNECT_TIMEOUT)
            .version(ClusterConfig.HTTP_VERSION)
            .executor(ThreadManager.newVirtualThreadPool())
            .build();
    }

    CompletableFuture<NodeStatus> fetchPingAsync(ServerNode node) {
        String uriStr = node.getFullHost();
        String path = node.pingPath();
        if (path != null && !path.isEmpty()) {
            if (!path.startsWith("/")) {
                path = "/" + path;
            }
            if (!uriStr.endsWith("/") && !path.equals("/")) {
                uriStr = uriStr + path;
            } else if (uriStr.endsWith("/") && !path.equals("/")) {
                uriStr = uriStr.substring(0, uriStr.length() - 1) + path;
            }
        }

        HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
            .uri(URI.create(uriStr))
            .timeout(ClusterConfig.HTTP_REQUEST_TIMEOUT)
            .GET();

        if (node.pingHeaderName() != null && !node.pingHeaderName().trim().isEmpty() 
            && node.pingHeaderValue() != null) {
            reqBuilder.header(node.pingHeaderName().trim(), node.pingHeaderValue());
        }

        return client.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
            .thenApply(res -> {
                if(res.statusCode() >= 200 && res.statusCode() < 300) {
                    return NodeStatus.ONLINE;
                } else {
                    return NodeStatus.UNSTABLE;
                }
            })
            .exceptionally(ex -> {
                DebugUtils.error("Ping connection failed for host: " + node.getFullHost(), ex);
                return NodeStatus.OFFLINE;
            });
    }
}
