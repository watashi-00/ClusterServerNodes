package hexacloud.core.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.BiConsumer;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.model.ServerNode;
import hexacloud.core.utils.DebugUtils;

class Server {

    private boolean clusterActive = true;
    private final int MAX_WORKERS = 20;
    private final Cluster cluster;
    private final int port; 
    private final HashMap<String, BiConsumer<String, PrintWriter>> routes;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_WORKERS);

    public Server(Cluster cluster) {
        this.port = 8080;
        this.cluster = cluster;
        this.routes = new HashMap<>();
        DebugUtils.log("Server instance initialized on default port 8080");
        map();
    }
    
    public Server(int port, Cluster cluster) {
        this.port = port;
        this.cluster = cluster;
        this.routes = new HashMap<>();
        DebugUtils.log("Server instance initialized on port " + port);
        map();
    }

    private void map() {
        routes.put("GET_NODES", (args, out) -> {
            StringBuilder sb = new StringBuilder();
            for(ServerNode node : this.cluster.getCluster()) {
                sb.append(node.getFullHost()).append("=").append(node.status()).append(";");
            }
            out.println(sb.toString());
        });
    }

    public void listen() {
        new Thread(() -> serverListen(this.port), "ClusterServer-Listener-" + this.port).start();
    }
    public void listen(int port) {
        new Thread(() -> serverListen(port), "ClusterServer-Listener-" + port).start();
    }

    private void serverListen(int port) {
        DebugUtils.log("TCP Server starting to listen on port " + port);
        try(ServerSocket serverSocket = new ServerSocket(port)) {
            DebugUtils.log("TCP Server successfully bound and listening on port " + port);
            while(clusterActive) {
                Socket socket = serverSocket.accept();
                DebugUtils.log("TCP Server accepted new connection from " + socket.getRemoteSocketAddress());
                threadPool.execute(() -> conn(socket));
            }
            
        } catch(IOException ex) {
            DebugUtils.error("TCP Server failed to open port " + port, ex);
        }
    }

    private void conn(Socket socket) {
        try(
            socket;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        ) {
            String line = in.readLine();
            if(line == null || line.trim().isEmpty()) {
                DebugUtils.log("Server received empty connection request from " + socket.getRemoteSocketAddress());
                return;
            }

            DebugUtils.log("Server received raw command line: '" + line + "'");

            String[] tokens = line.split(" ", 2);
            String command = tokens[0].toUpperCase();
            String args = tokens.length > 1 ? tokens[1].trim() : "";

            var handler = routes.get(command);

            if(handler == null) {
                DebugUtils.error("Server: Unknown command '" + command + "' received from client.");
                out.println("Unknown command " + command);
                return;
            }

            DebugUtils.log("Server: Executing route handler for command '" + command + "' with args '" + args + "'");
            handler.accept(args, out);
            DebugUtils.log("Server: Successfully completed request handler for command '" + command + "'");
            
        } catch(IOException ex) {
            DebugUtils.error("Failed to process request from client", ex);
        }
    }
}
