package hexacloud.core.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.utils.DebugUtils;

class Server {

    private boolean clusterActive = true;
    private final int MAX_WORKERS = 20;
    private final Cluster cluster;
    private final int port; 

    private final ExecutorService threadPool = Executors.newFixedThreadPool(MAX_WORKERS);
    
    public Server(int port, Cluster cluster) {
        this.port = port;
        this.cluster = cluster;
    }

    public void listen() {
        serverListen(this.port);
    }
    public void listen(int port) {
        serverListen(port);
    }

    private void serverListen(int port) {
        try(ServerSocket serverSocket = new ServerSocket(port)) {
            while(clusterActive) {
                Socket socket = serverSocket.accept();
                threadPool.execute(() -> conn(socket));
            }
            
        } catch(IOException ex) {
            DebugUtils.error("", ex);
        }
    }

    private void conn(Socket socket) {
        try(
            socket;
            BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
        ) {
            String line = in.readLine();
        } catch(IOException ex) {
            DebugUtils.error("", ex);
        }
    }

}
