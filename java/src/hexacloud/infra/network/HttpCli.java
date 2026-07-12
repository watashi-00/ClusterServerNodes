package hexacloud.infra.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.concurrent.CompletableFuture;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.config.ClusterConfig;

class HttpCli {

    public HttpCli() {}

    CompletableFuture<NodeStatus> fetchPingAsync(String host) {
        HttpClient client = HttpClient.newBuilder()
            .connectTimeout(ClusterConfig.HTTP_CONNECT_TIMEOUT)
            .version(ClusterConfig.HTTP_VERSION)
            .build();

        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create(host))
            .timeout(ClusterConfig.HTTP_REQUEST_TIMEOUT)
            .GET()
            .build();

        return client.sendAsync(req, HttpResponse.BodyHandlers.ofString())
            .thenApply(res -> {
                if(res.statusCode() >= 200 && res.statusCode() < 300) {
                    return NodeStatus.ONLINE;
                } else {
                    return NodeStatus.UNSTABLE;
                }
            })
            .exceptionally(ex -> {
                DebugUtils.error("Ping connection failed for host: " + host, ex);
                return NodeStatus.OFFLINE;
            });
    }
}
