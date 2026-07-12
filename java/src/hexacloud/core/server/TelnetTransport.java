package hexacloud.core.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hexacloud.core.config.ClusterConfig;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.utils.DebugUtils;

public class TelnetTransport implements ServerTransport {

    private boolean clusterActive = true;
    private ServerSocket serverSocket;
    private boolean running = false;
    private final ExecutorService threadPool = Executors.newFixedThreadPool(ClusterConfig.MAX_WORKERS);

    @Override
    public void listen(int port, RouteRegistry registry) {
        new Thread(() -> serverListen(port, registry), "TelnetServer-Listener-" + port).start();
    }

    private void serverListen(int port, RouteRegistry registry) {
        DebugUtils.log("Telnet Transport starting to listen on port " + port);
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            DebugUtils.log("Telnet Transport successfully bound and listening on port " + port);
            while(clusterActive) {
                Socket socket = serverSocket.accept();
                DebugUtils.log("Telnet Transport accepted new connection from " + socket.getRemoteSocketAddress());
                threadPool.execute(() -> conn(socket, registry));
            }
        } catch(IOException ex) {
            if (clusterActive) {
                DebugUtils.error("Telnet Transport failed to open port " + port, ex);
            }
        }
    }

    private void conn(Socket socket, RouteRegistry registry) {
        try(
            socket;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        ) {
            String line = in.readLine();
            if(line == null || line.trim().isEmpty()) {
                DebugUtils.log("Telnet received empty connection request from " + socket.getRemoteSocketAddress());
                return;
            }

            DebugUtils.log("Telnet received raw command line: '" + line + "'");

            String[] tokens = line.split(" ", 2);
            String command = tokens[0].toUpperCase();
            String args = tokens.length > 1 ? tokens[1].trim() : "";

            var handler = registry.getRoutes().get(command);

            if(handler == null) {
                DebugUtils.error("Telnet: Unknown command '" + command + "' received from client.");
                out.println("Unknown command " + command);
                return;
            }

            DebugUtils.log("Telnet: Executing route handler for command '" + command + "' with args '" + args + "'");
            handler.accept(args, out);
            DebugUtils.log("Telnet: Successfully completed request handler for command '" + command + "'");
            
        } catch(IOException ex) {
            DebugUtils.error("Failed to process request from client", ex);
        }
    }

    @Override
    public void stop() {
        clusterActive = false;
        running = false;
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                DebugUtils.error("Error closing Telnet server socket", e);
            }
        }
        threadPool.shutdownNow();
        DebugUtils.log("Telnet Transport stopped.");
    }

    @Override
    public boolean isRunning() {
        return running;
    }
}
