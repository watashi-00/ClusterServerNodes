package hexacloud.application;

import java.util.ArrayList;
import java.util.List;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import hexacloud.core.utils.TerminalScanner;
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

    private static final int VIEW_DASHBOARD = 0;
    private static final int VIEW_CLUSTER_DETAIL = 1;
    private static final int VIEW_FULL_LOGS = 2;
    private static final int VIEW_NODE_CONFIG = 3;

    private int currentView = VIEW_DASHBOARD;
    private int activePanel = PANEL_CLUSTERS;
    private int selectedClusterIndex = 0;
    private int selectedNodeIndex = 0;
    private int selectedLogIndex = 0;

    private int servicesViewportStart = 0;
    private int logViewportStart = 0;

    private List<String> clusterNames = new ArrayList<>();
    private String selectedClusterName = "";

    // Dynamically fetched cluster configurations
    private boolean targetRequireToken;
    private int targetTimeoutMs;
    private String targetAllowedIps = "";
    private int targetRateLimitRequests;
    private int targetRateLimitDurationSeconds;

    // Dynamically fetched engine global configurations
    private int globalPingInterval;

    private final String displayName;
    private boolean running = true;
    private List<ServerNode> nodes = new ArrayList<>();

    private static final java.util.Map<String, hexacloud.core.ports.GatewayPort> activeGateways = new java.util.concurrent.ConcurrentHashMap<>();

    public static void startTerminal(String displayName) {
        new TerminalUI(displayName).run();
    }

    public static void startTerminal(String displayName, hexacloud.core.ports.GatewayPort gateway) {
        if (gateway != null) {
            activeGateways.put(gateway.getClusterName(), gateway);
        }
        new TerminalUI(displayName).run();
    }

    public TerminalUI(String displayName) {
        this.displayName = displayName != null ? displayName : "GateBridge Control Plane";
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
        this.globalPingInterval = hexacloud.core.config.ClusterConfig.DEFAULT_PING_INTERVAL_SECONDS;
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

    private void drawHeader(String viewTitle) {
        String boxColor = CYAN;
        NativeTerminal.printAt(1, 1, boxColor + "╔══════════════════════════════════════════════════════════════════════════════╗" + RESET);
        
        int width = 78;
        int padding = Math.max(0, (width - viewTitle.length()) / 2);
        StringBuilder sb = new StringBuilder();
        sb.append("║");
        for (int i = 0; i < padding; i++) sb.append(" ");
        sb.append(WHITE_BOLD).append(viewTitle).append(boxColor);
        for (int i = 0; i < width - padding - viewTitle.length(); i++) sb.append(" ");
        sb.append("║");
        
        NativeTerminal.printAt(1, 2, boxColor + sb.toString() + RESET);
        NativeTerminal.printAt(1, 3, boxColor + "╚══════════════════════════════════════════════════════════════════════════════╝" + RESET);
    }

    private void drawDashboard() {
        NativeTerminal.clearScreen();

        if (currentView == VIEW_DASHBOARD) {
            drawHeader(displayName);
            renderDashboardView();
        } else if (currentView == VIEW_CLUSTER_DETAIL) {
            drawHeader(selectedClusterName + " - Cluster Console");
            renderClusterDetailView();
        } else if (currentView == VIEW_FULL_LOGS) {
            drawHeader("Detailed System Logs");
            renderFullLogsView();
        } else if (currentView == VIEW_NODE_CONFIG) {
            drawHeader("Node Config Panel");
            renderNodeConfigView();
        }
    }

    private void renderDashboardView() {
        // Safe 79-column layout
        drawBox(2, 5, 24, 17, "CLUSTERS (" + clusterNames.size() + ")", activePanel == PANEL_CLUSTERS);
        drawBox(26, 5, 79, 17, "CLUSTER CONFIG & SERVICES", activePanel == PANEL_SERVICES);
        drawBox(2, 18, 79, 22, "RECENT SYSTEM LOGS [L: Full Logs]", false);

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

        // Right Panel - Top Portion (Compact Configs)
        String ips = targetAllowedIps.isEmpty() ? "Any Client" : targetAllowedIps;
        if (ips.length() > 22) ips = ips.substring(0, 19) + "...";

        hexacloud.core.ports.GatewayPort gw = activeGateways.get(selectedClusterName);
        String gwStatus = gw != null ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;

        NativeTerminal.printAt(28, 6, WHITE_BOLD + "Active:   " + RESET + selectedClusterName + " | Gateway: " + gwStatus);
        NativeTerminal.printAt(28, 7, "Security: " + (targetRequireToken ? GREEN + "Required (Key Hidden)" + RESET : YELLOW + "Optional" + RESET) + " | Allowed: " + CYAN + ips + RESET);
        NativeTerminal.printAt(28, 8, "Limits:   " + YELLOW + targetRateLimitRequests + " reqs / " + targetRateLimitDurationSeconds + "s" + RESET + " | Timeout: " + targetTimeoutMs + " ms");
        
        // Separator line
        StringBuilder sep = new StringBuilder();
        for (int i = 27; i < 79; i++) sep.append("─");
        NativeTerminal.printAt(26, 9, CYAN + "├" + sep.substring(1, sep.length() - 1) + "┤" + RESET);

        // Right Panel - Bottom Portion (Services list with scrolling viewport)
        y = 10;
        NativeTerminal.printAt(28, y, WHITE_BOLD + String.format("%-26s %-10s %-12s", "SERVICE HOST", "PORT", "STATUS") + RESET);
        y++;

        adjustServicesViewport(6); // 6 visible rows for services

        if (nodes.isEmpty()) {
            NativeTerminal.printAt(28, y, RED + "No services registered." + RESET);
        } else {
            for (int i = 0; i < 6; i++) {
                int index = servicesViewportStart + i;
                if (index >= nodes.size()) break;

                ServerNode node = nodes.get(index);
                String statusText = node.status().name();
                String coloredStatus = GREEN + "ONLINE" + RESET;
                if (statusText.equals("OFFLINE")) {
                    coloredStatus = RED + "OFFLINE" + RESET;
                } else if (statusText.equals("UNSTABLE")) {
                    coloredStatus = YELLOW + "UNSTABLE" + RESET;
                }

                String portStr = node.port() == 0 ? "-" : String.valueOf(node.port());
                String prefix = "  ";
                if (index == selectedNodeIndex && activePanel == PANEL_SERVICES) {
                    prefix = "➔ ";
                }

                String line = String.format("%s%-24s %-10s %-12s", prefix, node.host(), portStr, coloredStatus);
                NativeTerminal.printAt(28, y, line);
                y++;
            }
            
            // Show scroll indicators if needed
            if (servicesViewportStart > 0) {
                NativeTerminal.printAt(77, 10, WHITE_BOLD + "▲" + RESET);
            }
            if (servicesViewportStart + 6 < nodes.size()) {
                NativeTerminal.printAt(77, 16, WHITE_BOLD + "▼" + RESET);
            }
        }

        // Recent System Logs (last 3 lines)
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
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + " [Tab] Focus  [Enter] Console  [G] Gateway  [C] New Cluster  [L] Logs  [Q] Exit");
    }

    private void renderClusterDetailView() {
        // Detailed panel layout
        drawBox(2, 5, 29, 12, "POLICIES & LIMITS", false);
        drawBox(31, 5, 79, 12, "SERVICES / TELEMETRY (" + nodes.size() + ")", true);
        drawBox(2, 13, 79, 22, "CONSOLE LOGS FOR " + selectedClusterName, false);

        // Top Left - Policies summary
        NativeTerminal.printAt(4, 6, WHITE_BOLD + "Active:   " + RESET + selectedClusterName);
        NativeTerminal.printAt(4, 7, "Security: " + (targetRequireToken ? GREEN + "Token Required" + RESET : YELLOW + "Optional" + RESET));
        String ips = targetAllowedIps.isEmpty() ? "Any Client Allowed" : targetAllowedIps;
        if (ips.length() > 14) ips = ips.substring(0, 11) + "...";
        NativeTerminal.printAt(4, 8, "Allowed:  " + CYAN + ips + RESET);
        NativeTerminal.printAt(4, 9, "Limits:   " + YELLOW + targetRateLimitRequests + " reqs / " + targetRateLimitDurationSeconds + "s" + RESET);
        NativeTerminal.printAt(4, 10, "Timeout:  " + targetTimeoutMs + " ms");
        NativeTerminal.printAt(4, 11, "Ping Int: " + globalPingInterval + "s");

        // Top Right - Services with scrolling viewport (5 visible rows)
        int y = 6;
        NativeTerminal.printAt(33, y, WHITE_BOLD + String.format("%-22s %-8s %-10s", "SERVICE HOST", "PORT", "STATUS") + RESET);
        y++;

        adjustServicesViewport(5);

        if (nodes.isEmpty()) {
            NativeTerminal.printAt(33, y, RED + "No services registered." + RESET);
        } else {
            for (int i = 0; i < 5; i++) {
                int index = servicesViewportStart + i;
                if (index >= nodes.size()) break;

                ServerNode node = nodes.get(index);
                String statusText = node.status().name();
                String coloredStatus = GREEN + "ONLINE" + RESET;
                if (statusText.equals("OFFLINE")) {
                    coloredStatus = RED + "OFFLINE" + RESET;
                } else if (statusText.equals("UNSTABLE")) {
                    coloredStatus = YELLOW + "UNSTABLE" + RESET;
                }

                String portStr = node.port() == 0 ? "-" : String.valueOf(node.port());
                String prefix = index == selectedNodeIndex ? "➔ " : "  ";

                String line = String.format("%s%-20s %-8s %-10s", prefix, node.host(), portStr, coloredStatus);
                NativeTerminal.printAt(33, y, line);
                y++;
            }
            if (servicesViewportStart > 0) {
                NativeTerminal.printAt(77, 7, WHITE_BOLD + "▲" + RESET);
            }
            if (servicesViewportStart + 5 < nodes.size()) {
                NativeTerminal.printAt(77, 11, WHITE_BOLD + "▼" + RESET);
            }
        }

        // Bottom - Filtered logs for this cluster (up to 8 lines)
        y = 14;
        List<String> filteredLogs = getFilteredLogs(selectedClusterName);
        if (filteredLogs.isEmpty()) {
            NativeTerminal.printAt(4, y, "No logs recorded for this cluster.");
        } else {
            int startIdx = Math.max(0, filteredLogs.size() - 8);
            for (int i = startIdx; i < filteredLogs.size(); i++) {
                String logLine = filteredLogs.get(i);
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

        // Help controls inside Console view
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + " [Backspc] Back  [Enter] Node Config  [G] Gateway  [A] Add  [D] Delete  [I] IPs  [T] Timeout");
    }

    private void renderFullLogsView() {
        drawBox(2, 5, 79, 22, "DETAILED SYSTEM LOGS", true);

        List<String> logs = DebugUtils.getRecentLogs();
        int y = 6;
        
        adjustLogsViewport(logs.size(), 16);

        if (logs.isEmpty()) {
            NativeTerminal.printAt(4, y, "No logs recorded yet.");
        } else {
            for (int i = 0; i < 16; i++) {
                int index = logViewportStart + i;
                if (index >= logs.size()) break;

                String logLine = logs.get(index);
                String prefix = index == selectedLogIndex ? "➔ " : "  ";
                String clearedLine = prefix + logLine + "                                                                                ";
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
            if (logViewportStart > 0) {
                NativeTerminal.printAt(77, 6, WHITE_BOLD + "▲" + RESET);
            }
            if (logViewportStart + 16 < logs.size()) {
                NativeTerminal.printAt(77, 21, WHITE_BOLD + "▼" + RESET);
            }
        }

        // Logs Help controls
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + " [Backspace] Back to Dashboard  [UP/DOWN] Scroll logs");
    }

    private void renderNodeConfigView() {
        if (nodes.isEmpty() || selectedNodeIndex >= nodes.size()) {
            currentView = VIEW_CLUSTER_DETAIL;
            return;
        }

        ServerNode node = nodes.get(selectedNodeIndex);

        // Draw a central config card
        drawBox(10, 6, 70, 18, "NODE CONFIGURATION PANEL", true);

        NativeTerminal.printAt(13, 8, WHITE_BOLD + "Node Host Address: " + RESET + node.host());
        NativeTerminal.printAt(13, 9, WHITE_BOLD + "Port Number:       " + RESET + node.port());
        
        String coloredStatus = GREEN + "ONLINE" + RESET;
        if (node.status().name().equals("OFFLINE")) {
            coloredStatus = RED + "OFFLINE" + RESET;
        } else if (node.status().name().equals("UNSTABLE")) {
            coloredStatus = YELLOW + "UNSTABLE" + RESET;
        }
        NativeTerminal.printAt(13, 10, WHITE_BOLD + "Current Status:    " + RESET + coloredStatus);

        NativeTerminal.printAt(13, 12, WHITE_BOLD + "[P] Ping Monitoring:  " + RESET + (node.pingEnabled() ? GREEN + "Enabled" + RESET : RED + "Disabled" + RESET));
        NativeTerminal.printAt(13, 13, WHITE_BOLD + "[E] Ping Path Route:  " + RESET + CYAN + node.pingPath() + RESET);
        
        String headerName = node.pingHeaderName() == null ? "None" : node.pingHeaderName();
        String headerVal = node.pingHeaderValue() == null ? "None" : node.pingHeaderValue();
        NativeTerminal.printAt(13, 14, WHITE_BOLD + "[H] Auth Header Name: " + RESET + CYAN + headerName + RESET);
        NativeTerminal.printAt(13, 15, WHITE_BOLD + "[V] Token Value:      " + RESET + CYAN + headerVal + RESET);

        NativeTerminal.printAt(13, 17, WHITE_BOLD + "Press [Backspace / Esc] to return to Cluster Console" + RESET);

        // Help controls at safe row 23
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + " [Backspace] Console  [P] Toggle Ping  [E] Change Path  [H] Header Name  [V] Value");
    }

    private List<String> getFilteredLogs(String clusterName) {
        List<String> allLogs = DebugUtils.getRecentLogs();
        List<String> filtered = new ArrayList<>();
        Cluster c = ClusterRegistry.getInstance().getCluster(clusterName);
        List<Integer> ports = new ArrayList<>();
        if (c != null) {
            for (ServerNode node : c.getCluster()) {
                ports.add(node.port());
            }
        }

        for (String log : allLogs) {
            boolean matches = log.contains("'" + clusterName + "'") || log.contains(" " + clusterName + " ");
            if (!matches) {
                for (int port : ports) {
                    if (log.contains(":" + port)) {
                        matches = true;
                        break;
                    }
                }
            }
            if (matches) {
                filtered.add(log);
            }
        }
        return filtered;
    }

    private void adjustServicesViewport(int visibleCount) {
        if (nodes.isEmpty()) {
            servicesViewportStart = 0;
            return;
        }
        if (selectedNodeIndex < 0) selectedNodeIndex = 0;
        if (selectedNodeIndex >= nodes.size()) selectedNodeIndex = nodes.size() - 1;
        
        if (selectedNodeIndex < servicesViewportStart) {
            servicesViewportStart = selectedNodeIndex;
        } else if (selectedNodeIndex >= servicesViewportStart + visibleCount) {
            servicesViewportStart = selectedNodeIndex - visibleCount + 1;
        }
    }

    private void adjustLogsViewport(int totalLogs, int visibleCount) {
        if (totalLogs == 0) {
            logViewportStart = 0;
            return;
        }
        if (selectedLogIndex < 0) selectedLogIndex = 0;
        if (selectedLogIndex >= totalLogs) selectedLogIndex = totalLogs - 1;
        
        if (selectedLogIndex < logViewportStart) {
            logViewportStart = selectedLogIndex;
        } else if (selectedLogIndex >= logViewportStart + visibleCount) {
            logViewportStart = selectedLogIndex - visibleCount + 1;
        }
    }

    private void handleKeyPress(int key) {
        if (currentView == VIEW_DASHBOARD) {
            handleKeyPressDashboard(key);
        } else if (currentView == VIEW_CLUSTER_DETAIL) {
            handleKeyPressClusterDetail(key);
        } else if (currentView == VIEW_FULL_LOGS) {
            handleKeyPressFullLogs(key);
        } else if (currentView == VIEW_NODE_CONFIG) {
            handleKeyPressNodeConfig(key);
        }
    }

    private void handleKeyPressDashboard(int key) {
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
        } else if (key == 10 || key == 13) { // Enter: Open Cluster Console Detail View
            if (!selectedClusterName.isEmpty()) {
                currentView = VIEW_CLUSTER_DETAIL;
                selectedNodeIndex = 0;
                servicesViewportStart = 0;
            }
        } else if (key == 'g' || key == 'G') {
            manageGatewayPrompt();
        } else if (key == 'c' || key == 'C') {
            createNewClusterPrompt();
        } else if (key == 'l' || key == 'L') {
            currentView = VIEW_FULL_LOGS;
            selectedLogIndex = DebugUtils.getRecentLogs().size() - 1;
            logViewportStart = 0;
        } else if (key == 'q' || key == 'Q' || key == 27) {
            running = false;
        }
    }

    private void handleKeyPressClusterDetail(int key) {
        if (key == 1000) { // UP Arrow
            selectedNodeIndex--;
            if (selectedNodeIndex < 0) {
                selectedNodeIndex = Math.max(0, nodes.size() - 1);
            }
        } else if (key == 1001) { // DOWN Arrow
            selectedNodeIndex++;
            if (selectedNodeIndex >= nodes.size()) {
                selectedNodeIndex = 0;
            }
        } else if (key == 10 || key == 13) { // Enter: Open Selected Node Config Screen
            if (!nodes.isEmpty()) {
                currentView = VIEW_NODE_CONFIG;
            }
        } else if (key == 'g' || key == 'G') {
            manageGatewayPrompt();
        } else if (key == 'a' || key == 'A') {
            addNewNodePrompt();
        } else if (key == 'd' || key == 'D') {
            if (!nodes.isEmpty()) {
                deregisterSelectedNode();
            }
        } else if (key == 'i' || key == 'I') {
            changeAllowedIpsPrompt();
        } else if (key == 't' || key == 'T') {
            changeTimeoutPrompt();
        } else if (key == 'l' || key == 'L') {
            changeRateLimitPrompt();
        } else if (key == 127 || key == 8 || key == 27) { // Backspace or Escape
            currentView = VIEW_DASHBOARD;
        }
    }

    private void handleKeyPressFullLogs(int key) {
        List<String> logs = DebugUtils.getRecentLogs();
        if (key == 1000) { // UP Arrow
            selectedLogIndex--;
            if (selectedLogIndex < 0) {
                selectedLogIndex = Math.max(0, logs.size() - 1);
            }
        } else if (key == 1001) { // DOWN Arrow
            selectedLogIndex++;
            if (selectedLogIndex >= logs.size()) {
                selectedLogIndex = 0;
            }
        } else if (key == 127 || key == 8 || key == 27) { // Backspace or Escape
            currentView = VIEW_DASHBOARD;
        }
    }

    private void handleKeyPressNodeConfig(int key) {
        if (nodes.isEmpty() || selectedNodeIndex >= nodes.size()) {
            currentView = VIEW_CLUSTER_DETAIL;
            return;
        }

        Cluster cluster = ClusterRegistry.getInstance().getCluster(selectedClusterName);
        if (cluster == null) return;

        ServerNode node = nodes.get(selectedNodeIndex);

        if (key == 127 || key == 8 || key == 27) { // Backspace or Escape
            currentView = VIEW_CLUSTER_DETAIL;
        } else if (key == 'p' || key == 'P') {
            ServerNode updated = new ServerNode(
                node.host(), node.port(), node.status(), node.isExternal(),
                !node.pingEnabled(), node.pingPath(), node.pingHeaderName(), node.pingHeaderValue()
            );
            cluster.updateServerNode(updated);
            fetchNodeStatus();
        } else if (key == 'e' || key == 'E') {
            changeNodePingPathPrompt(cluster, node);
        } else if (key == 'h' || key == 'H') {
            changeNodePingHeaderNamePrompt(cluster, node);
        } else if (key == 'v' || key == 'V') {
            changeNodePingHeaderValuePrompt(cluster, node);
        }
    }

    private void createNewClusterPrompt() {
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter unique name for new cluster (or /cancel to abort): " + RESET);
        String name = TerminalScanner.readLine();
        if (name.equalsIgnoreCase("/cancel") || name.isEmpty()) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
            try { Thread.sleep(800); } catch (Exception e) {}
        } else {
            ClusterRegistry.getInstance().createCluster(name);
            System.out.println(GREEN + "SUCCESS: Created cluster '" + name + "'" + RESET);
            System.out.print("Do you want to configure and start a gateway for this cluster now? (y/n): ");
            String ans = TerminalScanner.readLine();
            if (ans.equalsIgnoreCase("y")) {
                selectedClusterName = name;
                manageGatewayPrompt();
                return;
            } else {
                try { Thread.sleep(800); } catch (Exception e) {}
            }
        }
        NativeTerminal.initTerminal();
        fetchClusterNames();
        selectedClusterIndex = 0;
    }

    private void manageGatewayPrompt() {
        if (selectedClusterName.isEmpty()) {
            NativeTerminal.resetTerminal();
            System.out.println(YELLOW + "Please select or create a cluster first." + RESET);
            try { Thread.sleep(800); } catch (Exception e) {}
            NativeTerminal.initTerminal();
            return;
        }

        hexacloud.core.ports.GatewayPort currentGw = activeGateways.get(selectedClusterName);
        NativeTerminal.resetTerminal();
        System.out.println("\n" + WHITE_BOLD + "=== Gateway Setup for Cluster: " + selectedClusterName + " ===" + RESET);
        if (currentGw != null) {
            System.out.println("Status: " + GREEN + "ONLINE" + RESET);
            System.out.print("Do you want to STOP the gateway? (y/n) [/cancel]: ");
            String ans = TerminalScanner.readLine();
            if (ans.equalsIgnoreCase("y")) {
                currentGw.stop();
                activeGateways.remove(selectedClusterName);
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

                    hexacloud.core.ports.GatewayPort newGw = hexacloud.infra.gateway.GatewayFactory.createGateway(selectedClusterName)
                        .port(port)
                        .pingInterval(pingInt)
                        .enableTelnet(telnet)
                        .enableHttp(http)
                        .enableWs(ws)
                        .listen()
                        .startPingScheduler();

                    activeGateways.put(selectedClusterName, newGw);
                    System.out.println(GREEN + "SUCCESS: Gateway started successfully." + RESET);
                    try { Thread.sleep(1000); } catch (Exception e) {}
                } catch (Exception e) {
                    System.out.println(RED + "ERROR: Failed to start gateway: " + e.getMessage() + RESET);
                    try { Thread.sleep(1500); } catch (Exception ex) {}
                }
            }
        }
        NativeTerminal.initTerminal();
        fetchNodeStatus();
    }

    private void addNewNodePrompt() {
        if (selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter port to register new service node in " + selectedClusterName + " (or /cancel to abort): " + RESET);
        String input = TerminalScanner.readLine();
        if (input.equalsIgnoreCase("/cancel") || input.isEmpty()) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } else {
            try {
                int port = Integer.parseInt(input);
                Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
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
        System.out.print("\n" + CYAN + ">> Enter Allowed IPs (comma-separated, or /cancel to abort): " + RESET);
        String ips = TerminalScanner.readLine();
        if (ips.equalsIgnoreCase("/cancel")) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } else {
            Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
            if (c != null) {
                c.setAllowedIps(ips);
                System.out.println(GREEN + "SUCCESS: Allowed IPs updated." + RESET);
            }
        }
        try { Thread.sleep(800); } catch (Exception e) {}
        NativeTerminal.initTerminal();
        fetchClusterConfig(selectedClusterName);
    }

    private void changeTimeoutPrompt() {
        if (selectedClusterName.isEmpty()) return;
        NativeTerminal.resetTerminal();
        System.out.print("\n" + CYAN + ">> Enter Timeout in ms (or /cancel to abort): " + RESET);
        String input = TerminalScanner.readLine();
        if (input.equalsIgnoreCase("/cancel") || input.isEmpty()) {
            System.out.println(YELLOW + "Operation cancelled." + RESET);
        } else {
            try {
                int timeout = Integer.parseInt(input);
                Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
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
        fetchClusterConfig(selectedClusterName);
    }

    private void changeRateLimitPrompt() {
        if (selectedClusterName.isEmpty()) return;
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
                    Cluster c = ClusterRegistry.getInstance().getCluster(selectedClusterName);
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
        fetchClusterConfig(selectedClusterName);
    }

    private void changeNodePingPathPrompt(Cluster cluster, ServerNode node) {
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
        fetchNodeStatus();
    }

    private void changeNodePingHeaderNamePrompt(Cluster cluster, ServerNode node) {
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
        fetchNodeStatus();
    }

    private void changeNodePingHeaderValuePrompt(Cluster cluster, ServerNode node) {
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
        fetchNodeStatus();
    }
}
