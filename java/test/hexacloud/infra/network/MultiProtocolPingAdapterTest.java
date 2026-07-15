package hexacloud.infra.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.sun.net.httpserver.HttpServer;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.model.PingResult;
import hexacloud.core.model.ServerNode;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;

public class MultiProtocolPingAdapterTest {

    private MultiProtocolPingAdapter adapter;

    @BeforeEach
    public void setUp() {
        adapter = new MultiProtocolPingAdapter();
    }

    @Test
    public void testPingProtocolNone() throws Exception {
        ServerNode node = new ServerNode("http://localhost", 8080, NodeStatus.ONLINE, false, PingProtocol.NONE, "/", null, null);
        CompletableFuture<PingResult> future = adapter.fetchPingAsync("test-cluster", node);
        assertNotNull(future);
        assertEquals(NodeStatus.ONLINE, future.get().status());
        assertFalse(future.get().hasTelemetry());
    }

    @Test
    public void testHttpPingWithoutTelemetryDoesNotFlagTelemetry() throws Exception {
        HttpServer server = startHttpServer("OK");
        try {
            ServerNode node = new ServerNode("http://127.0.0.1", server.getAddress().getPort(), NodeStatus.OFFLINE, false);
            PingResult result = adapter.fetchPingAsync("test-cluster", node).get();

            assertEquals(NodeStatus.ONLINE, result.status());
            assertFalse(result.hasTelemetry());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testHttpPingWithTelemetryFlagsTelemetry() throws Exception {
        HttpServer server = startHttpServer("{\"language\":\"Java\",\"cpu\":12.5,\"ram\":64.0}");
        try {
            ServerNode node = new ServerNode("http://127.0.0.1", server.getAddress().getPort(), NodeStatus.OFFLINE, false);
            PingResult result = adapter.fetchPingAsync("test-cluster", node).get();

            assertEquals(NodeStatus.ONLINE, result.status());
            assertTrue(result.hasTelemetry());
            assertEquals("Java", node.runtime());
            assertEquals(12.5, node.cpuUsage());
            assertEquals(64.0, node.ramUsage());
        } finally {
            server.stop(0);
        }
    }

    @Test
    public void testPingProtocolRoutingFailuresGracefully() throws Exception {
        // TCP offline ping should fail gracefully and return OFFLINE instead of throwing exceptions
        ServerNode nodeTcp = new ServerNode("127.0.0.1", 19999, NodeStatus.OFFLINE, false, PingProtocol.TCP, "/", null, null);
        CompletableFuture<PingResult> tcpFuture = adapter.fetchPingAsync("test-cluster", nodeTcp);
        assertEquals(NodeStatus.OFFLINE, tcpFuture.get().status());

        // UDP offline ping should fail gracefully
        ServerNode nodeUdp = new ServerNode("127.0.0.1", 19999, NodeStatus.OFFLINE, false, PingProtocol.UDP, "/", null, null);
        CompletableFuture<PingResult> udpFuture = adapter.fetchPingAsync("test-cluster", nodeUdp);
        assertNotNull(udpFuture.get().status());

        // GRPC offline ping should fail gracefully
        ServerNode nodeGrpc = new ServerNode("127.0.0.1", 19999, NodeStatus.OFFLINE, false, PingProtocol.GRPC, "/", null, null);
        CompletableFuture<PingResult> grpcFuture = adapter.fetchPingAsync("test-cluster", nodeGrpc);
        assertEquals(NodeStatus.OFFLINE, grpcFuture.get().status());
    }

    private HttpServer startHttpServer(String responseBody) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        return server;
    }
}
