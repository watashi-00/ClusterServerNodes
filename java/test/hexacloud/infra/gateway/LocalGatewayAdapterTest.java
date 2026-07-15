package hexacloud.infra.gateway;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class LocalGatewayAdapterTest {

    private LocalGatewayAdapter gateway;

    @BeforeEach
    public void setUp() {
        // Initial setup creating a gateway targeting test-cluster
        gateway = (LocalGatewayAdapter) GatewayFactory.createGateway("test-cluster");
    }

    @Test
    public void testGatewayBuilderSetup() {
        gateway.port(5000)
               .gatewayName("custom-gateway")
               .pingInterval(10)
               .enableHttp(true)
               .enableTelnet(true)
               .enableWs(true);

        assertEquals("custom-gateway", gateway.getGatewayName());
        assertEquals(5000, gateway.getPort());
        assertFalse(gateway.isRunning()); // Should be stopped by default
    }

    @Test
    public void testGatewayDefaultNameFallback() {
        gateway.port(6000);
        // Custom name not set, should default to gw-6000
        assertEquals("gw-6000", gateway.getGatewayName());
    }
}
