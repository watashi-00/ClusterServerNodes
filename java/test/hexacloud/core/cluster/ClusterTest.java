package hexacloud.core.cluster;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import hexacloud.core.cluster.event.ClusterEventBusManager;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;

@ExtendWith(MockitoExtension.class)
public class ClusterTest {

    private Cluster cluster;

    @Mock
    private ClusterEventBusManager eventManager;

    @BeforeEach
    public void setUp() {
        cluster = new Cluster("test-cluster", eventManager);
    }

    @Test
    public void testGetClusterName() {
        assertEquals("test-cluster", cluster.getClusterName());
    }

    @Test
    public void testRegisterServer() {
        ServerNode node = new ServerNode("127.0.0.1", 8080, NodeStatus.OFFLINE, false);
        cluster.registerServer(node);

        assertFalse(cluster.getCluster().isEmpty());
        assertEquals(1, cluster.getCluster().size());
        assertEquals("http://127.0.0.1", cluster.getCluster().get(0).host());
        assertEquals(8080, cluster.getCluster().get(0).port());
    }
}
