package hexacloud.core.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetSocketAddress;

import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.utils.DebugUtils;

public class HttpTransport implements ServerTransport {

    private HttpServer server;
    private boolean running = false;

    @Override
    public void listen(int port, RouteRegistry registry) {
        try {
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/", new HttpHandler() {
                @Override
                public void handle(HttpExchange exchange) throws IOException {
                    String path = exchange.getRequestURI().getPath().substring(1).toUpperCase();
                    if(path.isEmpty()) {
                        path = "GET_NODES";
                    }
                    
                    var handler = registry.getRoutes().get(path);
                    if(handler != null) {
                        exchange.getResponseHeaders().set("Content-Type", "text/plain");
                        exchange.sendResponseHeaders(200, 0); // chunked transfer
                        
                        try(OutputStream os = exchange.getResponseBody();
                             PrintWriter out = new PrintWriter(os, true)) {
                            String query = exchange.getRequestURI().getQuery();
                            String args = query != null ? query : "";
                            handler.accept(args, out);
                        }
                    } else {
                        String response = "404 Not Found - Unknown Route: " + path;
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
                DebugUtils.log("HTTP Transport successfully bound and listening on port " + port);
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
