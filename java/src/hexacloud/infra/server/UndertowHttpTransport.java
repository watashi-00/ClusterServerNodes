package hexacloud.infra.server;

import io.undertow.Undertow;
import io.undertow.UndertowOptions;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;
import io.undertow.util.Headers;
import io.undertow.util.HttpString;
import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.ServerNode;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.server.ServerTransport;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.server.filter.HttpFilter;
import hexacloud.core.server.filter.HttpRequest;
import hexacloud.core.server.filter.HttpResponse;
import hexacloud.core.server.filter.Order;
import hexacloud.core.server.filter.builtin.IpRestrictionFilter;
import hexacloud.core.server.filter.builtin.RateLimitFilter;
import hexacloud.core.server.filter.builtin.TokenAuthFilter;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.concurrent.ThreadManager;
import hexacloud.core.server.filter.HttpFilterChainImpl;

import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.stream.Collectors;

public class UndertowHttpTransport implements ServerTransport {

    private Undertow server;
    private boolean running = false;
    private final ConcurrentHashMap<String, AtomicInteger> roundRobinIndices = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, RouteHandlerInfo> routeCache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, HttpString> headerCache = new ConcurrentHashMap<>(128);
    private static final java.util.concurrent.ConcurrentLinkedQueue<byte[]> BUFFER_POOL = new java.util.concurrent.ConcurrentLinkedQueue<>();
    private static final HttpString CORS_ALLOW_ORIGIN = HttpString.tryFromString("Access-Control-Allow-Origin");
    private static final HttpString CORS_ALLOW_METHODS = HttpString.tryFromString("Access-Control-Allow-Methods");
    private static final HttpString CORS_ALLOW_HEADERS = HttpString.tryFromString("Access-Control-Allow-Headers");
    private final ExecutorService virtualExecutor = ThreadManager.newVirtualThreadPool();
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
            Undertow.Builder builder = Undertow.builder()
                    .addHttpListener(port, "0.0.0.0");

            if (performanceProfile == hexacloud.core.server.PerformanceProfile.MAX_PERFORMANCE) {
                // Maximized performance profile for container resource utilization
                builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true)
                        .setServerOption(UndertowOptions.BUFFER_PIPELINED_DATA, true)
                        .setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, false)
                        .setServerOption(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false)
                        .setIoThreads(Runtime.getRuntime().availableProcessors() * 2)
                        .setWorkerThreads(Runtime.getRuntime().availableProcessors() * 8)
                        .setBufferSize(65536)
                        .setDirectBuffers(true);
            } else {
                // Standard lightweight profile for normal operations
                builder.setServerOption(UndertowOptions.ALWAYS_SET_KEEP_ALIVE, true)
                        .setServerOption(UndertowOptions.BUFFER_PIPELINED_DATA, true)
                        .setServerOption(UndertowOptions.RECORD_REQUEST_START_TIME, false)
                        .setServerOption(UndertowOptions.ENABLE_CONNECTOR_STATISTICS, false)
                        .setIoThreads(Runtime.getRuntime().availableProcessors())
                        .setWorkerThreads(Runtime.getRuntime().availableProcessors())
                        .setBufferSize(16384)
                        .setDirectBuffers(false);
            }

            server = builder.setHandler(new HttpHandler() {
                        @Override
                        public void handleRequest(HttpServerExchange exchange) throws Exception {
                            String path = exchange.getRequestPath();
                            if (path.startsWith("/clusters/")) {
                                // Proxy route: dispatch to Virtual Threads
                                if (exchange.isInIoThread()) {
                                    exchange.dispatch(virtualExecutor, () -> {
                                        try {
                                            processRequest(exchange, registry, cluster, customFilters);
                                        } catch (Exception e) {
                                            handleError(exchange, e);
                                        }
                                    });
                                    return;
                                }
                            } else {
                                // Direct route: run inline on I/O threads in MAX_PERFORMANCE mode to bypass dispatching overhead,
                                // or dispatch to standard Undertow worker platform threads in STANDARD mode.
                                if (performanceProfile != hexacloud.core.server.PerformanceProfile.MAX_PERFORMANCE) {
                                    if (exchange.isInIoThread()) {
                                        exchange.dispatch(exchange.getConnection().getWorker(), () -> {
                                            try {
                                                processRequest(exchange, registry, cluster, customFilters);
                                            } catch (Exception e) {
                                                handleError(exchange, e);
                                            }
                                        });
                                        return;
                                    }
                                }
                            }
                            processRequest(exchange, registry, cluster, customFilters);
                        }
                    })
                    .build();

            server.start();
            running = true;
            DebugUtils.info("HTTP Transport (Undertow) successfully bound and listening on port " + port);
        } catch (Exception e) {
            DebugUtils.error("HTTP Transport (Undertow) failed to start on port " + port, e);
        }
    }

    private void processRequest(HttpServerExchange exchange, RouteRegistry registry, Cluster cluster, List<HttpFilter> customFilters) {
        // Set CORS headers
        exchange.getResponseHeaders().put(CORS_ALLOW_ORIGIN, "*");
        exchange.getResponseHeaders().put(CORS_ALLOW_METHODS, "GET, POST, OPTIONS, PUT, DELETE");
        exchange.getResponseHeaders().put(CORS_ALLOW_HEADERS, "X-Cluster-Token, Content-Type, Authorization");

        if (io.undertow.util.Methods.OPTIONS.equals(exchange.getRequestMethod())) {
            exchange.setStatusCode(204);
            return;
        }

        try {
            // 1. Wrap request and response
            UndertowHttpRequestImpl req = new UndertowHttpRequestImpl(exchange);
            UndertowHttpResponseImpl res = new UndertowHttpResponseImpl(exchange);

            // 2. Build active filter chain
            // Filters are pre-compiled in this.activeFilters list

            // 3. Define Route execution handler
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

                        // Round-Robin selection
                        AtomicInteger rrIdx = roundRobinIndices.computeIfAbsent(targetClusterName, k -> new AtomicInteger(0));
                        int selectedIndex = (rrIdx.getAndIncrement() & Integer.MAX_VALUE) % activeNodes.size();
                        ServerNode targetNode = activeNodes.get(selectedIndex);

                        // Forward request
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

                        // Forward body if present
                        String method = r.getMethod();
                        java.net.http.HttpRequest.BodyPublisher bodyPublisher;
                        boolean hasBody = "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method) || "PATCH".equalsIgnoreCase(method);
                        if (hasBody) {
                            if (!exchange.isBlocking()) {
                                exchange.startBlocking();
                            }
                            bodyPublisher = java.net.http.HttpRequest.BodyPublishers.ofInputStream(() -> {
                                try {
                                    return exchange.getInputStream();
                                } catch (Exception e) {
                                    throw new RuntimeException(e);
                                }
                            });
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

                        // Copy response headers to client
                        if (proxyResponse != null) {
                            for (Map.Entry<String, List<String>> entry : proxyResponse.headers().map().entrySet()) {
                                String hName = entry.getKey();
                                if (hName == null || hName.equalsIgnoreCase("Transfer-Encoding") || hName.equalsIgnoreCase("Content-Length") || hName.equalsIgnoreCase("Connection")) {
                                    continue;
                                }
                                HttpString cachedHeader = headerCache.computeIfAbsent(hName, HttpString::tryFromString);
                                for (String val : entry.getValue()) {
                                    exchange.getResponseHeaders().add(cachedHeader, val);
                                }
                            }
                        }

                        // Send response
                        exchange.setStatusCode(respCode);
                        if (proxyResponse != null) {
                            if (!exchange.isBlocking()) {
                                exchange.startBlocking();
                            }
                            byte[] buf = BUFFER_POOL.poll();
                            if (buf == null) {
                                buf = new byte[8192];
                            }
                            try (InputStream in = proxyResponse.body();
                                 OutputStream os = exchange.getOutputStream()) {
                                int len;
                                while ((len = in.read(buf)) != -1) {
                                    os.write(buf, 0, len);
                                }
                                os.flush();
                            } finally {
                                BUFFER_POOL.offer(buf);
                            }
                        } else {
                            if (respCode == 502) {
                                byte[] respBytes = "502 Bad Gateway - Connection failed".getBytes(java.nio.charset.StandardCharsets.UTF_8);
                                exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
                                if (!exchange.isBlocking()) {
                                    exchange.startBlocking();
                                }
                                try (OutputStream os = exchange.getOutputStream()) {
                                    os.write(respBytes);
                                    os.flush();
                                }
                            }
                        }

                    } else {
                        // Direct Custom Routes
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
            res.flushBuffer();

        } catch (Exception e) {
            handleError(exchange, e);
        }
    }

    private void handleError(HttpServerExchange exchange, Exception e) {
        DebugUtils.error("UndertowHttpTransport: Exception caught in pipeline: " + e.getMessage(), e);
        if (!exchange.getResponseHeaders().contains(Headers.CONTENT_TYPE)) {
            exchange.getResponseHeaders().put(Headers.CONTENT_TYPE, "text/plain");
        }
        exchange.setStatusCode(500);
        exchange.getResponseSender().send("500 Internal Server Error - Execution failure: " + e.getMessage());
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
        if (server != null) {
            server.stop();
            running = false;
            virtualExecutor.shutdown();
            DebugUtils.log("HTTP Transport (Undertow) stopped.");
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
