package hexacloud.infra.server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;

import hexacloud.core.cluster.event.ClusterEvent;
import hexacloud.core.event.Event;
import hexacloud.core.event.EventBusManager;
import hexacloud.core.event.EventListener;
import hexacloud.core.server.ServerTransport;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.utils.Casts;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.StrUtils;
import hexacloud.core.utils.ThreadManager;

/**
 * WebSocket event stream transport for cluster events.
 */
public class WsTransport implements ServerTransport {
    private static final String WS_MAGIC = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";

    private final ExecutorService threadPool = ThreadManager.newVirtualThreadPool();
    private final List<ClientConnection> clients = new CopyOnWriteArrayList<>();
    private final EventListener<Event> eventInterceptor = this::broadcastEvent;

    private ServerSocket serverSocket;
    private volatile boolean running = false;

    @Override
    public void listen(int port, RouteRegistry registry, hexacloud.core.cluster.Cluster cluster) {
        threadPool.execute(() -> serverListen(port));
    }

    private void serverListen(int port) {
        try {
            serverSocket = new ServerSocket(port);
            running = true;
            EventBusManager.getGlobal().addInterceptor(eventInterceptor);
            DebugUtils.info("WebSocket Transport successfully bound and listening on port " + port);

            while (running) {
                Socket socket = serverSocket.accept();
                threadPool.execute(() -> acceptClient(socket));
            }
        } catch (IOException ex) {
            if (running) {
                DebugUtils.error("WebSocket Transport failed on listener socket", ex);
            }
        } finally {
            EventBusManager.getGlobal().removeInterceptor(eventInterceptor);
            closeAllClients();
            running = false;
        }
    }

    private void acceptClient(Socket socket) {
        try {
            Map<String, String> headers = readHandshakeHeaders(socket.getInputStream());
            String key = headers.get("sec-websocket-key");
            if (StrUtils.isBlank(key)) {
                socket.close();
                return;
            }

            String acceptKey = createAcceptKey(key);
            OutputStream out = socket.getOutputStream();
            String response = "HTTP/1.1 101 Switching Protocols\r\n"
                + "Upgrade: websocket\r\n"
                + "Connection: Upgrade\r\n"
                + "Sec-WebSocket-Accept: " + acceptKey + "\r\n"
                + "\r\n";
            out.write(response.getBytes(StandardCharsets.US_ASCII));
            out.flush();

            ClientConnection client = new ClientConnection(socket, out);
            clients.add(client);
            client.send("{\"type\":\"Connected\",\"message\":\"GateBridge WebSocket event stream connected\"}");
            waitForClientClose(socket);
            clients.remove(client);
            client.close();
        } catch (Exception e) {
            DebugUtils.error("WebSocket Transport failed to accept client", e);
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }

    private Map<String, String> readHandshakeHeaders(InputStream input) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(input, StandardCharsets.US_ASCII));
        String requestLine = reader.readLine();
        if (requestLine == null || !requestLine.toUpperCase(Locale.ROOT).contains("HTTP/1.1")) {
            throw new IOException("Invalid WebSocket handshake request");
        }

        return reader.lines()
            .takeWhile(line -> line != null && !line.isEmpty())
            .filter(line -> line.contains(":"))
            .map(line -> line.split(":", 2))
            .collect(Collectors.toMap(
                parts -> parts[0].trim().toLowerCase(Locale.ROOT),
                parts -> parts[1].trim(),
                (left, right) -> right
            ));
    }

    private String createAcceptKey(String key) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-1");
        byte[] hash = digest.digest((key.trim() + WS_MAGIC).getBytes(StandardCharsets.US_ASCII));
        return Base64.getEncoder().encodeToString(hash);
    }

    private void waitForClientClose(Socket socket) throws IOException {
        InputStream in = socket.getInputStream();
        try {
            while (running && !socket.isClosed()) {
                if (in.read() == -1) {
                    break;
                }
            }
        } catch (SocketException e) {
            if (running && !socket.isClosed()) {
                throw e;
            }
        }
    }

    private void broadcastEvent(Event event) {
        if (!(Casts.is(event, ClusterEvent.class))) {
            return;
        }

        String payload = toJson(event);
        for (ClientConnection client : clients) {
            try {
                client.send(payload);
            } catch (IOException e) {
                clients.remove(client);
                client.close();
            }
        }
    }

    private String toJson(Event event) {
        return Casts.<String>matchValue(event)
            .when(ClusterEvent.ClusterRegistered.class,
                e -> "{\"type\":\"ClusterRegistered\",\"clusterName\":\"" + json(e.clusterName()) + "\"}")
            .when(ClusterEvent.NodeRegistered.class,
                e -> "{\"type\":\"NodeRegistered\",\"host\":\"" + json(e.node().getFullHost())
                    + "\",\"status\":\"" + e.node().status() + "\"}")
            .when(ClusterEvent.NodeDeregistered.class,
                e -> "{\"type\":\"NodeDeregistered\",\"host\":\"" + json(e.host()) + "\"}")
            .when(ClusterEvent.NodeStatusChanged.class,
                e -> "{\"type\":\"NodeStatusChanged\",\"host\":\"" + json(e.host())
                    + "\",\"status\":\"" + e.status() + "\"}")
            .when(ClusterEvent.NodeTelemetryUpdated.class,
                e -> "{\"type\":\"NodeTelemetryUpdated\",\"host\":\"" + json(e.host()) + "\"}")
            .when(ClusterEvent.NodeEventSubmitted.class,
                e -> "{\"type\":\"NodeEventSubmitted\",\"host\":\"" + json(e.host())
                    + "\",\"port\":" + e.port()
                    + ",\"protocol\":\"" + json(e.protocol())
                    + "\",\"format\":\"" + json(e.format())
                    + "\",\"event\":\"" + json(e.event())
                    + "\",\"attributes\":" + attributesJson(e.attributes()) + "}")
            .otherwise(e ->
                "{\"type\":\"" + json(e.getClass().getSimpleName()) + "\"}");
    }

    private String attributesJson(Map<String, String> attributes) {
        if (attributes == null || attributes.isEmpty()) {
            return "{}";
        }
        return attributes.entrySet().stream()
            .map(entry -> "\"" + json(entry.getKey()) + "\":\"" + json(entry.getValue()) + "\"")
            .collect(Collectors.joining(",", "{", "}"));
    }

    private String json(String value) {
        if (value == null) return "";
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    @Override
    public void stop() {
        running = false;
        EventBusManager.getGlobal().removeInterceptor(eventInterceptor);
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                DebugUtils.error("Error closing WebSocket server socket", e);
            }
        }
        closeAllClients();
        threadPool.shutdownNow();
        DebugUtils.log("WebSocket Transport stopped.");
    }

    private void closeAllClients() {
        for (ClientConnection client : clients) {
            client.close();
        }
        clients.clear();
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private static class ClientConnection {
        private final Socket socket;
        private final OutputStream out;

        private ClientConnection(Socket socket, OutputStream out) {
            this.socket = socket;
            this.out = out;
        }

        private synchronized void send(String text) throws IOException {
            byte[] payload = text.getBytes(StandardCharsets.UTF_8);
            out.write(0x81);
            if (payload.length <= 125) {
                out.write(payload.length);
            } else if (payload.length <= 65535) {
                out.write(126);
                out.write((payload.length >>> 8) & 0xff);
                out.write(payload.length & 0xff);
            } else {
                out.write(127);
                long length = payload.length;
                for (int shift = 56; shift >= 0; shift -= 8) {
                    out.write((int) ((length >>> shift) & 0xff));
                }
            }
            out.write(payload);
            out.flush();
        }

        private void close() {
            try {
                socket.close();
            } catch (IOException ignored) {
            }
        }
    }
}
