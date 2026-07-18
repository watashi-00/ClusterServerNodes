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
import hexacloud.core.model.PingResult;
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
            .version(ClusterConfig.HTTP_VERSION.resolveHttpVersion(ClusterConfig.HTTP_VERSION))
            .executor(ThreadManager.newVirtualThreadPool())
            .build();
    }

    @Override
    public CompletableFuture<PingResult> fetchPingAsync(String clusterName, ServerNode node) {
        String uriStr = node.getFullHost();

        switch (node.pingProtocol()) {
            case NONE: {
                setNode(node);
                return CompletableFuture.completedFuture(new PingResult(node.status(), false));
            }
            case WEBSOCKET: {return fetchWsPingAsync(clusterName, node, uriStr);}
            case TCP: {return fetchTcpPingAsync(clusterName, node, uriStr);}
            case UDP: {return fetchUdpPingAsync(clusterName, node, uriStr);}
            case GRPC: {return fetchGrpcPingAsync(clusterName, node, uriStr);}
            default: {}
        };

        // Standard HTTP fallback
        String path = node.pingPath();
        if (path != null && !path.isEmpty()) {
            try {
                java.net.URI baseUri = java.net.URI.create(uriStr);
                if (!path.startsWith("/")) {
                    path = "/" + path;
                }
                String basePath = baseUri.getPath();
                if (basePath == null) basePath = "";
                if (basePath.endsWith("/")) {
                    basePath = basePath.substring(0, basePath.length() - 1);
                }
                String finalPath = basePath + path;
                java.net.URI finalUri = new java.net.URI(
                    baseUri.getScheme(),
                    baseUri.getUserInfo(),
                    baseUri.getHost(),
                    baseUri.getPort(),
                    finalPath,
                    baseUri.getQuery(),
                    baseUri.getFragment()
                );
                uriStr = finalUri.toString();
            } catch (java.net.URISyntaxException | IllegalArgumentException e) {
                if (!uriStr.endsWith("/") && !path.startsWith("/")) {
                    uriStr = uriStr + "/" + path;
                } else if (uriStr.endsWith("/") && path.startsWith("/")) {
                    uriStr = uriStr + path.substring(1);
                } else {
                    uriStr = uriStr + path;
                }
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
                    boolean hasTelemetry = false;
                    if (runtime != null) {
                        node.setRuntime(runtime);
                        hasTelemetry = true;
                    }
                    
                    String cpuStr = extractJsonField(body, "cpu");
                    if (cpuStr != null) {
                        try {
                            node.setCpuUsage(Double.parseDouble(cpuStr));
                            hasTelemetry = true;
                        } catch (Exception e) {
                            DebugUtils.error(clusterName, node.getFullHost(), "Failed to parse CPU usage value from HTTP ping: " + cpuStr, e);
                        }
                    }
                    
                    String ramStr = extractJsonField(body, "ram");
                    if (ramStr != null) {
                        try {
                            node.setRamUsage(Double.parseDouble(ramStr));
                            hasTelemetry = true;
                        } catch (Exception e) {
                            DebugUtils.error(clusterName, node.getFullHost(), "Failed to parse RAM usage value from HTTP ping: " + ramStr, e);
                        }
                    }
                    return new PingResult(NodeStatus.ONLINE, hasTelemetry);
                } else {
                    setNode(node);
                    return new PingResult(NodeStatus.UNSTABLE, false);
                }
            })
            .exceptionally(ex -> {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "Ping connection failed for host: " + node.getFullHost(), ex);
                return new PingResult(NodeStatus.OFFLINE, false);
            });
    }

    private CompletableFuture<PingResult> fetchWsPingAsync(String clusterName, ServerNode node, String uriStr) {
        long startTime = System.currentTimeMillis();
        CompletableFuture<PingResult> future = new CompletableFuture<>();

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
                        boolean hasTelemetry = false;
                        if (runtime != null) {
                            node.setRuntime(runtime);
                            hasTelemetry = true;
                        }

                        String cpuStr = extractJsonField(body, "cpu");
                        if (cpuStr != null) {
                            try {
                                node.setCpuUsage(Double.parseDouble(cpuStr));
                                hasTelemetry = true;
                            } catch (Exception e) {
                                DebugUtils.error(clusterName, node.getFullHost(), "Failed to parse CPU usage value from WS ping: " + cpuStr, e);
                            }
                        }

                        String ramStr = extractJsonField(body, "ram");
                        if (ramStr != null) {
                            try {
                                node.setRamUsage(Double.parseDouble(ramStr));
                                hasTelemetry = true;
                            } catch (Exception e) {
                                DebugUtils.error(clusterName, node.getFullHost(), "Failed to parse RAM usage value from WS ping: " + ramStr, e);
                            }
                        }
                        future.complete(new PingResult(NodeStatus.ONLINE, hasTelemetry));
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
                    future.complete(new PingResult(NodeStatus.OFFLINE, false));
                }
            });

        return future.orTimeout(ClusterConfig.HTTP_REQUEST_TIMEOUT.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS)
            .exceptionally(ex -> {
                setNode(node);
                return new PingResult(NodeStatus.OFFLINE, false);
            });
    }

    private CompletableFuture<PingResult> fetchTcpPingAsync(String clusterName, ServerNode node, String uriStr) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String hostPart = node.getHostWithoutProtocol();
            if (hostPart.contains("/")) {
                hostPart = hostPart.substring(0, hostPart.indexOf("/"));
            }
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(hostPart, node.port()), (int) ClusterConfig.HTTP_CONNECT_TIMEOUT.toMillis());
                long latency = System.currentTimeMillis() - startTime;
                setNode(node, (int) latency, "TCP");
                return new PingResult(NodeStatus.ONLINE, false);
            } catch (Exception e) {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "TCP connection failed for host: " + node.getFullHost(), e);
                return new PingResult(NodeStatus.OFFLINE, false);
            }
        }, ThreadManager.newVirtualThreadPool());
    }

    private CompletableFuture<PingResult> fetchUdpPingAsync(String clusterName, ServerNode node, String uriStr) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String hostPart = node.getHostWithoutProtocol();
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
                return new PingResult(NodeStatus.ONLINE, false);
            } catch (Exception e) {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "UDP connection failed for host: " + node.getFullHost(), e);
                return new PingResult(NodeStatus.OFFLINE, false);
            }
        }, ThreadManager.newVirtualThreadPool());
    }

    private CompletableFuture<PingResult> fetchGrpcPingAsync(String clusterName, ServerNode node, String uriStr) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String hostPart = node.getHostWithoutProtocol();
            if (hostPart.contains("/")) {
                hostPart = hostPart.substring(0, hostPart.indexOf("/"));
            }
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(hostPart, node.port()), (int) ClusterConfig.HTTP_CONNECT_TIMEOUT.toMillis());
                long latency = System.currentTimeMillis() - startTime;
                setNode(node, (int) latency,"gRPC");
                return new PingResult(NodeStatus.ONLINE, false);
            } catch (Exception e) {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "gRPC connection failed for host: " + node.getFullHost(), e);
                return new PingResult(NodeStatus.OFFLINE, false);
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
