package hexacloud.infra.network;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.ports.PingClientPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.ThreadManager;
import hexacloud.core.config.ClusterConfig;

/**
 * Infrastructure adapter implementing PingClientPort to execute health-check pings
 * over multiple protocols (HTTP, WebSocket, TCP, UDP, gRPC).
 */
public class MultiProtocolPingAdapter implements PingClientPort {

    private final HttpClient client;
    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.regex.Pattern> PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public MultiProtocolPingAdapter() {
        this.client = HttpClient.newBuilder()
            .connectTimeout(ClusterConfig.HTTP_CONNECT_TIMEOUT)
            .version(ClusterConfig.HTTP_VERSION)
            .executor(ThreadManager.newVirtualThreadPool())
            .build();
    }

    @Override
    public CompletableFuture<NodeStatus> fetchPingAsync(String clusterName, ServerNode node) {
        String uriStr = node.getFullHost();

        if (node.pingProtocol() == PingProtocol.NONE) {
            setNode(node);
            return CompletableFuture.completedFuture(node.status());
        }
        if (node.pingProtocol() == PingProtocol.WEBSOCKET) {
            return fetchWsPingAsync(clusterName, node, uriStr);
        }
        if (node.pingProtocol() == PingProtocol.TCP) {
            return fetchTcpPingAsync(clusterName, node, uriStr);
        }
        if (node.pingProtocol() == PingProtocol.UDP) {
            return fetchUdpPingAsync(clusterName, node, uriStr);
        }
        if (node.pingProtocol() == PingProtocol.GRPC) {
            return fetchGrpcPingAsync(clusterName, node, uriStr);
        }

        // Standard HTTP fallback
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

        long startTime = System.currentTimeMillis();
        return client.sendAsync(reqBuilder.build(), HttpResponse.BodyHandlers.ofString())
            .thenApply(res -> {
                long latency = System.currentTimeMillis() - startTime;
                node.setLatencyMs((int) latency);
                
                if (res.statusCode() >= 200 && res.statusCode() < 300) {
                    String body = res.body();
                    String runtime = extractJsonField(body, "language");
                    if (runtime != null) node.setRuntime(runtime);
                    
                    String cpuStr = extractJsonField(body, "cpu");
                    if (cpuStr != null) {
                        try {
                            node.setCpuUsage(Double.parseDouble(cpuStr));
                        } catch (Exception e) {
                            DebugUtils.error(clusterName, node.getFullHost(), "Failed to parse CPU usage value from HTTP ping: " + cpuStr, e);
                        }
                    }
                    
                    String ramStr = extractJsonField(body, "ram");
                    if (ramStr != null) {
                        try {
                            node.setRamUsage(Double.parseDouble(ramStr));
                        } catch (Exception e) {
                            DebugUtils.error(clusterName, node.getFullHost(), "Failed to parse RAM usage value from HTTP ping: " + ramStr, e);
                        }
                    }
                    return NodeStatus.ONLINE;
                } else {
                    setNode(node);
                    return NodeStatus.UNSTABLE;
                }
            })
            .exceptionally(ex -> {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "Ping connection failed for host: " + node.getFullHost(), ex);
                return NodeStatus.OFFLINE;
            });
    }

    private CompletableFuture<NodeStatus> fetchWsPingAsync(String clusterName, ServerNode node, String uriStr) {
        long startTime = System.currentTimeMillis();
        CompletableFuture<NodeStatus> future = new CompletableFuture<>();

        client.newWebSocketBuilder()
            .connectTimeout(ClusterConfig.HTTP_CONNECT_TIMEOUT)
            .buildAsync(URI.create(uriStr), new WebSocket.Listener() {
                private final StringBuilder payload = new StringBuilder();

                @Override
                public void onOpen(WebSocket webSocket) {
                    long latency = System.currentTimeMillis() - startTime;
                    node.setLatencyMs((int) latency);
                    webSocket.sendText("ping", true);
                    webSocket.request(1);
                }

                @Override
                public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                    payload.append(data);
                    if (last) {
                        String body = payload.toString();
                        String runtime = extractJsonField(body, "language");
                        if (runtime != null) node.setRuntime(runtime);

                        String cpuStr = extractJsonField(body, "cpu");
                        if (cpuStr != null) {
                            try {
                                node.setCpuUsage(Double.parseDouble(cpuStr));
                            } catch (Exception e) {
                                DebugUtils.error(clusterName, node.getFullHost(), "Failed to parse CPU usage value from WS ping: " + cpuStr, e);
                            }
                        }

                        String ramStr = extractJsonField(body, "ram");
                        if (ramStr != null) {
                            try {
                                node.setRamUsage(Double.parseDouble(ramStr));
                            } catch (Exception e) {
                                DebugUtils.error(clusterName, node.getFullHost(), "Failed to parse RAM usage value from WS ping: " + ramStr, e);
                            }
                        }
                        future.complete(NodeStatus.ONLINE);
                        webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Goodbye");
                    } else {
                        webSocket.request(1);
                    }
                    return null;
                }

                @Override
                public void onError(WebSocket webSocket, Throwable error) {
                    setNode(node);
                    DebugUtils.error(clusterName, node.getFullHost(), "WS connection failed for host: " + node.getFullHost(), error);
                    future.complete(NodeStatus.OFFLINE);
                }
            });

        return future.orTimeout(ClusterConfig.HTTP_REQUEST_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                setNode(node);
                return NodeStatus.OFFLINE;
            });
    }

    private CompletableFuture<NodeStatus> fetchTcpPingAsync(String clusterName, ServerNode node, String uriStr) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String hostPart = node.host().replace("tcp://", "").replace("http://", "");
            if (hostPart.contains("/")) {
                hostPart = hostPart.substring(0, hostPart.indexOf("/"));
            }
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(hostPart, node.port()), (int) ClusterConfig.HTTP_CONNECT_TIMEOUT.toMillis());
                long latency = System.currentTimeMillis() - startTime;
                setNode(node, (int) latency, "TCP");
                return NodeStatus.ONLINE;
            } catch (Exception e) {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "TCP connection failed for host: " + node.getFullHost(), e);
                return NodeStatus.OFFLINE;
            }
        }, ThreadManager.newVirtualThreadPool());
    }

    private CompletableFuture<NodeStatus> fetchUdpPingAsync(String clusterName, ServerNode node, String uriStr) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String hostPart = node.host().replace("udp://", "").replace("http://", "");
            if (hostPart.contains("/")) {
                hostPart = hostPart.substring(0, hostPart.indexOf("/"));
            }
            try (java.net.DatagramSocket socket = new java.net.DatagramSocket()) {
                socket.setSoTimeout((int) ClusterConfig.HTTP_CONNECT_TIMEOUT.toMillis());
                java.net.InetAddress address = java.net.InetAddress.getByName(hostPart);
                byte[] buf = new byte[1];
                java.net.DatagramPacket packet = new java.net.DatagramPacket(buf, buf.length, address, node.port());
                socket.send(packet);
                long latency = System.currentTimeMillis() - startTime;
                setNode(node, (int) latency, "UDP");
                return NodeStatus.ONLINE;
            } catch (Exception e) {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "UDP connection failed for host: " + node.getFullHost(), e);
                return NodeStatus.OFFLINE;
            }
        }, ThreadManager.newVirtualThreadPool());
    }

    private CompletableFuture<NodeStatus> fetchGrpcPingAsync(String clusterName, ServerNode node, String uriStr) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String hostPart = node.host().replace("grpc://", "").replace("http://", "");
            if (hostPart.contains("/")) {
                hostPart = hostPart.substring(0, hostPart.indexOf("/"));
            }
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(hostPart, node.port()), (int) ClusterConfig.HTTP_CONNECT_TIMEOUT.toMillis());
                long latency = System.currentTimeMillis() - startTime;
                setNode(node, (int) latency,"gRPC");
                return NodeStatus.ONLINE;
            } catch (Exception e) {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "gRPC connection failed for host: " + node.getFullHost(), e);
                return NodeStatus.OFFLINE;
            }
        }, ThreadManager.newVirtualThreadPool());
    }

    private String extractJsonField(String json, String field) {
        if (json == null) return null;
        java.util.regex.Pattern pattern = PATTERN_CACHE.computeIfAbsent(field, f -> 
            java.util.regex.Pattern.compile("\"" + f + "\"\\s*:\\s*(?:\"([^\"]*)\"|([\\d.]+)|(true|false|null))", java.util.regex.Pattern.CASE_INSENSITIVE)
        );
        java.util.regex.Matcher matcher = pattern.matcher(json);
        if (matcher.find()) {
            if (matcher.group(1) != null) return matcher.group(1);
            if (matcher.group(2) != null) return matcher.group(2);
            if (matcher.group(3) != null) return matcher.group(3);
        }
        return null;
    }

    private void setNode(ServerNode node) {
        setNode(node, 0, 0.0, 0.0);
    }

    private void setNode(ServerNode node, int latency, String runtime) {
        setNode(node, latency, 0.0, 0.0, runtime);
    }

    private void setNode(ServerNode node, int latency, double cpuUsage, double ramUsage) {
        node.setLatencyMs(latency);
        node.setCpuUsage(cpuUsage);
        node.setRamUsage(ramUsage);
    }

    private void setNode(ServerNode node, int latency, double cpuUsage, double ramUsage, String runtime) {
        setNode(node, latency, cpuUsage, ramUsage);
        node.setRuntime(runtime);
    }
}
