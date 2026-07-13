package hexacloud.application;

import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import hexacloud.core.model.ServerNode;

public class TerminalUI {

    private static final String RESET = "\033[0m";
    private static final String GREEN = "\033[32m";
    private static final String RED = "\033[31m";
    private static final String YELLOW = "\033[33m";
    private static final String CYAN = "\033[36m";
    private static final String WHITE_BOLD = "\033[1;37m";

    private static final int PANEL_CLUSTERS = 0;
    private static final int PANEL_SERVICES = 1;

    private int activePanel = PANEL_CLUSTERS;
    private int selectedClusterIndex = 0;
    private int selectedNodeIndex = 0;

    private List<String> clusterNames = new ArrayList<>();
    private String selectedClusterName = "";

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

    private final String displayName;
    private final Scanner scanner;
    private boolean running = true;
    private List<ServerNode> nodes = new ArrayList<>();

    public static void startTerminal(String displayName) {
        new TerminalUI(displayName).run();
    }

    public TerminalUI(String displayName) {
        this.displayName = displayName != null ? displayName : "GateBridge Control Plane";
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

            // Fetch initial configuration & clusters list
            fetchClusterNames();
            fetchGlobalConfig();
            if (!clusterNames.isEmpty()) {
                selectedClusterName = clusterNames.get(0);
                fetchNodeStatus();
                fetchClusterConfig(selectedClusterName);
            }

            while (running) {
                long now = System.currentTimeMillis();
                if (now - lastFetch >= 1200) {
                    fetchClusterNames();
                    if (!selectedClusterName.isEmpty()) {
                        fetchNodeStatus();
                        fetchClusterConfig(selectedClusterName);
                    }
                    fetchGlobalConfig();
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
        List<String> names = new ArrayList<>();
        for (Cluster c : ClusterRegistry.getInstance().getClusters()) {
            names.add(c.getClusterName());
        }
        this.clusterNames = names;
    }

    private void fetchClusterConfig(String name) {
        if (name == null || name.isEmpty()) return;
        Cluster c = ClusterRegistry.getInstance().getCluster(name);
        if (c != null) {
            this.targetRequireToken = c.isRequireToken();
            this.targetTimeoutMs = c.getTimeoutMs();
            this.targetAllowedIps = c.getAllowedIps();
            this.targetRateLimitRequests = c.getRateLimitRequests();
            this.targetRateLimitDurationSeconds = c.getRateLimitDurationSeconds();
        }
    }

    private void fetchGlobalConfig() {
        this.globalMaxClusterSize = hexacloud.core.config.ClusterConfig.MAX_CLUSTER_SIZE;
        this.globalMaxWorkers = hexacloud.core.config.ClusterConfig.MAX_WORKERS;
        this.globalPingInterval = hexacloud.core.config.ClusterConfig.DEFAULT_PING_INTERVAL_SECONDS;
        this.globalHttpVersion = hexacloud.core.config.ClusterConfig.HTTP_VERSION.name();
    }

    private void fetchNodeStatus() {
        if (selectedClusterName.isEmpty()) return;
        Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
        if (c != null) {
            this.nodes = c.getCluster();
        } else {
            this.nodes.clear();
        }
    }

    private void drawBox(int x1, int y1, int x2, int y2, String title, boolean highlighted) {
        String boxColor = highlighted ? WHITE_BOLD : CYAN;
        
        StringBuilder horizontal = new StringBuilder();
        for (int i = x1 + 1; i < x2; i++) horizontal.append("─");
        
        NativeTerminal.printAt(x1, y1, boxColor + "┌" + horizontal + "┐" + RESET);
        NativeTerminal.printAt(x1, y2, boxColor + "└" + horizontal + "┘" + RESET);
        
        for (int y = y1 + 1; y < y2; y++) {
            NativeTerminal.printAt(x1, y, boxColor + "│" + RESET);
            NativeTerminal.printAt(x2, y, boxColor + "│" + RESET);
        }
        
        if (title != null && !title.isEmpty()) {
            String titleStr = " " + title + " ";
            NativeTerminal.printAt(x1 + 2, y1, boxColor + "┤" + WHITE_BOLD + titleStr + boxColor + "├" + RESET);
        }
    }

    private void drawHeader() {
        String boxColor = CYAN;
        NativeTerminal.printAt(1, 1, boxColor + "╔══════════════════════════════════════════════════════════════════════════════╗" + RESET);
        
        int width = 78;
        int padding = Math.max(0, (width - displayName.length()) / 2);
        StringBuilder sb = new StringBuilder();
        sb.append("║");
        for (int i = 0; i < padding; i++) sb.append(" ");
        sb.append(WHITE_BOLD).append(displayName).append(boxColor);
        for (int i = 0; i < width - padding - displayName.length(); i++) sb.append(" ");
        sb.append("║");
        
        NativeTerminal.printAt(1, 2, boxColor + sb.toString() + RESET);
        NativeTerminal.printAt(1, 3, boxColor + "╚══════════════════════════════════════════════════════════════════════════════╝" + RESET);
    }

    private void drawDashboard() {
        NativeTerminal.clearScreen();
        drawHeader();

        // Render Panels inside a safe 78-column layout
        drawBox(2, 5, 23, 17, "CLUSTERS (" + clusterNames.size() + ")", activePanel == PANEL_CLUSTERS);
        drawBox(25, 5, 78, 11, "SERVICES / TELEMETRY (" + nodes.size() + ")", activePanel == PANEL_SERVICES);
        drawBox(25, 12, 78, 17, "POLICIES & LIMITS", false);
        drawBox(2, 18, 78, 22, "RECENT SYSTEM LOGS", false);

        // Left Panel (Clusters list)
        int y = 6;
        if (clusterNames.isEmpty()) {
            NativeTerminal.printAt(4, y, RED + "No clusters." + RESET);
        } else {
            for (int i = 0; i < clusterNames.size(); i++) {
                if (y >= 17) break;
                String name = clusterNames.get(i);
                if (i == selectedClusterIndex) {
                    NativeTerminal.printAt(4, y, CYAN + "➔ " + WHITE_BOLD + name + RESET);
                } else {
                    NativeTerminal.printAt(4, y, "  " + name);
                }
                y++;
            }
        }

        // Right Top Panel (Services list)
        y = 6;
        NativeTerminal.printAt(27, y, WHITE_BOLD + String.format("%-26s %-10s %-12s", "SERVICE HOST", "PORT", "STATUS") + RESET);
        y++;
        if (nodes.isEmpty()) {
            NativeTerminal.printAt(27, y, RED + "No services registered in this cluster." + RESET);
        } else {
            for (int i = 0; i < nodes.size(); i++) {
                if (y >= 11) break;
                ServerNode node = nodes.get(i);
                String statusText = node.status().name();
                String coloredStatus = GREEN + "ONLINE" + RESET;
                if (statusText.equals("OFFLINE")) {
                    coloredStatus = RED + "OFFLINE" + RESET;
                } else if (statusText.equals("UNSTABLE")) {
                    coloredStatus = YELLOW + "UNSTABLE" + RESET;
                }

                String portStr = node.port() == 0 ? "-" : String.valueOf(node.port());
                String prefix = "  ";
                if (i == selectedNodeIndex && activePanel == PANEL_SERVICES) {
                    prefix = "➔ ";
                }

                String line = String.format("%s%-24s %-10s %-12s", prefix, node.host(), portStr, coloredStatus);
                NativeTerminal.printAt(27, y, line);
                y++;
            }
        }

        // Right Bottom Panel (Policies & Configs)
        NativeTerminal.printAt(27, 13, WHITE_BOLD + "Active Cluster:  " + RESET + selectedClusterName + " (Capacity: " + globalMaxClusterSize + " nodes, Ping: " + globalPingInterval + "s)");
        NativeTerminal.printAt(27, 14, "Token Security:  " + (targetRequireToken ? GREEN + "Required (Token Key Hidden)" + RESET : YELLOW + "Optional" + RESET) + " | Protocol: " + globalHttpVersion);
        NativeTerminal.printAt(27, 15, "Allowed IP list: " + CYAN + (targetAllowedIps.isEmpty() ? "Any Client Allowed" : targetAllowedIps) + RESET + " | Workers: " + globalMaxWorkers + " VT");
        NativeTerminal.printAt(27, 16, "Limits & Window: " + YELLOW + targetRateLimitRequests + " reqs / " + targetRateLimitDurationSeconds + "s" + RESET + "    Timeout: " + targetTimeoutMs + " ms");

        // Logs Panel
        y = 19;
        List<String> logs = DebugUtils.getRecentLogs();
        if (logs.isEmpty()) {
            NativeTerminal.printAt(4, y, "No logs recorded yet.");
        } else {
            int startIdx = Math.max(0, logs.size() - 3);
            for (int i = startIdx; i < logs.size(); i++) {
                String logLine = logs.get(i);
                String clearedLine = logLine + "                                                                                ";
                if (clearedLine.length() > 72) {
                    clearedLine = clearedLine.substring(0, 72);
                }
                if (logLine.contains("[ERROR]")) {
                    NativeTerminal.printAt(4, y, RED + clearedLine + RESET);
                } else if (logLine.contains("[INFO]")) {
                    NativeTerminal.printAt(4, y, CYAN + clearedLine + RESET);
                } else {
                    NativeTerminal.printAt(4, y, clearedLine);
                }
                y++;
            }
        }

        // Help controls at safe row 23
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + " [Tab] Focus  [A] Add Node  [D] Delete Node  [C] New Cluster  [I] IPs  [T] Timeout  [L] Limit  [Q] Exit");
    }

    private void handleKeyPress(int key) {
        if (key == 9) { // Tab: Switch Focus
            activePanel = (activePanel == PANEL_CLUSTERS) ? PANEL_SERVICES : PANEL_CLUSTERS;
        } else if (key == 1000) { // UP Arrow
            if (activePanel == PANEL_CLUSTERS) {
                selectedClusterIndex--;
                if (selectedClusterIndex < 0) {
                    selectedClusterIndex = Math.max(0, clusterNames.size() - 1);
                }
                if (!clusterNames.isEmpty()) {
                    selectedClusterName = clusterNames.get(selectedClusterIndex);
                    selectedNodeIndex = 0;
                    fetchNodeStatus();
                    fetchClusterConfig(selectedClusterName);
                }
            } else {
                selectedNodeIndex--;
                if (selectedNodeIndex < 0) {
                    selectedNodeIndex = Math.max(0, nodes.size() - 1);
                }
            }
        } else if (key == 1001) { // DOWN Arrow
            if (activePanel == PANEL_CLUSTERS) {
                selectedClusterIndex++;
                if (selectedClusterIndex >= clusterNames.size()) {
                    selectedClusterIndex = 0;
                }
                if (!clusterNames.isEmpty()) {
                    selectedClusterName = clusterNames.get(selectedClusterIndex);
                    selectedNodeIndex = 0;
                    fetchNodeStatus();
                    fetchClusterConfig(selectedClusterName);
                }
            } else {
                selectedNodeIndex++;
                if (selectedNodeIndex >= nodes.size()) {
                    selectedNodeIndex = 0;
                }
            }
        } else if (key == 'a' || key == 'A') {
            addNewNodePrompt();
        } else if (key == 'd' || key == 'D') {
            if (activePanel == PANEL_SERVICES && !nodes.isEmpty()) {
                deregisterSelectedNode();
            }
        } else if (key == 'c' || key == 'C') {
            createNewClusterPrompt();
        } else if (key == 'i' || key == 'I') {
            changeAllowedIpsPrompt();
        } else if (key == 't' || key == 'T') {
            changeTimeoutPrompt();
        } else if (key == 'l' || key == 'L') {
            changeRateLimitPrompt();
        } else if (key == 'r' || key == 'R') {
            fetchClusterNames();
            if (!selectedClusterName.isEmpty()) {
                fetchNodeStatus();
                fetchClusterConfig(selectedClusterName);
            }
        } else if (key == 'q' || key == 'Q' || key == 27) {
            running = false;
        }
    }

    private void createNewClusterPrompt() {
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter unique name for new cluster: " + RESET);
        String name = scanner.next();
        if (name != null && !name.trim().isEmpty()) {
            ClusterRegistry.getInstance().createCluster(name.trim());
            System.out.println(GREEN + "SUCCESS: Created cluster '" + name.trim() + "'" + RESET);
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        fetchClusterNames();
        selectedClusterIndex = 0;
    }

    private void addNewNodePrompt() {
        if (selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter port to register new service node in " + selectedClusterName + ": " + RESET);
        if (scanner.hasNextInt()) {
            int port = scanner.nextInt();
            Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
            if (c != null) {
                c.registerServer(port);
                System.out.println(GREEN + "SUCCESS: Registered service node." + RESET);
            }
        } else {
            System.out.println(RED + "Invalid port format." + RESET);
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        fetchNodeStatus();
    }

    private void deregisterSelectedNode() {
        Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
        if (c != null && !nodes.isEmpty()) {
            ServerNode node = nodes.get(selectedNodeIndex);
            c.deregisterServer(node.getFullHost());
            fetchNodeStatus();
            selectedNodeIndex = 0;
        }
    }

    private void changeAllowedIpsPrompt() {
        if (selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter Allowed IPs (comma-separated, empty for all): " + RESET);
        scanner.nextLine();
        String ips = scanner.nextLine();
        Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
        if (c != null) {
            c.setAllowedIps(ips.trim());
            System.out.println(GREEN + "SUCCESS: Allowed IPs updated." + RESET);
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        fetchClusterConfig(selectedClusterName);
    }

    private void changeTimeoutPrompt() {
        if (selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter Timeout (in ms): " + RESET);
        if (scanner.hasNextInt()) {
            int timeout = scanner.nextInt();
            Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
            if (c != null) {
                c.setTimeoutMs(timeout);
                System.out.println(GREEN + "SUCCESS: Timeout updated." + RESET);
            }
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        fetchClusterConfig(selectedClusterName);
    }

    private void changeRateLimitPrompt() {
        if (selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter Rate Limit (format: <requests> <durationSeconds>): " + RESET);
        scanner.nextLine();
        String line = scanner.nextLine();
        try {
            String[] parts = line.trim().split(" ");
            if (parts.length >= 2) {
                int requests = Integer.parseInt(parts[0]);
                int duration = Integer.parseInt(parts[1]);
                Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
                if (c != null) {
                    c.setRateLimit(requests, duration);
                    System.out.println(GREEN + "SUCCESS: Rate limit updated." + RESET);
                }
            }
        } catch (Exception e) {
            System.out.println(RED + "ERROR: Update failed." + RESET);
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        fetchClusterConfig(selectedClusterName);
    }
}
