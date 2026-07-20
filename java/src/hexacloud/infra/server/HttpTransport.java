package hexacloud.infra.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.util.function.BiConsumer;

import hexacloud.core.server.ServerTransport;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.concurrent.ThreadManager;
import hexacloud.core.utils.network.HttpHeader;
import hexacloud.core.utils.network.ContentType;

/**
 * Concrete HTTP implementation of ServerTransport bound to a local port
 * and using virtual threads for routing and rate-limiting incoming traffic.
 */
public class HttpTransport implements ServerTransport {

    private HttpServer server;
    private boolean running = false;

    @Override
    public void listen(int port, RouteRegistry registry, hexacloud.core.cluster.Cluster cluster) {
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

                    // Handle preflight OPTIONS requests
                    if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
                        exchange.sendResponseHeaders(204, -1);
                        return;
                    }

                    String clientIp = exchange.getRemoteAddress().getAddress().getHostAddress();
                    String rawPath = exchange.getRequestURI().getPath();
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

                    if (targetCluster == null) {
                        String response = "404 Not Found - Cluster Not Found";
                        exchange.getResponseHeaders().set(HttpHeader.CONNECTION.value(), "close");
                        exchange.sendResponseHeaders(404, response.length());
                        try(OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                        return;
                    }

                    if(!targetCluster.isIpAllowed(clientIp)) {
                        String response = "403 Forbidden - IP Not Allowed";
                        exchange.getResponseHeaders().set(HttpHeader.CONNECTION.value(), "close");
                        exchange.sendResponseHeaders(403, response.length());
                        try(OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                        return;
                    }

                    if(!targetCluster.checkRateLimit(clientIp)) {
                        String response = "429 Too Many Requests";
                        exchange.getResponseHeaders().set(HttpHeader.CONNECTION.value(), "close");
                        exchange.getResponseHeaders().set(HttpHeader.RETRY_AFTER.value(), "10");
                        exchange.sendResponseHeaders(429, response.length());
                        try(OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                        return;
                    }

                    String token = exchange.getRequestHeaders().getFirst(HttpHeader.X_CLUSTER_TOKEN.value());
                    if(token == null || token.isEmpty()) {
                        String query = exchange.getRequestURI().getQuery();
                        if(query != null && query.contains("token=")) {
                            for(String param : query.split("&")) {
                                if(param.startsWith("token=")) {
                                    token = param.substring(6);
                                    break;
                                }
                            }
                        }
                    }

                    if(!targetCluster.authenticate(token)) {
                        String response = "401 Unauthorized - Invalid or Missing API Token";
                        exchange.getResponseHeaders().set(HttpHeader.CONNECTION.value(), "close");
                        exchange.sendResponseHeaders(401, response.length());
                        try(OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
                        return;
                    }

                    BiConsumer<String, PrintWriter> handler = targetRegistry.getRoutes().get(routeName);
                    if(handler != null) {
                        if (routeName.equals("GET_NODES_JSON")) {
                            exchange.getResponseHeaders().set(HttpHeader.CONTENT_TYPE.value(), ContentType.APPLICATION_JSON.value());
                        } else {
                            exchange.getResponseHeaders().set(HttpHeader.CONTENT_TYPE.value(), ContentType.TEXT_PLAIN.value());
                        }
                        exchange.sendResponseHeaders(200, 0); // chunked transfer
                        
                        try(OutputStream os = exchange.getResponseBody();
                             PrintWriter out = new PrintWriter(os, true)) {
                            String query = exchange.getRequestURI().getQuery();
                            String args = query != null ? query : "";
                            handler.accept(args, out);
                        }
                    } else {
                        String response = "404 Not Found - Unknown Route: " + routeName;
                        exchange.sendResponseHeaders(404, response.length());
                        try(OutputStream os = exchange.getResponseBody()) {
                            os.write(response.getBytes());
                        }
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
