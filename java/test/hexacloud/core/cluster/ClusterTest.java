package hexacloud.core.cluster;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

import java.util.List;

@ExtendWith(MockitoExtension.class)
public class ClusterTest {

    private Cluster cluster;

    @Mock
    private ClusterEventBusManager eventManager;

    @BeforeEach
    public void setUp() {
        cluster = new Cluster("test-cluster-env", eventManager);
    }

    @Test
    public void testGetClusterName() {
        assertEquals("test-cluster-env", cluster.getClusterName());
        cluster.setClusterName("new-name");
        assertEquals("new-name", cluster.getClusterName());
    }

    @Test
    public void testRegisterServer() {
        ServerNode node = new ServerNode("127.0.0.1", 8080, NodeStatus.OFFLINE, false);
        cluster.registerServer(node);

        List<ServerNode> nodes = cluster.getCluster();
        assertFalse(nodes.isEmpty());
        assertEquals(1, nodes.size());
        assertEquals("http://127.0.0.1", nodes.get(0).host());
        assertEquals(8080, nodes.get(0).port());
    }

    @Test
    public void testDeregisterServer() {
        ServerNode node = new ServerNode("127.0.0.1", 9000, NodeStatus.OFFLINE, false);
        cluster.registerServer(node);
        assertEquals(1, cluster.getCluster().size());

        cluster.deregisterServer("http://127.0.0.1:9000");
        assertTrue(cluster.getCluster().isEmpty());
    }

    @Test
    public void testDuplicateRegistrationFails() {
        ServerNode node1 = new ServerNode("127.0.0.1", 8080, NodeStatus.OFFLINE, false);
        ServerNode node2 = new ServerNode("127.0.0.1", 8080, NodeStatus.OFFLINE, false);

        cluster.registerServer(node1);
        cluster.registerServer(node2); // Should fail silently due to duplicate validation

        assertEquals(1, cluster.getCluster().size());
    }

    @Test
    public void testBatchTogglingServers() {
        ServerNode node1 = new ServerNode("localhost", 3001, NodeStatus.OFFLINE, false);
        ServerNode node2 = new ServerNode("localhost", 3002, NodeStatus.OFFLINE, false);

        cluster.registerServer(node1);
        cluster.registerServer(node2);
        assertEquals(2, cluster.getCluster().size());

        // Stop all (toggle off)
        cluster.deregisterAllServers();
        assertTrue(cluster.getCluster().isEmpty());

        // Start all (toggle on)
        cluster.registerAllServers();
        assertEquals(2, cluster.getCluster().size());
    }

    @Test
    public void testSecurityDelegation() {
        cluster.setSecret("super-secret");
        assertTrue(cluster.isRequireToken());
        assertEquals("super-secret", cluster.getSecret());
        assertTrue(cluster.authenticate("super-secret"));
        assertFalse(cluster.authenticate("wrong-token"));

        cluster.setRequireToken(false);
        assertFalse(cluster.isRequireToken());
    }

    @Test
    public void testRateLimitingDelegation() {
        cluster.setRateLimit(200, 30);
        assertEquals(200, cluster.getRateLimitRequests());
        assertEquals(30, cluster.getRateLimitDurationSeconds());
    }

    @Test
    public void testTimeoutDelegation() {
        cluster.setTimeoutMs(4500);
        assertEquals(4500, cluster.getTimeoutMs());
    }
}
