package hexacloud.infra.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.function.BiConsumer;
import java.util.List;
import java.util.ArrayList;

import hexacloud.core.server.filter.HttpFilter;
import hexacloud.core.server.filter.Order;
import hexacloud.core.server.filter.HttpRequest;
import hexacloud.core.server.filter.HttpResponse;
import hexacloud.core.server.filter.builtin.IpRestrictionFilter;
import hexacloud.core.server.filter.builtin.RateLimitFilter;
import hexacloud.core.server.filter.builtin.TokenAuthFilter;
import hexacloud.infra.server.filter.HttpRequestImpl;
import hexacloud.infra.server.filter.HttpResponseImpl;
import hexacloud.core.server.filter.HttpFilterChainImpl;

import hexacloud.core.server.ServerTransport;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.concurrent.ThreadManager;

/**
 * Concrete HTTP implementation of ServerTransport bound to a local port
 * and using traditional platform threads for routing and rate-limiting incoming traffic in Java 8.
 */
public class HttpTransport implements ServerTransport {

    private HttpServer server;
    private boolean running = false;

    @Override
    public void listen(int port, RouteRegistry registry, hexacloud.core.cluster.Cluster cluster, List<HttpFilter> customFilters) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
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

                        // 2. Build complete filters list (Built-in + Custom user filters)
                        List<HttpFilter> activeFilters = new ArrayList<>();
                        activeFilters.add(new IpRestrictionFilter(cluster));
                        activeFilters.add(new RateLimitFilter(cluster));
                        activeFilters.add(new TokenAuthFilter(cluster));
                        activeFilters.addAll(customFilters);

                        // Sort custom filters by @Order annotation value (if present)
                        activeFilters.sort((f1, f2) -> {
                            int o1 = f1.getClass().isAnnotationPresent(Order.class) ? f1.getClass().getAnnotation(Order.class).value() : 100;
                            int o2 = f2.getClass().isAnnotationPresent(Order.class) ? f2.getClass().getAnnotation(Order.class).value() : 100;
                            return Integer.compare(o1, o2);
                        });

                        // 3. Final Route execution handler
                        BiConsumer<HttpRequest, HttpResponse> routeHandler = (r, s) -> {
                            try {
                                String rawPath = r.getPath();
                                hexacloud.core.cluster.Cluster targetCluster = cluster;
                                RouteRegistry targetRegistry = registry;
                                String routeName = "";

                                if (rawPath.startsWith("/clusters/")) {
                                    String[] parts = rawPath.split("/");
                                    if (parts.length >= 4) {
                                        String targetClusterName = parts[2];
                                        routeName = parts[3].toUpperCase();
                                        targetCluster = hexacloud.core.cluster.ClusterRegistry.getInstance().getCluster(targetClusterName);
                                        if (targetCluster != null) {
                                            targetRegistry = targetCluster.getRouteRegistry();
                                        }
                                    }
                                } else {
                                    routeName = rawPath.substring(1).toUpperCase();
                                    if (routeName.isEmpty()) {
                                        routeName = "GET_NODES";
                                    }
                                }

                                java.util.function.BiConsumer<String, java.io.PrintWriter> handler = targetRegistry.getRoutes().get(routeName);
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
                                } else {
                                    s.setStatus(404);
                                    try (PrintWriter out = s.getWriter()) {
                                        out.print("404 Not Found - Unknown Route: " + routeName);
                                    }
                                }
                            } catch (Exception e) {
                                throw new RuntimeException(e);
                            }
                        };

                        // 4. Run chain
                        HttpFilterChainImpl chain = new HttpFilterChainImpl(activeFilters, routeHandler);
                        chain.doFilter(req, res);

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
}
