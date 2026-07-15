package hexacloud.core.model;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

public class ServerNodeTest {

    @Test
    public void testGetHostWithoutProtocol() {
        ServerNode node1 = new ServerNode("http://localhost", 8080, NodeStatus.OFFLINE, false);
        assertEquals("localhost", node1.getHostWithoutProtocol());

        ServerNode node2 = new ServerNode("ws://192.168.1.5", 3000, NodeStatus.OFFLINE, false);
        assertEquals("192.168.1.5", node2.getHostWithoutProtocol());

        ServerNode node3 = new ServerNode("grpc://my-service.domain.com", 50051, NodeStatus.OFFLINE, false);
        assertEquals("my-service.domain.com", node3.getHostWithoutProtocol());

        // Test fallback if no scheme exists
        ServerNode node4 = new ServerNode("localhost", 9000, NodeStatus.OFFLINE, false);
        assertEquals("localhost", node4.getHostWithoutProtocol());
    }

    @Test
    public void testGetFullHost() {
        ServerNode node = new ServerNode("http://localhost", 8080, NodeStatus.OFFLINE, false);
        assertEquals("http://localhost:8080", node.getFullHost());
    }

    @Test
    public void testWithStatus() {
        ServerNode node = new ServerNode("http://localhost", 8080, NodeStatus.OFFLINE, false);
        ServerNode updatedNode = node.withStatus(NodeStatus.ONLINE);

        assertEquals(NodeStatus.ONLINE, updatedNode.status());
        assertEquals(NodeStatus.OFFLINE, node.status()); // Immutable check
    }
}
