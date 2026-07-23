package hexacloud.infra.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.server.route.RouteRegistry;
import hexacloud.core.server.route.RouteRule;
import hexacloud.core.server.route.RouteController;
import hexacloud.core.server.route.RouteMapping;
import java.io.PrintWriter;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class IngressRoutingTest {

    private HttpServer backend1;
    private HttpServer backend2;
    private int backendPort1;
    private int backendPort2;
    private int gatewayPort1;
    private int gatewayPort2;

    private HttpTransport jdkTransport;
    private UndertowHttpTransport undertowTransport;
    private Cluster testCluster;

    @BeforeEach
    public void setUp() throws Exception {
        System.setProperty("sun.net.http.allowRestrictedHeaders", "true");
        backendPort1 = findFreePort();
        backendPort2 = findFreePort();
        gatewayPort1 = findFreePort();
        gatewayPort2 = findFreePort();

        // Setup Backend 1 (Normal backend node)
        backend1 = HttpServer.create(new InetSocketAddress(backendPort1), 0);
        backend1.createContext("/", exchange -> {
            byte[] resp = "Backend 1".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        backend1.start();

        // Setup Backend 2 (Telemetry-only backend node)
        backend2 = HttpServer.create(new InetSocketAddress(backendPort2), 0);
        backend2.createContext("/", exchange -> {
            byte[] resp = "Backend 2 Telemetry".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        backend2.start();

        // Register Cluster
        testCluster = new Cluster("ingress-test-cluster");
        testCluster.setRequireToken(false);
        testCluster.setRoutingMode(Cluster.RoutingMode.HYBRID);

        ServerNode node1 = new ServerNode("node-1", "http://127.0.0.1", backendPort1, NodeStatus.ONLINE, false, hexacloud.core.model.PingProtocol.HTTP, "/", null, null, false, false);
        ServerNode node2 = new ServerNode("node-2", "http://127.0.0.1", backendPort2, NodeStatus.ONLINE, false, hexacloud.core.model.PingProtocol.HTTP, "/", null, null, false, true); // telemetryOnly = true

        testCluster.registerServer(node1);
        testCluster.registerServer(node2);

        ClusterRegistry.getInstance().clear();
        ClusterRegistry.getInstance().registerCluster(testCluster);
    }

    @AfterEach
    public void tearDown() {
        if (jdkTransport != null && jdkTransport.isRunning()) {
            jdkTransport.stop();
        }
        if (undertowTransport != null && undertowTransport.isRunning()) {
            undertowTransport.stop();
        }
        if (backend1 != null) {
            backend1.stop(0);
        }
        if (backend2 != null) {
            backend2.stop(0);
        }
        ClusterRegistry.getInstance().clear();
    }

    private int findFreePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            socket.setReuseAddress(true);
            return socket.getLocalPort();
        }
    }

    @Test
    public void testV1PrefixPeelingAndNodeFilterJdkTransport() throws Exception {
        RouteRegistry registry = new RouteRegistry();
        jdkTransport = new HttpTransport();
        jdkTransport.listen(gatewayPort1, registry, testCluster, Collections.emptyList());

        // Test /v1/clusters/ingress-test-cluster/api
        URL url = new URL("http://127.0.0.1:" + gatewayPort1 + "/v1/clusters/ingress-test-cluster/api");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }
        assertEquals("Backend 1", body);
    }

    @Test
    public void testIngressRuleRoutingJdkTransport() throws Exception {
        RouteRegistry registry = new RouteRegistry();
        registry.addRouteRule(new RouteRule("127.0.0.1", "/app/**", "ingress-test-cluster"));

        jdkTransport = new HttpTransport();
        jdkTransport.listen(gatewayPort1, registry, testCluster, Collections.emptyList());

        URL url = new URL("http://127.0.0.1:" + gatewayPort1 + "/app/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }
        assertEquals("Backend 1", body);
    }

    @Test
    public void testIngressRuleRoutingUndertowTransport() throws Exception {
        RouteRegistry registry = new RouteRegistry();
        registry.addRouteRule(new RouteRule("127.0.0.1", "/app/**", "ingress-test-cluster"));

        undertowTransport = new UndertowHttpTransport();
        undertowTransport.listen(gatewayPort2, registry, testCluster, Collections.emptyList());

        URL url = new URL("http://127.0.0.1:" + gatewayPort2 + "/app/users");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }
        assertEquals("Backend 1", body);
    }

    @Test
    public void testTelemetryOnlyNodesExcludedJdkTransport() throws Exception {
        RouteRegistry registry = new RouteRegistry();
        Cluster telemetryOnlyCluster = new Cluster("telemetry-only-cluster");
        telemetryOnlyCluster.setRequireToken(false);
        telemetryOnlyCluster.setRoutingMode(Cluster.RoutingMode.HYBRID);
        ServerNode node = new ServerNode("node-t", "http://127.0.0.1", backendPort2, NodeStatus.ONLINE, false, hexacloud.core.model.PingProtocol.HTTP, "/", null, null, false, true);
        telemetryOnlyCluster.registerServer(node);
        ClusterRegistry.getInstance().registerCluster(telemetryOnlyCluster);

        jdkTransport = new HttpTransport();
        jdkTransport.listen(gatewayPort1, registry, telemetryOnlyCluster, Collections.emptyList());

        URL url = new URL("http://127.0.0.1:" + gatewayPort1 + "/clusters/telemetry-only-cluster/data");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(503, conn.getResponseCode());
    }

    @Test
    public void testTelemetryOnlyNodesExcludedUndertowTransport() throws Exception {
        RouteRegistry registry = new RouteRegistry();
        Cluster telemetryOnlyCluster = new Cluster("telemetry-only-cluster-undertow");
        telemetryOnlyCluster.setRequireToken(false);
        telemetryOnlyCluster.setRoutingMode(Cluster.RoutingMode.HYBRID);
        ServerNode node = new ServerNode("node-t", "http://127.0.0.1", backendPort2, NodeStatus.ONLINE, false, hexacloud.core.model.PingProtocol.HTTP, "/", null, null, false, true);
        telemetryOnlyCluster.registerServer(node);
        ClusterRegistry.getInstance().registerCluster(telemetryOnlyCluster);

        undertowTransport = new UndertowHttpTransport();
        undertowTransport.listen(gatewayPort2, registry, telemetryOnlyCluster, Collections.emptyList());

        URL url = new URL("http://127.0.0.1:" + gatewayPort2 + "/clusters/telemetry-only-cluster-undertow/data");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(503, conn.getResponseCode());
    }

    @Test
    public void testLocalRoutePriorityJdkTransport() throws Exception {
        RouteRegistry registry = new RouteRegistry();
        registry.registerController(new RouteController() {
            @RouteMapping("TEST_LOCAL")
            public void testLocal(String args, PrintWriter out) {
                out.print("LOCAL RESPONSE");
            }
        });
        registry.addRouteRule(new RouteRule("127.0.0.1", "/**", "ingress-test-cluster"));

        jdkTransport = new HttpTransport();
        jdkTransport.listen(gatewayPort1, registry, testCluster, Collections.emptyList());

        URL url = new URL("http://127.0.0.1:" + gatewayPort1 + "/test_local");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }
        assertEquals("LOCAL RESPONSE", body);
    }

    @Test
    public void testLocalRoutePriorityUndertowTransport() throws Exception {
        RouteRegistry registry = new RouteRegistry();
        registry.registerController(new RouteController() {
            @RouteMapping("TEST_LOCAL")
            public void testLocal(String args, PrintWriter out) {
                out.print("LOCAL RESPONSE");
            }
        });
        registry.addRouteRule(new RouteRule("127.0.0.1", "/**", "ingress-test-cluster"));

        undertowTransport = new UndertowHttpTransport();
        undertowTransport.listen(gatewayPort2, registry, testCluster, Collections.emptyList());

        URL url = new URL("http://127.0.0.1:" + gatewayPort2 + "/test_local");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");

        assertEquals(200, conn.getResponseCode());
        String body;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            body = reader.lines().collect(Collectors.joining("\n"));
        }
        assertEquals("LOCAL RESPONSE", body);
    }
}
