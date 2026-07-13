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
import hexacloud.core.model.ServerNode;

public class TerminalUI {

    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String YELLOW = "\033[33m";
    private static final String CYAN = "\033[36m";
    private static final String WHITE_BOLD = "\033[1;37m";

    private static final int SCREEN_MAIN_MENU = 0;
    private static final int SCREEN_SELECT_CLUSTER = 1;
    private static final int SCREEN_TELEMETRY = 2;
    private static final int SCREEN_CLUSTERS_DETAILS = 3;
    private static final int SCREEN_CONFIG = 4;

    private int currentScreen = SCREEN_MAIN_MENU;
    private int selectedMenuIndex = 0;
    private final String[] menuOptions = {
        "1. Monitor Telemetry",
        "2. View Clusters & Services",
        "3. General Configurations",
        "4. Exit"
    };

    private int selectedClusterIndex = 0;
    private List<String> clusterNames = new ArrayList<>();
    private String selectedClusterName = "";
    private int selectClusterTargetScreen = SCREEN_TELEMETRY;

    // Dynamically fetched cluster configurations
    private boolean targetRequireToken;
    private int targetTimeoutMs;
    private String targetAllowedIps = "";
    private int targetRateLimitRequests;
    private int targetRateLimitDurationSeconds;

    // Dynamically fetched engine global configurations
    private int globalMaxClusterSize;
    private int globalMaxWorkers;
    private int globalPingInterval;
    private String globalHttpVersion = "";

    private final String clusterName;
    private final String secret;
    private final int httpPort;
    private final Scanner scanner;
    private boolean running = true;
    private List<ServerNode> nodes = new ArrayList<>();

    public static void startTerminal(String clusterName, int httpPort) {
        new TerminalUI(clusterName, httpPort).run();
    }

    public static void startTerminal(String clusterName) {
        startTerminal(clusterName, 3001);
    }

    public TerminalUI(String clusterName, int httpPort) {
        this.clusterName = clusterName;
        this.secret = EnvLoader.get(clusterName, "secret", "watashi_secretKey");
        this.httpPort = httpPort;
        this.scanner = new Scanner(System.in);
    }

    private void run() {
        DebugUtils.setTuiModeActive(true);

        NativeTerminal.initTerminal();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            NativeTerminal.resetTerminal();
        }));

        try {
            long lastFetch = 0;
            boolean needRedraw = true;

            fetchClusterNames();

            while (running) {
                long now = System.currentTimeMillis();
                if (now - lastFetch >= 1200) {
                    if (currentScreen == SCREEN_TELEMETRY || currentScreen == SCREEN_CLUSTERS_DETAILS) {
                        fetchNodeStatus();
                    }
                    if (currentScreen == SCREEN_CLUSTERS_DETAILS) {
                        fetchClusterConfig(selectedClusterName);
                    }
                    if (currentScreen == SCREEN_CONFIG) {
                        fetchGlobalConfig();
                    }
                    if (currentScreen == SCREEN_SELECT_CLUSTER) {
                        fetchClusterNames();
                    }
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

    private void fetchClusterNames() {
        try {
            URL url = java.net.URI.create("http://localhost:" + httpPort + "/LIST_CLUSTERS").toURL();
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
                    List<String> names = new ArrayList<>();
                    for (String name : response.split(";")) {
                        if (!name.trim().isEmpty()) {
                            names.add(name.trim());
                        }
                    }
                    this.clusterNames = names;
                }
            }
        } catch (Exception e) {
            // Log ignored
        }
    }

    private void fetchClusterConfig(String name) {
        if (name == null || name.isEmpty()) return;
        try {
            URL url = java.net.URI.create("http://localhost:" + httpPort + "/clusters/" + name + "/GET_CLUSTER_CONFIG").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Cluster-Token", secret);
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine();
                in.close();

                if (response != null && !response.isEmpty()) {
                    for (String part : response.split(";")) {
                        if (part.contains("=")) {
                            String[] split = part.split("=");
                            String key = split[0];
                            String val = split.length > 1 ? split[1] : "";
                            switch (key) {
                                case "requireToken":
                                    this.targetRequireToken = Boolean.parseBoolean(val);
                                    break;
                                case "timeoutMs":
                                    this.targetTimeoutMs = Integer.parseInt(val);
                                    break;
                                case "allowedIps":
                                    this.targetAllowedIps = val;
                                    break;
                                case "rateLimitRequests":
                                    this.targetRateLimitRequests = Integer.parseInt(val);
                                    break;
                                case "rateLimitDurationSeconds":
                                    this.targetRateLimitDurationSeconds = Integer.parseInt(val);
                                    break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log ignored
        }
    }

    private void fetchGlobalConfig() {
        try {
            URL url = java.net.URI.create("http://localhost:" + httpPort + "/GET_GLOBAL_CONFIG").toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Cluster-Token", secret);
            conn.setConnectTimeout(800);
            conn.setReadTimeout(800);

            if (conn.getResponseCode() == 200) {
                BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String response = in.readLine();
                in.close();

                if (response != null && !response.isEmpty()) {
                    for (String part : response.split(";")) {
                        if (part.contains("=")) {
                            String[] split = part.split("=");
                            String key = split[0];
                            String val = split.length > 1 ? split[1] : "";
                            switch (key) {
                                case "maxClusterSize":
                                    this.globalMaxClusterSize = Integer.parseInt(val);
                                    break;
                                case "maxWorkers":
                                    this.globalMaxWorkers = Integer.parseInt(val);
                                    break;
                                case "pingInterval":
                                    this.globalPingInterval = Integer.parseInt(val);
                                    break;
                                case "httpVersion":
                                    this.globalHttpVersion = val;
                                    break;
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // Log ignored
        }
    }

    private void fetchNodeStatus() {
        if (selectedClusterName.isEmpty()) return;
        try {
            URL url = java.net.URI.create("http://localhost:" + httpPort + "/clusters/" + selectedClusterName + "/GET_NODES").toURL();
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
                    List<ServerNode> newNodes = new ArrayList<>();
                    for (String part : response.split(";")) {
                        if (part.contains("=")) {
                            String[] split = part.split("=");
                            String fullHost = split[0];
                            String state = split[1];
                            int lastColon = fullHost.lastIndexOf(':');
                            String host = fullHost;
                            int port = -1;
                            if (lastColon != -1) {
                                try {
                                    port = Integer.parseInt(fullHost.substring(lastColon + 1));
                                    host = fullHost.substring(0, lastColon);
                                } catch (NumberFormatException nfe) {
                                    port = -1;
                                }
                            }
                            NodeStatus ns = NodeStatus.OFFLINE;
                            try { ns = NodeStatus.valueOf(state); } catch (Exception ex) {}
                            newNodes.add(new ServerNode(host, port == -1 ? 0 : port, ns, false));
                        }
                    }
                    this.nodes = newNodes;
                } else {
                    this.nodes.clear();
                }
            }
        } catch (Exception e) {
            this.nodes.clear();
        }
    }

    private void drawDashboard() {
        NativeTerminal.clearScreen();

        NativeTerminal.printAt(1, 1, CYAN + "╔══════════════════════════════════════════════════════════════════════════════╗" + RESET);
        NativeTerminal.printAt(1, 2, CYAN + "║                         " + WHITE_BOLD + "HEXACLOUD TELEMETRY MONITOR" + CYAN + "                          ║" + RESET);
        NativeTerminal.printAt(1, 3, CYAN + "╚══════════════════════════════════════════════════════════════════════════════╝" + RESET);

        switch (currentScreen) {
            case SCREEN_MAIN_MENU:
                drawMainMenu();
                break;
            case SCREEN_SELECT_CLUSTER:
                drawSelectClusterScreen();
                break;
            case SCREEN_TELEMETRY:
                drawTelemetryScreen();
                break;
            case SCREEN_CLUSTERS_DETAILS:
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

    private void drawSelectClusterScreen() {
        NativeTerminal.printAt(3, 5, WHITE_BOLD + "Select Cluster to Manage:" + RESET);
        NativeTerminal.printAt(3, 6, "Use UP / DOWN arrow keys to navigate and ENTER to select.");

        int y = 9;
        for (int i = 0; i < clusterNames.size(); i++) {
            if (i == selectedClusterIndex) {
                NativeTerminal.printAt(5, y, CYAN + "➔   " + WHITE_BOLD + clusterNames.get(i) + RESET);
            } else {
                NativeTerminal.printAt(5, y, "    " + clusterNames.get(i));
            }
            y += 2;
        }

        if (selectedClusterIndex == clusterNames.size()) {
            NativeTerminal.printAt(5, y, CYAN + "➔   " + GREEN + "(Create New Cluster...)" + RESET);
        } else {
            NativeTerminal.printAt(5, y, "    " + GREEN + "(Create New Cluster...)" + RESET);
        }
        y += 3;

        NativeTerminal.printAt(3, y, CYAN + "[Q/ESC]" + RESET + " Back to Main Menu");
    }

    private void drawTelemetryScreen() {
        NativeTerminal.printAt(3, 5, WHITE_BOLD + "Active Cluster: " + RESET + selectedClusterName);
        NativeTerminal.printAt(3, 6, WHITE_BOLD + "Target Host:    " + RESET + "http://localhost:" + httpPort);
        NativeTerminal.printAt(40, 5, WHITE_BOLD + "Config File:    " + RESET + "resources/hexacloud.properties");
        NativeTerminal.printAt(40, 6, WHITE_BOLD + "Library C:      " + RESET + "libhexaterminal.so (Loaded)");

        NativeTerminal.printAt(3, 8, CYAN + "┌──────────────────────────────────────────────┬──────────────┬────────────────┐" + RESET);
        NativeTerminal.printAt(3, 9, CYAN + "│ " + WHITE_BOLD + "NODE ENDPOINT" + CYAN + "                                │ " + WHITE_BOLD + "PORT" + CYAN + "         │ " + WHITE_BOLD + "STATUS" + CYAN + "         │" + RESET);
        NativeTerminal.printAt(3, 10, CYAN + "├──────────────────────────────────────────────┼──────────────┼────────────────┤" + RESET);

        int y = 11;
        if (nodes.isEmpty()) {
            NativeTerminal.printAt(5, y, RED + "No nodes registered in cluster or Control Plane is unreachable." + RESET);
            y++;
        } else {
            for (ServerNode node : nodes) {
                String host = node.host();
                String statusText = node.status().name();
                String coloredStatus = GREEN + "ONLINE" + RESET;
                if (statusText.equals("OFFLINE")) {
                    coloredStatus = RED + "OFFLINE" + RESET;
                } else if (statusText.equals("UNSTABLE")) {
                    coloredStatus = YELLOW + "UNSTABLE" + RESET;
                }

                String portStr = node.port() == 0 ? "-" : String.valueOf(node.port());

                String line = String.format("│ %-44s │ %-12s │ %-23s │", host, portStr, coloredStatus);
                NativeTerminal.printAt(3, y, line);
                y++;
            }
        }

        NativeTerminal.printAt(3, y, CYAN + "└──────────────────────────────────────────────┴──────────────┴────────────────┘" + RESET);
        y += 2;

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
        NativeTerminal.printAt(3, 5, WHITE_BOLD + "Cluster Details: " + RESET + selectedClusterName);

        NativeTerminal.printAt(3, 7, WHITE_BOLD + "Access Security Configurations:" + RESET);
        NativeTerminal.printAt(5, 8, "API Token Validation:  " + (targetRequireToken ? GREEN + "Required" : YELLOW + "Optional") + RESET);
        NativeTerminal.printAt(5, 9, "Token Secret Key:      " + RED + "[HIDDEN / SECURED]" + RESET);
        NativeTerminal.printAt(5, 10, "Allowed IP Addresses:  " + CYAN + (targetAllowedIps.isEmpty() ? "Any IP Allowed" : targetAllowedIps) + RESET);
        NativeTerminal.printAt(5, 11, "Rate Limiting Policy:  " + YELLOW + targetRateLimitRequests + " requests / " + targetRateLimitDurationSeconds + "s" + RESET);
        NativeTerminal.printAt(5, 12, "Connection Timeout:    " + targetTimeoutMs + " ms");

        NativeTerminal.printAt(3, 14, WHITE_BOLD + "Registered Services & Nodes:" + RESET);
        int y = 15;
        if (nodes.isEmpty()) {
            NativeTerminal.printAt(5, y, "No services active in this cluster.");
            y++;
        } else {
            for (ServerNode node : nodes) {
                String coloredStatus = node.status().equals(NodeStatus.ONLINE) ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;
                if (node.status().equals(NodeStatus.UNSTABLE)) coloredStatus = YELLOW + "UNSTABLE" + RESET;
                
                NativeTerminal.printAt(5, y, "• Service host: " + node.host() + ":" + node.port() + " -> Status: " + coloredStatus);
                y++;
            }
        }

        NativeTerminal.printAt(3, y + 2, CYAN + "[Q/ESC]" + RESET + " Back to Main Menu");
    }

    private void drawConfigScreen() {
        NativeTerminal.printAt(3, 5, WHITE_BOLD + "Global GateBridge Configurations" + RESET);

        NativeTerminal.printAt(3, 7, WHITE_BOLD + "Gateway Server Port Configuration:" + RESET);
        NativeTerminal.printAt(5, 8, "Base Listening Port:     " + (httpPort - 1));
        NativeTerminal.printAt(5, 9, "HTTP Dashboard Port:     " + httpPort);
        NativeTerminal.printAt(5, 10, "WebSocket Port:          " + (httpPort + 1));

        NativeTerminal.printAt(3, 12, WHITE_BOLD + "Internal Engine Defaults:" + RESET);
        NativeTerminal.printAt(5, 13, "Max Capacity:            " + globalMaxClusterSize + " registered nodes per cluster");
        NativeTerminal.printAt(5, 14, "Max Handler Workers:     " + globalMaxWorkers + " Virtual Threads");
        NativeTerminal.printAt(5, 15, "Ping Scheduler Interval: " + globalPingInterval + " seconds");
        NativeTerminal.printAt(5, 16, "HTTP Protocol Version:   " + globalHttpVersion);
        NativeTerminal.printAt(5, 17, "Routing Strategy:        Dynamic Path Partitioning");

        NativeTerminal.printAt(3, 20, CYAN + "[Q/ESC]" + RESET + " Back to Main Menu");
    }

    private void handleKeyPress(int key) {
        if (currentScreen == SCREEN_MAIN_MENU) {
            if (key == 1000) { // UP
                selectedMenuIndex--;
                if (selectedMenuIndex < 0) {
                    selectedMenuIndex = menuOptions.length - 1;
                }
            } else if (key == 1001) { // DOWN
                selectedMenuIndex++;
                if (selectedMenuIndex >= menuOptions.length) {
                    selectedMenuIndex = 0;
                }
            } else if (key == 10 || key == 13) { // ENTER
                if (selectedMenuIndex == 0) {
                    selectClusterTargetScreen = SCREEN_TELEMETRY;
                    currentScreen = SCREEN_SELECT_CLUSTER;
                    selectedClusterIndex = 0;
                    fetchClusterNames();
                } else if (selectedMenuIndex == 1) {
                    selectClusterTargetScreen = SCREEN_CLUSTERS_DETAILS;
                    currentScreen = SCREEN_SELECT_CLUSTER;
                    selectedClusterIndex = 0;
                    fetchClusterNames();
                } else if (selectedMenuIndex == 2) {
                    currentScreen = SCREEN_CONFIG;
                    fetchGlobalConfig();
                } else if (selectedMenuIndex == 3) {
                    running = false;
                }
            } else if (key == 'q' || key == 'Q') {
                running = false;
            }
        } else if (currentScreen == SCREEN_SELECT_CLUSTER) {
            int maxOptions = clusterNames.size() + 1;
            if (key == 1000) { // UP
                selectedClusterIndex--;
                if (selectedClusterIndex < 0) {
                    selectedClusterIndex = maxOptions - 1;
                }
            } else if (key == 1001) { // DOWN
                selectedClusterIndex++;
                if (selectedClusterIndex >= maxOptions) {
                    selectedClusterIndex = 0;
                }
            } else if (key == 10 || key == 13) { // ENTER
                if (selectedClusterIndex == clusterNames.size()) {
                    createNewClusterPrompt();
                } else {
                    selectedClusterName = clusterNames.get(selectedClusterIndex);
                    currentScreen = selectClusterTargetScreen;
                    if (currentScreen == SCREEN_CLUSTERS_DETAILS) {
                        fetchClusterConfig(selectedClusterName);
                    }
                    fetchNodeStatus();
                }
            } else if (key == 27 || key == 'q' || key == 'Q') {
                currentScreen = SCREEN_MAIN_MENU;
            }
        } else {
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

    private void createNewClusterPrompt() {
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter unique name for new cluster: " + RESET);
        
        String name = scanner.next();
        if (name != null && !name.trim().isEmpty()) {
            registerClusterRequest(name.trim());
        } else {
            System.out.println(RED + "Invalid cluster name." + RESET);
            try { Thread.sleep(800); } catch (Exception e) {}
        }
        
        NativeTerminal.initTerminal();
        fetchClusterNames();
        selectedClusterIndex = 0;
    }

    private void registerClusterRequest(String name) {
        try {
            URL url = java.net.URI.create("http://localhost:" + httpPort + "/CREATE_CLUSTER?" + name).toURL();
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setRequestProperty("X-Cluster-Token", secret);
            conn.setConnectTimeout(2000);
            
            int status = conn.getResponseCode();
            if (status == 200) {
                System.out.println(GREEN + "SUCCESS: Created cluster '" + name + "'" + RESET);
            } else {
                System.out.println(RED + "ERROR: Creation failed with status code " + status + RESET);
            }
        } catch (Exception e) {
            System.out.println(RED + "ERROR: Could not communicate with server: " + e.getMessage() + RESET);
        }
        try { Thread.sleep(800); } catch (Exception e) {}
    }

    private void addNewNodePrompt() {
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter port to register new local node in " + selectedClusterName + ": " + RESET);
        
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
            URL url = java.net.URI.create("http://localhost:" + httpPort + "/clusters/" + selectedClusterName + "/REGISTER?" + port).toURL();
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
        sb.append("Cluster: ").append(selectedClusterName).append("\n");
        sb.append("Timestamp: ").append(new java.util.Date().toString()).append("\n\n");
        sb.append(String.format("%-40s | %-10s\n", "NODE ENDPOINT", "STATUS"));
        sb.append("--------------------------------------------------\n");
        for (ServerNode node : nodes) {
            sb.append(String.format("%-40s | %-10s\n", node.host() + ":" + node.port(), node.status()));
        }

        String filename = "hexacloud_report_" + selectedClusterName + ".txt";
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
}
