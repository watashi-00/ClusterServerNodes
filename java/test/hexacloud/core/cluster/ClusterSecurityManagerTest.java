package hexacloud.core.cluster;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class ClusterSecurityManagerTest {

    private ClusterSecurityManager securityManager;

    @BeforeEach
    public void setUp() {
        securityManager = new ClusterSecurityManager("security-test-cluster");
    }

    @Test
    public void testAuthenticationEnabledAndDisabled() {
        // By default requireToken is true
        securityManager.setSecret("my-token-123");
        securityManager.setRequireToken(true);
        assertTrue(securityManager.isRequireToken());
        assertTrue(securityManager.authenticate("my-token-123"));
        assertFalse(securityManager.authenticate("wrong-token"));

        // Disable requireToken
        securityManager.setRequireToken(false);
        assertFalse(securityManager.isRequireToken());
        assertTrue(securityManager.authenticate("wrong-token"));
    }

    @Test
    public void testIpAllowlistValidation() {
        // Empty allowlist allows all IPs
        securityManager.setAllowedIps("");
        assertTrue(securityManager.isIpAllowed("192.168.1.1"));
        assertTrue(securityManager.isIpAllowed("10.0.0.1"));

        // Set allowed IPs
        securityManager.setAllowedIps("127.0.0.1, 192.168.1.10");
        assertEquals("127.0.0.1, 192.168.1.10", securityManager.getAllowedIps());
        assertTrue(securityManager.isIpAllowed("127.0.0.1"));
        assertTrue(securityManager.isIpAllowed("192.168.1.10"));
        assertFalse(securityManager.isIpAllowed("192.168.1.1"));
    }

    @Test
    public void testRateLimitingConfiguration() {
        securityManager.setRateLimit(50, 10);
        assertEquals(50, securityManager.getRateLimitRequests());
        assertEquals(10, securityManager.getRateLimitDurationSeconds());

        // Under limits, checking rate limit allows requests
        assertTrue(securityManager.checkRateLimit("client-1"));
    }
}
