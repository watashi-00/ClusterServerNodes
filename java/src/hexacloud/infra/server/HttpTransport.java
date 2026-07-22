package hexacloud.infra.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.server.ServerTransport;
import hexacloud.core.server.filter.HttpFilter;
import hexacloud.core.server.filter.HttpFilterChainImpl;
import hexacloud.core.server.filter.HttpRequest;
import hexacloud.core.server.filter.HttpResponse;
import hexacloud.core.server.filter.Order;
import hexacloud.core.server.filter.builtin.IpRestrictionFilter;
import hexacloud.core.server.filter.builtin.RateLimitFilter;
import hexacloud.core.server.filter.builtin.TokenAuthFilter;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.concurrent.ThreadManager;
import hexacloud.infra.server.filter.HttpRequestImpl;
import hexacloud.infra.server.filter.HttpResponseImpl;

/**
 * Concrete HTTP implementation of ServerTransport bound to a local port
 * and using virtual threads for routing and rate-limiting incoming traffic.
 * Supports Layer 7 Reverse-Proxy load balancing and passive telemetry extraction.
 */
public class HttpTransport implements ServerTransport {

    private HttpServer server;
    private boolean running = false;
    private final ConcurrentHashMap<String, AtomicInteger> roundRobinIndices = new ConcurrentHashMap<>();
    private static final ThreadLocal<byte[]> COPY_BUFFER = ThreadLocal.withInitial(() -> new byte[8192]);
    private final ConcurrentHashMap<String, RouteHandlerInfo> routeCache = new ConcurrentHashMap<>();
    private final java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
            .version(java.net.http.HttpClient.Version.HTTP_2)
            .connectTimeout(java.time.Duration.ofMillis(5000))
            .build();

    private hexacloud.core.server.PerformanceProfile performanceProfile = hexacloud.core.server.PerformanceProfile.STANDARD;
    private final List<HttpFilter> activeFilters = new CopyOnWriteArrayList<>();

    private void rebuildFilters(Cluster cluster, List<HttpFilter> customFilters) {
        activeFilters.clear();
        String allowedIps = cluster.getAllowedIps();
        if (allowedIps != null && !allowedIps.trim().isEmpty()) {
            activeFilters.add(new IpRestrictionFilter(cluster));
        }
        if (cluster.getRateLimitRequests() > 0 && cluster.getRateLimitDurationSeconds() > 0) {
            activeFilters.add(new RateLimitFilter(cluster));
        }
        if (cluster.isRequireToken()) {
            activeFilters.add(new TokenAuthFilter(cluster));
        }
        activeFilters.addAll(customFilters);

        // Sort custom filters by @Order annotation value (if present)
        activeFilters.sort((f1, f2) -> {
            int o1 = f1.getClass().isAnnotationPresent(Order.class) ? f1.getClass().getAnnotation(Order.class).value() : 100;
            int o2 = f2.getClass().isAnnotationPresent(Order.class) ? f2.getClass().getAnnotation(Order.class).value() : 100;
            return Integer.compare(o1, o2);
        });
    }

    @Override
    public void setPerformanceProfile(hexacloud.core.server.PerformanceProfile profile) {
        if (profile != null) {
            this.performanceProfile = profile;
        }
    }

    @Override
    public void listen(int port, RouteRegistry registry, Cluster cluster, List<HttpFilter> customFilters) {
        try {
            rebuildFilters(cluster, customFilters);
            DebugUtils.log("HTTP Transport (JDK) starting on port " + port + " with profile: " + performanceProfile);
            server = HttpServer.create(new InetSocketAddress(port), 2048);
            server.setExecutor(ThreadManager.newVirtualThreadPool());
            server.createContext("/", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    // CORS Configuration
                    exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, POST, OPTIONS, PUT, DELETE");
                    exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "X-Cluster-Token, Content-Type, Authorization");

                    if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(204, -1);
                        return;
                    }

                    try {
                        // 1. Instantiate Wrappers
                        HttpRequestImpl req = new HttpRequestImpl(exchange);
                        HttpResponseImpl res = new HttpResponseImpl(exchange);

                        // 3. Final Route execution handler
                        BiConsumer<HttpRequest, HttpResponse> routeHandler = (r, s) -> {
                            try {
                                String rawPath = r.getPath();

                                if (rawPath.startsWith("/clusters/")) {
                                    String pathWithoutClusters = rawPath.substring("/clusters/".length());
                                    int slashIdx = pathWithoutClusters.indexOf('/');
                                    String targetClusterName;
                                    String clusterSubpath;

                                    if (slashIdx != -1) {
                                        targetClusterName = pathWithoutClusters.substring(0, slashIdx);
                                        clusterSubpath = pathWithoutClusters.substring(slashIdx);
                                    } else {
                                        targetClusterName = pathWithoutClusters;
                                        clusterSubpath = "/";
                                    }

                                    Cluster targetCluster = ClusterRegistry.getInstance().getCluster(targetClusterName);
                                    if (targetCluster == null) {
                                        s.setStatus(404);
                                        try (PrintWriter out = s.getWriter()) {
                                            out.print("404 Not Found - Unknown Cluster: " + targetClusterName);
                                        }
                                        return;
                                    }

                                    RouteRegistry targetRegistry = targetCluster.getRouteRegistry();
                                    String routeName = clusterSubpath.length() > 1 ? clusterSubpath.substring(1).toUpperCase() : "";

                                    // Check built-in cluster management routes
                                    BiConsumer<String, PrintWriter> handler = targetRegistry.getRoutes().get(routeName);
                                    if (handler != null) {
                                        if (routeName.equals("GET_NODES_JSON")) {
                                            s.setContentType("application/json");
                                        } else {
                                            s.setContentType("text/plain");
                                        }
                                        if (!s.isCommitted()) {
                                            s.setStatus(200);
                                        }
                                        try (PrintWriter out = s.getWriter()) {
                                            String query = r.getQuery();
                                            String args = query != null ? query : "";
                                            handler.accept(args, out);
                                        }
                                        return;
                                    }

                                    // Layer 7 Reverse Proxy Load Balancing
                                    if (targetCluster.getRoutingMode() == Cluster.RoutingMode.TELEMETRY_ONLY) {
                                        s.setStatus(403);
                                        try (PrintWriter out = s.getWriter()) {
                                            out.print("403 Forbidden - Load balancing is disabled for cluster: " + targetClusterName);
                                        }
                                        return;
                                    }

                                    List<ServerNode> activeNodes = targetCluster.getCluster().stream()
                                            .filter(n -> n != null && n.status() == NodeStatus.ONLINE)
                                            .collect(Collectors.toList());

                                    if (activeNodes.isEmpty()) {
                                        s.setStatus(503);
                                        try (PrintWriter out = s.getWriter()) {
                                            out.print("503 Service Unavailable - No active nodes in cluster: " + targetClusterName);
                                        }
                                        return;
                                    }

                                    // Thread-safe Round-Robin selection
                                    AtomicInteger rrIdx = roundRobinIndices.computeIfAbsent(targetClusterName, k -> new AtomicInteger(0));
                                    int selectedIndex = (rrIdx.getAndIncrement() & Integer.MAX_VALUE) % activeNodes.size();
                                    ServerNode targetNode = activeNodes.get(selectedIndex);

                                    // Forward HTTP request to backend node
                                    String targetUrlStr = targetNode.getFullHost() + clusterSubpath;
                                    String query = r.getQuery();
                                    if (query != null && !query.isEmpty()) {
                                        targetUrlStr += "?" + query;
                                    }

                                    long startTime = System.currentTimeMillis();
                                    java.net.http.HttpRequest.Builder reqBuilder = java.net.http.HttpRequest.newBuilder()
                                            .uri(java.net.URI.create(targetUrlStr));

                                    int timeout = targetCluster.getTimeoutMs() > 0 ? targetCluster.getTimeoutMs() : 5000;
                                    reqBuilder.timeout(java.time.Duration.ofMillis(timeout));

                                    // Copy request headers
                                    Map<String, List<String>> reqHeaders = r.getHeaders();
                                    if (reqHeaders != null) {
                                        for (Map.Entry<String, List<String>> entry : reqHeaders.entrySet()) {
                                            String hName = entry.getKey();
                                            if (hName == null || hName.equalsIgnoreCase("Host") || hName.equalsIgnoreCase("Content-Length") || hName.equalsIgnoreCase("Connection") || hName.equalsIgnoreCase("Upgrade")) {
                                                continue;
                                            }
                                            for (String val : entry.getValue()) {
                                                reqBuilder.header(hName, val);
                                            }
                                        }
                                    }

                                    // Forward request body if present
                                    String method = r.getMethod();
                                    java.net.http.HttpRequest.BodyPublisher bodyPublisher;
                                    boolean hasBody = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
                                    if (hasBody) {
                                        bodyPublisher = java.net.http.HttpRequest.BodyPublishers.ofInputStream(() -> exchange.getRequestBody());
                                    } else {
                                        bodyPublisher = java.net.http.HttpRequest.BodyPublishers.noBody();
                                    }
                                    reqBuilder.method(method, bodyPublisher);

                                    java.net.http.HttpRequest proxyRequest = reqBuilder.build();

                                    int respCode = 502;
                                    java.net.http.HttpResponse<InputStream> proxyResponse = null;
                                    try {
                                        proxyResponse = httpClient.send(proxyRequest, java.net.http.HttpResponse.BodyHandlers.ofInputStream());
                                        respCode = proxyResponse.statusCode();
                                    } catch (Exception ex) {
                                        respCode = 502;
                                    }

                                    long latencyMs = System.currentTimeMillis() - startTime;

                                    // Passive Telemetry extraction
                                    Double cpuVal = null;
                                    Double ramVal = null;
                                    if (proxyResponse != null) {
                                        cpuVal = parseHeaderDouble(proxyResponse.headers(), "X-Telemetry-CPU", "X-Node-CPU");
                                        ramVal = parseHeaderDouble(proxyResponse.headers(), "X-Telemetry-RAM", "X-Node-RAM");
                                    }

                                    targetCluster.updateTelemetryServer(targetNode.host(), targetNode.port(), cpuVal, ramVal, null, (int) latencyMs, null);
                                    if (cpuVal != null) targetNode.setCpuUsage(cpuVal);
                                    if (ramVal != null) targetNode.setRamUsage(ramVal);
                                    targetNode.setLatencyMs((int) latencyMs);

                                    // Copy response headers to client response
                                    if (proxyResponse != null) {
                                        for (Map.Entry<String, List<String>> entry : proxyResponse.headers().map().entrySet()) {
                                            String hName = entry.getKey();
                                            if (hName == null || hName.equalsIgnoreCase("Transfer-Encoding") || hName.equalsIgnoreCase("Content-Length") || hName.equalsIgnoreCase("Connection")) {
                                                continue;
                                            }
                                            for (String val : entry.getValue()) {
                                                exchange.getResponseHeaders().add(hName, val);
                                            }
                                        }
                                    }

                                    // Send response status and body
                                    if (proxyResponse != null) {
                                        long contentLength = proxyResponse.headers().firstValueAsLong("Content-Length").orElse(-1L);
                                        if (respCode == 204 || respCode == 304 || contentLength == 0) {
                                            exchange.sendResponseHeaders(respCode, -1);
                                        } else {
                                            // Chunked streaming for body
                                            exchange.sendResponseHeaders(respCode, 0);
                                            try (InputStream in = proxyResponse.body();
                                                 OutputStream os = exchange.getResponseBody()) {
                                                byte[] buf = COPY_BUFFER.get();
                                                int len;
                                                while ((len = in.read(buf)) != -1) {
                                                    os.write(buf, 0, len);
                                                }
                                                os.flush();
                                            }
                                        }
                                    } else {
                                        if (respCode == 502) {
                                            byte[] respBytes = "502 Bad Gateway - Connection failed".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                            exchange.getResponseHeaders().set("Content-Type", "text/plain");
                                            exchange.sendResponseHeaders(502, respBytes.length);
                                            try (OutputStream os = exchange.getResponseBody()) {
                                                os.write(respBytes);
                                                os.flush();
                                            }
                                        } else {
                                            exchange.sendResponseHeaders(respCode, -1);
                                        }
                                    }

                                } else {
                                    RouteHandlerInfo routeInfo = routeCache.computeIfAbsent(rawPath, path -> {
                                        String routeName = path.length() > 1 ? path.substring(1).toUpperCase() : "GET_NODES";
                                        BiConsumer<String, PrintWriter> handler = registry.getRoutes().get(routeName);
                                        return new RouteHandlerInfo(handler, routeName);
                                    });

                                    if (routeInfo.handler != null) {
                                        if (routeInfo.routeName.equals("GET_NODES_JSON")) {
                                            s.setContentType("application/json");
                                        } else {
                                            s.setContentType("text/plain");
                                        }
                                        if (!s.isCommitted()) {
                                            s.setStatus(200);
                                        }
                                        try (PrintWriter out = s.getWriter()) {
                                            String query = r.getQuery();
                                            String args = query != null ? query : "";
                                            routeInfo.handler.accept(args, out);
                                        }
                                    } else {
                                        s.setStatus(404);
                                        try (PrintWriter out = s.getWriter()) {
                                            out.print("404 Not Found - Unknown Route: " + routeInfo.routeName);
                                        }
                                    }
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        };

                        // 4. Run chain
                        if (!activeFilters.isEmpty()) {
                            HttpFilterChainImpl chain = new HttpFilterChainImpl(activeFilters, routeHandler);
                            chain.doFilter(req, res);
                        } else {
                            routeHandler.accept(req, res);
                        }

                    } catch (Exception e) {
                        DebugUtils.error("HttpTransport: Exception caught in filter chain pipeline: " + e.getMessage(), e);
                        if (!exchange.getResponseHeaders().containsKey("Content-Type")) {
                            exchange.getResponseHeaders().set("Content-Type", "text/plain");
                        }
                        try {
                            exchange.sendResponseHeaders(500, 0);
                            try (OutputStream os = exchange.getResponseBody();
                                 PrintWriter out = new PrintWriter(os, true)) {
                                out.println("500 Internal Server Error - Execution failure: " + e.getMessage());
                            }
                        } catch (Exception ignored) {}
                    }
                }
            });
            
            new Thread(() -> {
                server.start();
                running = true;
                DebugUtils.info("HTTP Transport successfully bound and listening on port " + port);
            }, "HttpServer-Listener-" + port).start();
            
        } catch(IOException e) {
            DebugUtils.error("HTTP Transport failed to start on port " + port, e);
        }
    }

    private Double parseHeaderDouble(java.net.http.HttpHeaders headers, String... headerNames) {
        for (String hName : headerNames) {
            java.util.Optional<String> valOpt = headers.firstValue(hName);
            if (valOpt.isPresent()) {
                String val = valOpt.get();
                if (!val.trim().isEmpty()) {
                    try {
                        return Double.parseDouble(val.replace("%", "").trim());
                    } catch (NumberFormatException ignored) {}
                }
            }
        }
        return null;
    }

    @Override
    public void stop() {
        if(server != null) {
            server.stop(0);
            running = false;
            DebugUtils.log("HTTP Transport stopped.");
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private static class RouteHandlerInfo {
        final BiConsumer<String, PrintWriter> handler;
        final String routeName;

        RouteHandlerInfo(BiConsumer<String, PrintWriter> handler, String routeName) {
            this.handler = handler;
            this.routeName = routeName;
        }
    }
}
