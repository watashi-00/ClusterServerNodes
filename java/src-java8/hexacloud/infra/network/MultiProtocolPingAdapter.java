package hexacloud.infra.network;

import java.util.concurrent.CompletableFuture;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.model.PingResult;
import hexacloud.core.ports.PingClientPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.ThreadManager;
import hexacloud.core.config.ClusterConfig;

/**
 * Infrastructure adapter implementing PingClientPort to execute health-check pings
 * over multiple protocols (HTTP, WebSocket, TCP, UDP, gRPC) for Java 8.
 */
public class MultiProtocolPingAdapter implements PingClientPort {

    private static final java.util.concurrent.ConcurrentHashMap<String, java.util.regex.Pattern> PATTERN_CACHE = new java.util.concurrent.ConcurrentHashMap<>();

    public MultiProtocolPingAdapter() {
    }

    @Override
    public CompletableFuture<PingResult> fetchPingAsync(String clusterName, ServerNode node) {
        String uriStr = node.getFullHost();

        switch (node.pingProtocol()) {
            case NONE:
                setNode(node);
                return CompletableFuture.completedFuture(new PingResult(node.status(), false));
            case WEBSOCKET:
                return fetchWsPingAsync(clusterName, node, uriStr);
            case TCP:
                return fetchTcpPingAsync(clusterName, node, uriStr);
            case UDP:
                return fetchUdpPingAsync(clusterName, node, uriStr);
            case GRPC:
                return fetchGrpcPingAsync(clusterName, node, uriStr);
            default:
                break;
        }

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

        return fetchHttpPingAsync(clusterName, node, uriStr);
    }

    private CompletableFuture<PingResult> fetchHttpPingAsync(String clusterName, ServerNode node, String uriStr) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            try {
                java.net.URL url = new java.net.URL(uriStr);
                java.net.HttpURLConnection conn = (java.net.HttpURLConnection) url.openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout((int) ClusterConfig.HTTP_CONNECT_TIMEOUT.toMillis());
                conn.setReadTimeout((int) ClusterConfig.HTTP_REQUEST_TIMEOUT.toMillis());
                if (node.pingHeaderName() != null && !node.pingHeaderName().trim().isEmpty() 
                    && node.pingHeaderValue() != null) {
                    conn.setRequestProperty(node.pingHeaderName().trim(), node.pingHeaderValue());
                }
                
                int code = conn.getResponseCode();
                long latency = System.currentTimeMillis() - startTime;
                node.setLatencyMs((int) latency);
                
                if (code >= 200 && code < 300) {
                    java.io.InputStream in = conn.getInputStream();
                    java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
                    byte[] buffer = new byte[4096];
                    int len;
                    while ((len = in.read(buffer)) != -1) {
                        bos.write(buffer, 0, len);
                    }
                    String body = new String(bos.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    
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
            } catch (Exception e) {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "HTTP connection failed for host: " + node.getFullHost(), e);
                return new PingResult(NodeStatus.OFFLINE, false);
            }
        }, ThreadManager.newVirtualThreadPool());
    }

    private CompletableFuture<PingResult> fetchWsPingAsync(String clusterName, ServerNode node, String uriStr) {
        return CompletableFuture.supplyAsync(() -> {
            long startTime = System.currentTimeMillis();
            String hostPart = node.getHostWithoutProtocol();
            if (hostPart.contains("/")) {
                hostPart = hostPart.substring(0, hostPart.indexOf("/"));
            }
            try (java.net.Socket socket = new java.net.Socket()) {
                socket.connect(new java.net.InetSocketAddress(hostPart, node.port()), (int) ClusterConfig.HTTP_CONNECT_TIMEOUT.toMillis());
                socket.setSoTimeout((int) ClusterConfig.HTTP_REQUEST_TIMEOUT.toMillis());
                
                java.io.OutputStream out = socket.getOutputStream();
                String path = node.pingPath() != null ? node.pingPath() : "/";
                if (!path.startsWith("/")) path = "/" + path;
                String handshake = "GET " + path + " HTTP/1.1\r\n"
                        + "Host: " + hostPart + "\r\n"
                        + "Upgrade: websocket\r\n"
                        + "Connection: Upgrade\r\n"
                        + "Sec-WebSocket-Key: dGhlIHNhbXBsZSBub25jZQ==\r\n"
                        + "Sec-WebSocket-Version: 13\r\n\r\n";
                out.write(handshake.getBytes(java.nio.charset.StandardCharsets.US_ASCII));
                out.flush();
                
                java.io.InputStream in = socket.getInputStream();
                java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.US_ASCII));
                String statusLine = reader.readLine();
                long latency = System.currentTimeMillis() - startTime;
                node.setLatencyMs((int) latency);
                
                if (statusLine != null && statusLine.contains("101")) {
                    return new PingResult(NodeStatus.ONLINE, false);
                } else {
                    setNode(node);
                    return new PingResult(NodeStatus.UNSTABLE, false);
                }
            } catch (Exception e) {
                setNode(node);
                DebugUtils.error(clusterName, node.getFullHost(), "WS connection failed for host: " + node.getFullHost(), e);
                return new PingResult(NodeStatus.OFFLINE, false);
            }
        }, ThreadManager.newVirtualThreadPool());
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
                setNode(node, (int) latency, "gRPC");
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