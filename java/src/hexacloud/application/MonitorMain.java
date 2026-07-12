package hexacloud.application;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import hexacloud.core.config.EnvLoader;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import hexacloud.core.model.NodeStatus;
import hexacloud.infra.gateway.GatewayFactory;

public class MonitorMain {

    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String YELLOW = "\033[33m";
    private static final String CYAN = "\033[36m";
    private static final String WHITE_BOLD = "\033[1;37m";

    private static final int SCREEN_MAIN_MENU = 0;
    private static final int SCREEN_TELEMETRY = 1;
    private static final int SCREEN_CLUSTERS = 2;
    private static final int SCREEN_CONFIG = 3;

    private int currentScreen = SCREEN_MAIN_MENU;
    private int selectedMenuIndex = 0;
    private final String[] menuOptions = {
        "1. Monitor Telemetry",
        "2. View Clusters & Services",
        "3. General Configurations",
        "4. Exit"
    };

    private final String clusterName;
    private final String secret;
    private final int httpPort;
    private final Scanner scanner;
    private boolean running = true;
    private List<NodeInfo> nodes = new ArrayList<>();

    public static void main(String[] args) {
        new MonitorMain().start();
    }

    public MonitorMain() {
        this.clusterName = "watashi-00";
        this.secret = EnvLoader.get(clusterName, "secret", "watashi_secretKey");
        this.httpPort = 3001;
        this.scanner = new Scanner(System.in);
    }

    public void start() {
        DebugUtils.setTuiModeActive(true);

        // Bootstrap gateway server in background
        GatewayFactory.createGateway(clusterName)
            .port(3000)
            .pingInterval(5)
            .enableTelnet(true)
            .enableHttp(true)
            .enableWs(true)
            .registerServer(3001, NodeStatus.OFFLINE)
            .registerServer(3002, NodeStatus.OFFLINE)
            .registerServer(3003, NodeStatus.OFFLINE)
            .registerServer(3004, NodeStatus.OFFLINE)
            .registerServer(3005, NodeStatus.OFFLINE)
            .listen()
            .startPingScheduler();

        NativeTerminal.initTerminal();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            NativeTerminal.resetTerminal();
        }));

        try {
            long lastFetch = 0;
            boolean needRedraw = true;

            while (running) {
                long now = System.currentTimeMillis();
                if (now - lastFetch >= 1200) {
                    fetchNodeStatus();
                    lastFetch = now;
                    needRedraw = true;
                }

                if (needRedraw) {
                    drawDashboard();
                    needRedraw = false;
                }

                int key = NativeTerminal.readKey();
                if (key != -1) {
                    handleKeyPress(key);
                    needRedraw = true;
                }

                Thread.sleep(100);
            }
        } catch (Exception e) {
            NativeTerminal.resetTerminal();
            e.printStackTrace();
        } finally {
            NativeTerminal.resetTerminal();
        }
    }

    private void fetchNodeStatus() {
        try {
            URL url = java.net.URI.create("http://localhost:" + httpPort + "/GET_NODES").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Cluster-Token", secret);
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);

            int status = conn.getResponseCode();
            if (status == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine();
                in.close();

                if (response != null && !response.isEmpty()) {
                    List<NodeInfo> newNodes = new ArrayList<>();
                    for (String part : response.split(";")) {
                        if (part.contains("=")) {
                            String[] split = part.split("=");
                            String fullHost = split[0];
                            String state = split[1];
                            newNodes.add(new NodeInfo(fullHost, state));
                        }
                    }
                    this.nodes = newNodes;
                }
            }
        } catch (Exception e) {
            this.nodes.clear();
        }
    }

    private void drawDashboard() {
        NativeTerminal.clearScreen();

        // Common header
        NativeTerminal.printAt(1, 1, CYAN + "╔══════════════════════════════════════════════════════════════════════════════╗" + RESET);
        NativeTerminal.printAt(1, 2, CYAN + "║                         " + WHITE_BOLD + "HEXACLOUD TELEMETRY MONITOR" + CYAN + "                          ║" + RESET);
        NativeTerminal.printAt(1, 3, CYAN + "╚══════════════════════════════════════════════════════════════════════════════╝" + RESET);

        switch (currentScreen) {
            case SCREEN_MAIN_MENU:
                drawMainMenu();
                break;
            case SCREEN_TELEMETRY:
                drawTelemetryScreen();
                break;
            case SCREEN_CLUSTERS:
                drawClustersScreen();
                break;
            case SCREEN_CONFIG:
                drawConfigScreen();
                break;
        }
    }

    private void drawMainMenu() {
        NativeTerminal.printAt(3, 5, WHITE_BOLD + "Welcome to the Hexacloud TUI Monitor Dashboard" + RESET);
        NativeTerminal.printAt(3, 6, "Use UP / DOWN arrow keys to navigate and ENTER to select an option.");

        int y = 9;
        for (int i = 0; i < menuOptions.length; i++) {
            if (i == selectedMenuIndex) {
                NativeTerminal.printAt(5, y, CYAN + "➔   " + WHITE_BOLD + menuOptions[i] + RESET);
            } else {
                NativeTerminal.printAt(5, y, "    " + menuOptions[i]);
            }
            y += 2;
        }

        NativeTerminal.printAt(3, y + 2, "Press [Q] or [4] to Exit.");
    }

    private void drawTelemetryScreen() {
        // Cluster details metadata
        NativeTerminal.printAt(3, 5, WHITE_BOLD + "Cluster Name: " + RESET + clusterName);
        NativeTerminal.printAt(3, 6, WHITE_BOLD + "Target Host:  " + RESET + "http://localhost:" + httpPort);
        NativeTerminal.printAt(40, 5, WHITE_BOLD + "Config File:  " + RESET + "resources/hexacloud.properties");
        NativeTerminal.printAt(40, 6, WHITE_BOLD + "Library C:    " + RESET + "libhexaterminal.so (Loaded)");

        // Table Header
        NativeTerminal.printAt(3, 8, CYAN + "┌──────────────────────────────────────────────┬──────────────┬────────────────┐" + RESET);
        NativeTerminal.printAt(3, 9, CYAN + "│ " + WHITE_BOLD + "NODE ENDPOINT" + CYAN + "                                │ " + WHITE_BOLD + "PORT" + CYAN + "         │ " + WHITE_BOLD + "STATUS" + CYAN + "         │" + RESET);
        NativeTerminal.printAt(3, 10, CYAN + "├──────────────────────────────────────────────┼──────────────┼────────────────┤" + RESET);

        int y = 11;
        if (nodes.isEmpty()) {
            NativeTerminal.printAt(5, y, RED + "No nodes registered or Control Plane is unreachable." + RESET);
            y++;
        } else {
            for (NodeInfo node : nodes) {
                String host = node.host;
                String statusText = node.status;
                String coloredStatus = GREEN + "ONLINE" + RESET;
                if (statusText.equals("OFFLINE")) {
                    coloredStatus = RED + "OFFLINE" + RESET;
                } else if (statusText.equals("UNSTABLE")) {
                    coloredStatus = YELLOW + "UNSTABLE" + RESET;
                }

                String portStr = "-";
                int lastColon = host.lastIndexOf(':');
                if (lastColon != -1) {
                    portStr = host.substring(lastColon + 1);
                    host = host.substring(0, lastColon);
                }

                String line = String.format("│ %-44s │ %-12s │ %-23s │", host, portStr, coloredStatus);
                NativeTerminal.printAt(3, y, line);
                y++;
            }
        }

        NativeTerminal.printAt(3, y, CYAN + "└──────────────────────────────────────────────┴──────────────┴────────────────┘" + RESET);
        y += 2;

        // Logs block
        NativeTerminal.printAt(3, y, WHITE_BOLD + "Recent System Logs:" + RESET);
        y++;
        
        List<String> logs = DebugUtils.getRecentLogs();
        if (logs.isEmpty()) {
            NativeTerminal.printAt(5, y, "No logs recorded yet.");
            y++;
        } else {
            for (String logLine : logs) {
                String clearedLine = logLine + "                                                                                ";
                if (clearedLine.length() > 76) {
                    clearedLine = clearedLine.substring(0, 76);
                }
                if (logLine.contains("[ERROR]")) {
                    NativeTerminal.printAt(5, y, RED + clearedLine + RESET);
                } else if (logLine.contains("[INFO]")) {
                    NativeTerminal.printAt(5, y, CYAN + clearedLine + RESET);
                } else {
                    NativeTerminal.printAt(5, y, clearedLine);
                }
                y++;
            }
        }
        y += 2;

        NativeTerminal.printAt(3, y, WHITE_BOLD + "Keyboard Actions:" + RESET);
        NativeTerminal.printAt(3, y + 1, CYAN + "[Q/ESC]" + RESET + " Back to Menu   " + CYAN + "[R]" + RESET + " Refresh   " + CYAN + "[A]" + RESET + " Add Node   " + CYAN + "[S]" + RESET + " Export Report");
    }

    private void drawClustersScreen() {
        NativeTerminal.printAt(3, 5, WHITE_BOLD + "Active Cluster Details" + RESET);
        NativeTerminal.printAt(3, 7, WHITE_BOLD + "Cluster Identifier: " + RESET + clusterName);

        // Security Policies (Note: Secrets are EXCLUDED for safety as requested!)
        NativeTerminal.printAt(3, 9, WHITE_BOLD + "Access Security Configurations:" + RESET);
        NativeTerminal.printAt(5, 10, "API Token Validation:  " + GREEN + "Required" + RESET);
        NativeTerminal.printAt(5, 11, "Token Secret Key:      " + RED + "[HIDDEN / SECURED]" + RESET);
        NativeTerminal.printAt(5, 12, "Allowed IP Addresses:  " + CYAN + "127.0.0.1, 0:0:0:0:0:0:0:1" + RESET);
        NativeTerminal.printAt(5, 13, "Connection Timeout:    " + "5000 ms");
        NativeTerminal.printAt(5, 14, "Rate Limiting Policy:  " + YELLOW + "3 requests / 10s" + RESET);

        // Services registered in the cluster
        NativeTerminal.printAt(3, 16, WHITE_BOLD + "Registered Services & Nodes:" + RESET);
        int y = 17;
        if (nodes.isEmpty()) {
            NativeTerminal.printAt(5, y, "No services active.");
            y++;
        } else {
            for (NodeInfo node : nodes) {
                String coloredStatus = node.status.equals("ONLINE") ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;
                if (node.status.equals("UNSTABLE")) coloredStatus = YELLOW + "UNSTABLE" + RESET;
                
                NativeTerminal.printAt(5, y, "• Service host: " + node.host + " -> Status: " + coloredStatus);
                y++;
            }
        }

        NativeTerminal.printAt(3, y + 2, CYAN + "[Q/ESC]" + RESET + " Back to Main Menu");
    }

    private void drawConfigScreen() {
        NativeTerminal.printAt(3, 5, WHITE_BOLD + "Global System Configurations" + RESET);

        NativeTerminal.printAt(3, 7, WHITE_BOLD + "Gateway Server Port Configuration:" + RESET);
        NativeTerminal.printAt(5, 8, "Base Listening Port:     3000");
        NativeTerminal.printAt(5, 9, "HTTP Dashboard Port:     3001");
        NativeTerminal.printAt(5, 10, "WebSocket Port:          3002 (Stub)");

        NativeTerminal.printAt(3, 12, WHITE_BOLD + "Internal Engine Defaults:" + RESET);
        NativeTerminal.printAt(5, 13, "Max Cluster Capacity:    10 registered nodes");
        NativeTerminal.printAt(5, 14, "Max Handler Workers:     20 Virtual Threads");
        NativeTerminal.printAt(5, 15, "Ping Scheduler Interval: 5 seconds");
        NativeTerminal.printAt(5, 16, "HTTP Protocol Version:   HTTP/1.1");
        NativeTerminal.printAt(5, 17, "Client Connection:       Asynchronous (Virtual Threads)");

        NativeTerminal.printAt(3, 20, CYAN + "[Q/ESC]" + RESET + " Back to Main Menu");
    }

    private void handleKeyPress(int key) {
        if (currentScreen == SCREEN_MAIN_MENU) {
            if (key == 1000) { // UP Arrow
                selectedMenuIndex--;
                if (selectedMenuIndex < 0) {
                    selectedMenuIndex = menuOptions.length - 1;
                }
            } else if (key == 1001) { // DOWN Arrow
                selectedMenuIndex++;
                if (selectedMenuIndex >= menuOptions.length) {
                    selectedMenuIndex = 0;
                }
            } else if (key == 10 || key == 13) { // ENTER
                if (selectedMenuIndex == 0) {
                    currentScreen = SCREEN_TELEMETRY;
                } else if (selectedMenuIndex == 1) {
                    currentScreen = SCREEN_CLUSTERS;
                } else if (selectedMenuIndex == 2) {
                    currentScreen = SCREEN_CONFIG;
                } else if (selectedMenuIndex == 3) {
                    running = false;
                }
            } else if (key == 'q' || key == 'Q') {
                running = false;
            }
        } else {
            // In sub-screens, ESC (27) or Q/q exits to main menu
            if (key == 27 || key == 'q' || key == 'Q') {
                currentScreen = SCREEN_MAIN_MENU;
            } else if (currentScreen == SCREEN_TELEMETRY) {
                if (key == 'r' || key == 'R') {
                    fetchNodeStatus();
                } else if (key == 'a' || key == 'A') {
                    addNewNodePrompt();
                } else if (key == 's' || key == 'S') {
                    exportReport();
                }
            }
        }
    }

    private void addNewNodePrompt() {
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter port to register new local node: " + RESET);
        
        if (scanner.hasNextInt()) {
            int port = scanner.nextInt();
            registerNodeRequest(port);
        } else {
            System.out.println(RED + "Invalid port number format." + RESET);
            try { Thread.sleep(800); } catch (Exception e) {}
        }
        
        NativeTerminal.initTerminal();
        fetchNodeStatus();
    }

    private void registerNodeRequest(int port) {
        try {
            URL url = java.net.URI.create("http://localhost:" + httpPort + "/REGISTER?" + port).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Cluster-Token", secret);
            conn.setConnectTimeout(2000);
            
            int status = conn.getResponseCode();
            if (status == 200) {
                System.out.println(GREEN + "SUCCESS: Registered node on port " + port + RESET);
            } else {
                System.out.println(RED + "ERROR: Registration failed with status code " + status + RESET);
            }
        } catch (Exception e) {
            System.out.println(RED + "ERROR: Could not communicate with server: " + e.getMessage() + RESET);
        }
        try { Thread.sleep(800); } catch (Exception e) {}
    }

    private void exportReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== HEXACLOUD TELEMETRY REPORT ===\n");
        sb.append("Cluster: ").append(clusterName).append("\n");
        sb.append("Timestamp: ").append(new java.util.Date().toString()).append("\n\n");
        sb.append(String.format("%-40s | %-10s\n", "NODE ENDPOINT", "STATUS"));
        sb.append("--------------------------------------------------\n");
        for (NodeInfo node : nodes) {
            sb.append(String.format("%-40s | %-10s\n", node.host, node.status));
        }

        String filename = "hexacloud_report.txt";
        boolean success = NativeTerminal.saveConfig(filename, sb.toString());

        NativeTerminal.resetTerminal();
        if (success) {
            System.out.println("\n" + GREEN + "SUCCESS: Telemetry report exported to '" + filename + "'" + RESET);
        } else {
            System.out.println("\n" + RED + "ERROR: Failed to save telemetry report file." + RESET);
        }
        try { Thread.sleep(1200); } catch (Exception e) {}
        NativeTerminal.initTerminal();
    }

    private static class NodeInfo {
        String host;
        String status;

        NodeInfo(String host, String status) {
            this.host = host;
            this.status = status;
        }
    }
}
