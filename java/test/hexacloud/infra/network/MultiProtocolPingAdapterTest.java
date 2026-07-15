package hexacloud.infra.network;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.model.ServerNode;

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
        CompletableFuture<NodeStatus> future = adapter.fetchPingAsync("test-cluster", node);
        assertNotNull(future);
        assertEquals(NodeStatus.ONLINE, future.get());
    }

    @Test
    public void testPingProtocolRoutingFailuresGracefully() throws Exception {
        // TCP offline ping should fail gracefully and return OFFLINE instead of throwing exceptions
        ServerNode nodeTcp = new ServerNode("127.0.0.1", 19999, NodeStatus.OFFLINE, false, PingProtocol.TCP, "/", null, null);
        CompletableFuture<NodeStatus> tcpFuture = adapter.fetchPingAsync("test-cluster", nodeTcp);
        assertEquals(NodeStatus.OFFLINE, tcpFuture.get());

        // UDP offline ping should fail gracefully
        ServerNode nodeUdp = new ServerNode("127.0.0.1", 19999, NodeStatus.OFFLINE, false, PingProtocol.UDP, "/", null, null);
        CompletableFuture<NodeStatus> udpFuture = adapter.fetchPingAsync("test-cluster", nodeUdp);
        assertNotNull(udpFuture.get());

        // GRPC offline ping should fail gracefully
        ServerNode nodeGrpc = new ServerNode("127.0.0.1", 19999, NodeStatus.OFFLINE, false, PingProtocol.GRPC, "/", null, null);
        CompletableFuture<NodeStatus> grpcFuture = adapter.fetchPingAsync("test-cluster", nodeGrpc);
        assertEquals(NodeStatus.OFFLINE, grpcFuture.get());
    }
}
