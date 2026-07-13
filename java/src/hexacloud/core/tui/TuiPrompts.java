package hexacloud.core.tui;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.GatewayPort;
import hexacloud.core.utils.NativeTerminal;
import hexacloud.core.utils.TerminalScanner;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles all interactive CLI prompts and input reading for the Terminal UI.
 */
public class TuiPrompts {

    private final TerminalUI tui;

    public TuiPrompts(TerminalUI tui) {
        this.tui = tui;
    }

    public void createNewClusterPrompt() {
        TuiState state = tui.state();
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter unique name for new cluster (or /cancel to abort): " + RESET);
        String name = TerminalScanner.readLine();
        if (name.equalsIgnoreCase("/cancel") || name.isEmpty()) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
            try { Thread.sleep(800); } catch (Exception e) {}
        } else {
            if (ClusterRegistry.getInstance().getCluster(name) != null) {
                System.out.println(RED + "ERROR: Cluster '" + name + "' already exists." + RESET);
                try { Thread.sleep(1500); } catch (Exception e) {}
            } else {
                ClusterRegistry.getInstance().createCluster(name);
                System.out.println(GREEN + "SUCCESS: Created cluster '" + name + "'" + RESET);
                if (tui.gatewayManagementEnabled()) {
                    System.out.print("Do you want to configure and start a gateway for this cluster now? (y/n): ");
                    String ans = TerminalScanner.readLine();
                    if (ans.equalsIgnoreCase("y")) {
                        state.selectedClusterName = name;
                        manageGatewayPrompt();
                        return;
                    }
                }
                try { Thread.sleep(800); } catch (Exception e) {}
            }
        }
        NativeTerminal.initTerminal();
        tui.fetchClusterNames();
        state.selectedClusterIndex = 0;
    }

    public void manageGatewayPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) {
            NativeTerminal.resetTerminal();
            System.out.println(YELLOW + "Please select or create a cluster first." + RESET);
            try { Thread.sleep(800); } catch (Exception e) {}
            NativeTerminal.initTerminal();
            return;
        }

        GatewayPort currentGw = tui.activeGateways().get(state.selectedClusterName);
        NativeTerminal.resetTerminal();
        System.out.println("\n" + WHITE_BOLD + "=== Gateway Setup for Cluster: " + state.selectedClusterName + " ===" + RESET);
        if (currentGw != null) {
            System.out.println("Status: " + GREEN + "ONLINE" + RESET);
            System.out.print("Do you want to STOP the gateway? (y/n) [/cancel]: ");
            String ans = TerminalScanner.readLine();
            if (ans.equalsIgnoreCase("y")) {
                currentGw.stop();
                tui.activeGateways().remove(state.selectedClusterName);
                System.out.println(GREEN + "SUCCESS: Gateway stopped." + RESET);
                try { Thread.sleep(800); } catch (Exception e) {}
            }
        } else {
            System.out.println("Status: " + RED + "OFFLINE" + RESET);
            System.out.print("Do you want to START the gateway? (y/n) [/cancel]: ");
            String ans = TerminalScanner.readLine();
            if (ans.equalsIgnoreCase("y")) {
                try {
                    System.out.print("Enter base port (default 3000) [/cancel]: ");
                    String portStr = TerminalScanner.readLine();
                    if (portStr.equalsIgnoreCase("/cancel")) { NativeTerminal.initTerminal(); return; }
                    int port = portStr.isEmpty() ? 3000 : Integer.parseInt(portStr);

                    System.out.print("Enter ping check interval in seconds (default 5) [/cancel]: ");
                    String intStr = TerminalScanner.readLine();
                    if (intStr.equalsIgnoreCase("/cancel")) { NativeTerminal.initTerminal(); return; }
                    int pingInt = intStr.isEmpty() ? 5 : Integer.parseInt(intStr);

                    System.out.print("Enable Telnet? (y/n, default y) [/cancel]: ");
                    String telnetStr = TerminalScanner.readLine();
                    if (telnetStr.equalsIgnoreCase("/cancel")) { NativeTerminal.initTerminal(); return; }
                    boolean telnet = !telnetStr.equalsIgnoreCase("n");

                    System.out.print("Enable HTTP? (y/n, default y) [/cancel]: ");
                    String httpStr = TerminalScanner.readLine();
                    if (httpStr.equalsIgnoreCase("/cancel")) { NativeTerminal.initTerminal(); return; }
                    boolean http = !httpStr.equalsIgnoreCase("n");

                    System.out.print("Enable WS? (y/n, default y) [/cancel]: ");
                    String wsStr = TerminalScanner.readLine();
                    if (wsStr.equalsIgnoreCase("/cancel")) { NativeTerminal.initTerminal(); return; }
                    boolean ws = !wsStr.equalsIgnoreCase("n");

                    GatewayPort newGw = hexacloud.infra.gateway.GatewayFactory.createGateway(state.selectedClusterName)
                        .port(port)
                        .pingInterval(pingInt)
                        .enableTelnet(telnet)
                        .enableHttp(http)
                        .enableWs(ws)
                        .listen()
                        .startPingScheduler();

                    tui.activeGateways().put(state.selectedClusterName, newGw);
                    System.out.println(GREEN + "SUCCESS: Gateway started successfully." + RESET);
                    try { Thread.sleep(1000); } catch (Exception e) {}
                } catch (Exception e) {
                    System.out.println(RED + "ERROR: Failed to start gateway: " + e.getMessage() + RESET);
                    try { Thread.sleep(1500); } catch (Exception ex) {}
                }
            }
        }
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void addNewNodePrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter port to register new service node in " + state.selectedClusterName + " (or /cancel to abort): " + RESET);
        String input = TerminalScanner.readLine();
        if (input.equalsIgnoreCase("/cancel") || input.isEmpty()) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } else {
            try {
                int port = Integer.parseInt(input);
                Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
                if (c != null) {
                    c.registerServer(port);
                    System.out.println(GREEN + "SUCCESS: Registered service node." + RESET);
                }
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid port format." + RESET);
            }
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void changeAllowedIpsPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter Allowed IPs (comma-separated, or /cancel to abort): " + RESET);
        String ips = TerminalScanner.readLine();
        if (ips.equalsIgnoreCase("/cancel")) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } else {
            Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
            if (c != null) {
                c.setAllowedIps(ips);
                System.out.println(GREEN + "SUCCESS: Allowed IPs updated." + RESET);
            }
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        tui.fetchClusterConfig(state.selectedClusterName);
    }

    public void changeTimeoutPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter Timeout in ms (or /cancel to abort): " + RESET);
        String input = TerminalScanner.readLine();
        if (input.equalsIgnoreCase("/cancel") || input.isEmpty()) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } else {
            try {
                int timeout = Integer.parseInt(input);
                Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
                if (c != null) {
                    c.setTimeoutMs(timeout);
                    System.out.println(GREEN + "SUCCESS: Timeout updated." + RESET);
                }
            } catch (NumberFormatException e) {
                System.out.println(RED + "Invalid timeout format." + RESET);
            }
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        tui.fetchClusterConfig(state.selectedClusterName);
    }

    public void changeRateLimitPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter Rate Limit (format: <requests> <durationSeconds>, or /cancel to abort): " + RESET);
        String line = TerminalScanner.readLine();
        if (line.equalsIgnoreCase("/cancel") || line.isEmpty()) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } else {
            try {
                String[] parts = line.split(" ");
                if (parts.length >= 2) {
                    int requests = Integer.parseInt(parts[0]);
                    int duration = Integer.parseInt(parts[1]);
                    Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
                    if (c != null) {
                        c.setRateLimit(requests, duration);
                        System.out.println(GREEN + "SUCCESS: Rate limit updated." + RESET);
                    }
                } else {
                    System.out.println(RED + "Invalid format. Expected: <requests> <durationSeconds>" + RESET);
                }
            } catch (Exception e) {
                System.out.println(RED + "ERROR: Update failed." + RESET);
            }
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        tui.fetchClusterConfig(state.selectedClusterName);
    }

    public void changeNodePingPathPrompt(Cluster cluster, ServerNode node) {
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter new ping endpoint route (or /cancel to abort): " + RESET);
        String path = TerminalScanner.readLine();
        if (!path.equalsIgnoreCase("/cancel")) {
            ServerNode updated = new ServerNode(
                node.host(), node.port(), node.status(), node.isExternal(),
                node.pingEnabled(), path, node.pingHeaderName(), node.pingHeaderValue()
            );
            cluster.updateServerNode(updated);
            System.out.println(GREEN + "SUCCESS: Ping endpoint path updated." + RESET);
            try { Thread.sleep(800); } catch (Exception e) {}
        }
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void changeNodePingHeaderNamePrompt(Cluster cluster, ServerNode node) {
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter new ping auth header name (or /cancel to abort, empty for None): " + RESET);
        String name = TerminalScanner.readLine();
        if (!name.equalsIgnoreCase("/cancel")) {
            String val = name.isEmpty() ? null : node.pingHeaderValue();
            ServerNode updated = new ServerNode(
                node.host(), node.port(), node.status(), node.isExternal(),
                node.pingEnabled(), node.pingPath(), name.isEmpty() ? null : name, val
            );
            cluster.updateServerNode(updated);
            System.out.println(GREEN + "SUCCESS: Ping header name updated." + RESET);
            try { Thread.sleep(800); } catch (Exception e) {}
        }
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void changeNodePingHeaderValuePrompt(Cluster cluster, ServerNode node) {
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter new ping header token value (or /cancel to abort, empty for None): " + RESET);
        String val = TerminalScanner.readLine();
        if (!val.equalsIgnoreCase("/cancel")) {
            ServerNode updated = new ServerNode(
                node.host(), node.port(), node.status(), node.isExternal(),
                node.pingEnabled(), node.pingPath(), node.pingHeaderName(), val.isEmpty() ? null : val
            );
            cluster.updateServerNode(updated);
            System.out.println(GREEN + "SUCCESS: Ping header value updated." + RESET);
            try { Thread.sleep(800); } catch (Exception e) {}
        }
        NativeTerminal.initTerminal();
        tui.fetchNodeStatus();
    }

    public void changeSecretPrompt() {
        TuiState state = tui.state();
        if (state.selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter new cluster secret API token (or /cancel to abort): " + RESET);
        String secret = TerminalScanner.readLine();
        if (secret.equalsIgnoreCase("/cancel")) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } else {
            Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
            if (c != null) {
                c.setSecret(secret);
                System.out.println(GREEN + "SUCCESS: Cluster secret API token updated." + RESET);
            }
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        tui.fetchClusterConfig(state.selectedClusterName);
    }
}
