package hexacloud.core.server.route;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class RouteRuleTest {
    @Test
    public void testIngressRuleMatching() {
        RouteRule rule = new RouteRule("auth.hexacloud.net.br", "/api/payment/**", "payment-cluster");
        assertTrue(rule.matches("auth.hexacloud.net.br:3003", "/api/payment/checkout"));
        assertFalse(rule.matches("other.domain.com", "/api/payment/checkout"));
        assertFalse(rule.matches("auth.hexacloud.net.br", "/api/auth/login"));
    }
}
