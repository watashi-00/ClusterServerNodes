package hexacloud.core.tui.view;

import java.util.List;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.tui.TerminalUI;
import hexacloud.core.tui.TuiRenderer;
import hexacloud.core.tui.TuiState;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles visual rendering for the main DevOps Dashboard View.
 */
public class DashboardViewRenderer {
    private final TerminalUI tui;
    private final TuiRenderer mainRenderer;
    private static boolean cpuErrorLogged = false;
    private static boolean threadErrorLogged = false;

    public DashboardViewRenderer(TerminalUI tui, TuiRenderer mainRenderer) {
        this.tui = tui;
        this.mainRenderer = mainRenderer;
    }

    public void draw() {
        TuiState state = tui.state();

        // 1. Draw all panels/boxes
        mainRenderer.drawBox(2, 5, 24, 9, "CLUSTERS (" + state.clusterNames.size() + ")", state.activePanel == PANEL_CLUSTERS);
        mainRenderer.drawBox(2, 10, 24, 13, "GATEWAYS (" + state.gateways.size() + ")", state.activePanel == PANEL_GATEWAYS);
        
        boolean isGatewayFocused = state.activePanel == PANEL_GATEWAYS;
        String middleTitle = isGatewayFocused ? "GATEWAY CONFIG & TRANSPORTS" : "CLUSTER CONFIG & SERVICES";
        mainRenderer.drawBox(26, 5, 79, 13, middleTitle, state.activePanel == PANEL_SERVICES || state.activePanel == PANEL_GATEWAYS);
        
        mainRenderer.drawBox(2, 14, 55, 22, "RECENT SYSTEM LOGS [L: Full Logs]", false);
        mainRenderer.drawBox(81, 5, 110, 13, "GATEWAYS & SYSTEM", false);
        mainRenderer.drawBox(57, 14, 110, 22, "RECENT EVENTS", false);

        // 2. Render CLUSTERS list
        int yCluster = 6;
        if (state.clusterNames.isEmpty()) {
            NativeTerminal.printAt(4, yCluster, RED + "No clusters." + RESET);
        } else {
            for (int i = 0; i < state.clusterNames.size(); i++) {
                if (yCluster >= 9) break;
                String name = state.clusterNames.get(i);
                hexacloud.core.cluster.Cluster c = hexacloud.core.cluster.ClusterRegistry.getInstance().getCluster(name);
                int nodeCount = (c != null) ? c.getCluster().size() : 0;
                String displayName = name + " (" + nodeCount + ")";
                if (displayName.length() > 18) {
                    displayName = displayName.substring(0, 15) + "...";
                }
                String clearedLine = displayName + "      ";
                if (clearedLine.length() > 20) clearedLine = clearedLine.substring(0, 20);

                if (i == state.selectedClusterIndex && state.activePanel == PANEL_CLUSTERS) {
                    NativeTerminal.printAt(4, yCluster, CYAN + "➔ " + WHITE_BOLD + clearedLine + RESET);
                } else if (i == state.selectedClusterIndex) {
                    NativeTerminal.printAt(4, yCluster, GRAY + "➔ " + RESET + clearedLine);
                } else {
                    NativeTerminal.printAt(4, yCluster, "  " + clearedLine);
                }
                yCluster++;
            }
        }
        for (int r = yCluster; r < 9; r++) {
            NativeTerminal.printAt(4, r, "                     ");
        }

        // 3. Render GATEWAYS list
        int yGateway = 11;
        if (state.gateways.isEmpty()) {
            NativeTerminal.printAt(4, yGateway, RED + "No gateways." + RESET);
        } else {
            for (int i = 0; i < state.gateways.size(); i++) {
                if (yGateway >= 13) break;
                TuiState.GatewayConfig gw = state.gateways.get(i);
                String statusIndicator = gw.running ? GREEN + "●" + RESET : RED + "○" + RESET;
                String displayName = gw.gatewayName + " (:" + gw.port + ")";
                if (displayName.length() > 14) {
                    displayName = displayName.substring(0, 11) + "...";
                }
                String clearedLine = displayName + "    ";
                if (clearedLine.length() > 16) clearedLine = clearedLine.substring(0, 16);

                if (i == state.selectedGatewayIndex && state.activePanel == PANEL_GATEWAYS) {
                    NativeTerminal.printAt(4, yGateway, CYAN + "➔ " + WHITE_BOLD + clearedLine + " " + statusIndicator + RESET);
                } else if (i == state.selectedGatewayIndex) {
                    NativeTerminal.printAt(4, yGateway, GRAY + "➔ " + RESET + clearedLine + " " + statusIndicator);
                } else {
                    NativeTerminal.printAt(4, yGateway, "  " + clearedLine + " " + statusIndicator);
                }
                yGateway++;
            }
        }
        for (int r = yGateway; r < 13; r++) {
            NativeTerminal.printAt(4, r, "                     ");
        }

        // 4. Render Middle Config & Services/Transports Table
        if (isGatewayFocused) {
            TuiState.GatewayConfig gw = !state.gateways.isEmpty() && state.selectedGatewayIndex < state.gateways.size()
                ? state.gateways.get(state.selectedGatewayIndex) : null;
            String targetCluster = (gw != null && !gw.clusterName.isEmpty()) ? gw.clusterName : "None";
            String gwStatusStr = (gw != null && gw.running) ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;
            int port = (gw != null) ? gw.port : 3000;
            int pingInt = (gw != null) ? gw.pingInterval : 5;
            String gwName = (gw != null) ? gw.gatewayName : "None";

            NativeTerminal.printAt(28, 6, WHITE_BOLD + "Gateway:  " + RESET + gwName + " (:" + port + ") | Target: " + targetCluster + RESET);
            NativeTerminal.printAt(28, 7, "Status:   " + gwStatusStr + " | Ping Interval: " + YELLOW + pingInt + "s" + RESET);
            
            StringBuilder sep = new StringBuilder();
            for (int i = 27; i < 79; i++) sep.append("─");
            NativeTerminal.printAt(26, 8, CYAN + "├" + sep.substring(1, sep.length() - 1) + "┤" + RESET);

            NativeTerminal.printAt(28, 9, WHITE_BOLD + String.format("%-26s %-8s %-14s", "PROTOCOL / TRANSPORT", "PORT", "STATUS") + RESET);

            int yGw = 10;
            if (gw != null) {
                String telnetStatus = (gw.running && gw.telnetEnabled) ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;
                String httpStatus = (gw.running && gw.httpEnabled) ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;
                String wsStatus = (gw.running && gw.wsEnabled) ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;

                NativeTerminal.printAt(28, yGw++, String.format("  %-26s %-8d %-14s", "Telnet Console (CLI)", port, telnetStatus));
                NativeTerminal.printAt(28, yGw++, String.format("  %-26s %-8d %-14s", "HTTP REST API (JSON)", port + 1, httpStatus));
                NativeTerminal.printAt(28, yGw++, String.format("  %-26s %-8d %-14s", "WebSocket Stream (JSON)", port + 2, wsStatus));
            } else {
                NativeTerminal.printAt(28, yGw++, RED + "No gateway selected." + RESET);
            }
            for (int r = yGw; r <= 12; r++) {
                NativeTerminal.printAt(28, r, "                                                    ");
            }
        } else {
            String ips = state.targetAllowedIps.isEmpty() ? "Any Client" : state.targetAllowedIps;
            if (ips.length() > 22) ips = ips.substring(0, 19) + "...";

            String gwStatus = tui.isGatewayActive(state.selectedClusterName) ? GREEN + "ONLINE" : RED + "OFFLINE";
            RunningGatewayPort activeGw = tui.activeGateways().get(state.selectedClusterName);
            String portSuffix = (activeGw != null) ? " (:" + activeGw.getPort() + ")" : "";

            NativeTerminal.printAt(28, 6, WHITE_BOLD + "Active:   " + RESET + state.selectedClusterName + " | Gateway: " + gwStatus + portSuffix + RESET);
            NativeTerminal.printAt(28, 7, "Security: " + (state.targetRequireToken ? GREEN + "Token Required" + RESET : YELLOW + "Optional" + RESET) + " | Allowed: " + CYAN + ips + RESET);
            
            StringBuilder sep = new StringBuilder();
            for (int i = 27; i < 79; i++) sep.append("─");
            NativeTerminal.printAt(26, 8, CYAN + "├" + sep.substring(1, sep.length() - 1) + "┤" + RESET);

            NativeTerminal.printAt(28, 9, WHITE_BOLD + String.format("%-26s %-8s %-14s", "SERVICE HOST", "PORT", "STATUS") + RESET);

            tui.adjustServicesViewport(3);

            int yNode = 10;
            if (state.nodes.isEmpty()) {
                NativeTerminal.printAt(28, yNode, RED + "No services registered." + RESET);
                yNode++;
            } else {
                for (int i = 0; i < 3; i++) {
                    int index = state.servicesViewportStart + i;
                    if (index >= state.nodes.size()) break;

                    ServerNode node = state.nodes.get(index);
                    String statusText = node.status().name();
                    String coloredStatus = GREEN + "ONLINE" + RESET;
                    if (statusText.equals("OFFLINE")) {
                        coloredStatus = RED + "OFFLINE" + RESET;
                    } else if (statusText.equals("UNSTABLE")) {
                        coloredStatus = YELLOW + "UNSTABLE" + RESET;
                    }

                    String portStr = node.port() == 0 ? "-" : String.valueOf(node.port());
                    String prefix = "  ";
                    if (index == state.selectedNodeIndex && state.activePanel == PANEL_SERVICES) {
                        prefix = "➔ ";
                    }

                    String hostStr = node.host() + (node.runtime().isEmpty() ? "" : " [" + node.runtime() + "]");
                    String statusLabel = coloredStatus + (node.status().name().equals("ONLINE") ? " (" + node.latencyMs() + "ms)" : "");

                    String line = String.format("%s%-26s %-8s %-14s", prefix, hostStr, portStr, statusLabel);
                    NativeTerminal.printAt(28, yNode, line);
                    yNode++;
                }
                if (state.servicesViewportStart > 0) {
                    NativeTerminal.printAt(77, 9, WHITE_BOLD + "▲" + RESET);
                }
                if (state.servicesViewportStart + 3 < state.nodes.size()) {
                    NativeTerminal.printAt(77, 12, WHITE_BOLD + "▼" + RESET);
                }
            }
            for (int r = yNode; r <= 12; r++) {
                NativeTerminal.printAt(28, r, "                                                    ");
            }
        }

        // 5. Render RECENT SYSTEM LOGS
        int yLog = 15;
        List<DebugUtils.LogEntry> dashboardLogs = DebugUtils.getDashboardLogs();
        if (dashboardLogs.isEmpty()) {
            NativeTerminal.printAt(4, yLog, "No logs recorded yet.");
        } else {
            int startIdx = Math.max(0, dashboardLogs.size() - 7);
            for (int i = startIdx; i < dashboardLogs.size(); i++) {
                DebugUtils.LogEntry entry = dashboardLogs.get(i);
                String logLine = entry.toString();
                String clearedLine = logLine + "                                                    ";
                if (clearedLine.length() > 50) {
                    clearedLine = clearedLine.substring(0, 50);
                }
                if (entry.getLevel().equals("ERROR")) {
                    NativeTerminal.printAt(4, yLog, RED + clearedLine + RESET);
                } else if (entry.getLevel().equals("INFO")) {
                    NativeTerminal.printAt(4, yLog, CYAN + clearedLine + RESET);
                } else {
                    NativeTerminal.printAt(4, yLog, clearedLine);
                }
                yLog++;
            }
        }

        // 6. Render GATEWAYS & SYSTEM Live Metrics
        NativeTerminal.printAt(83, 6, WHITE_BOLD + "SYSTEM RESOURCES" + RESET);
        long usedMem = (Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()) / (1024 * 1024);
        long allocatedMem = Runtime.getRuntime().totalMemory() / (1024 * 1024);
        long maxMem = Runtime.getRuntime().maxMemory() / (1024 * 1024);
        
        NativeTerminal.printAt(83, 7, "RAM Used:   " + CYAN + usedMem + " MB" + RESET);
        NativeTerminal.printAt(83, 8, "RAM Alloc:  " + CYAN + allocatedMem + " MB" + RESET);
        NativeTerminal.printAt(83, 9, "RAM Max:    " + CYAN + maxMem + " MB" + RESET);

        double cpu = -1;
        try {
            java.lang.management.OperatingSystemMXBean osBean = java.lang.management.ManagementFactory.getOperatingSystemMXBean();
            if (osBean instanceof com.sun.management.OperatingSystemMXBean sunBean) {
                cpu = sunBean.getProcessCpuLoad() * 100;
            }
        } catch (Throwable t) {
            if (!cpuErrorLogged) {
                DebugUtils.error("TUI", null, "Failed to retrieve CPU load metrics", t);
                cpuErrorLogged = true;
            }
        }
        String cpuStr = cpu >= 0 ? String.format("%.1f %%", cpu) : "N/A";
        NativeTerminal.printAt(83, 10, "CPU Load:   " + YELLOW + cpuStr + RESET);

        int threads = java.lang.management.ManagementFactory.getThreadMXBean().getThreadCount();
        int appThreads = 0;
        try {
            java.util.Set<Thread> threadSet = Thread.getAllStackTraces().keySet();
            for (Thread t : threadSet) {
                String name = t.getName();
                if (!(t.isDaemon() && (name.contains("ForkJoinPool") || name.contains("VirtualThread-unblocker") ||
                    name.equals("Reference Handler") || name.equals("Finalizer") || 
                    name.equals("Signal Dispatcher") || name.equals("Notification Thread") || 
                    name.equals("Common-Cleaner") || name.equals("Attach Listener")))) {
                    appThreads++;
                }
            }
        } catch (Throwable t) {
            if (!threadErrorLogged) {
                DebugUtils.error("TUI", null, "Failed to retrieve OS threads metrics", t);
                threadErrorLogged = true;
            }
            appThreads = 1;
        }
        NativeTerminal.printAt(83, 11, "OS Threads: " + CYAN + threads + RESET + " (App: " + CYAN + appThreads + RESET + ")");

        int gwCount = tui.activeGateways().size();
        String gwSummary = "Gateways:   " + (gwCount == 0 ? RED + "None" + RESET : GREEN + String.valueOf(gwCount) + RESET);
        if (gwCount > 0) {
            int firstPort = tui.activeGateways().values().iterator().next().getPort();
            gwSummary += " (:" + firstPort + ")";
        }
        String summaryPadding = " ".repeat(Math.max(0, 26 - gwSummary.replaceAll("\u001B\\[[;\\d]*m", "").length()));
        NativeTerminal.printAt(83, 12, gwSummary + summaryPadding);

        // 7. Render RECENT EVENTS
        int eventY = 15;
        if (state.recentEvents.isEmpty()) {
            NativeTerminal.printAt(59, eventY, GRAY + "No recent events." + RESET);
            eventY++;
        } else {
            for (TuiState.TuiEvent event : state.recentEvents) {
                if (eventY >= 22) break;

                long diffMs = System.currentTimeMillis() - event.timestamp();
                String timeAgo;
                if (diffMs < 1000) {
                    timeAgo = "now";
                } else if (diffMs < 60000) {
                    timeAgo = (diffMs / 1000) + "s";
                } else if (diffMs < 3600000) {
                    timeAgo = (diffMs / 60000) + "m";
                } else {
                    timeAgo = (diffMs / 3600000) + "h";
                }

                String timeStr = "[" + timeAgo + "] ";
                int remaining = 50 - timeStr.length();
                
                String shortName;
                switch (event.type()) {
                    case "NodeStatusChanged": shortName = "Status"; break;
                    case "NodeTelemetryUpdated": shortName = "Telemetry"; break;
                    case "NodeEventSubmitted": shortName = "NodeEvent"; break;
                    case "NodeRegistered": shortName = "NodeReg"; break;
                    case "NodeDeregistered": shortName = "NodeDereg"; break;
                    case "ClusterRegistered": shortName = "ClusterReg"; break;
                    case "DeveloperCustomEvent":
                    case "UserCustomEvent": shortName = "CustomEvent"; break;
                    default: 
                        shortName = event.type();
                        if (shortName.length() > 15) shortName = shortName.substring(0, 15);
                }

                String eventText = shortName + (event.detail().isEmpty() ? "" : ": " + event.detail());
                if (eventText.length() > remaining) {
                    eventText = eventText.substring(0, remaining - 3) + "...";
                }

                String color = YELLOW;
                if (event.type().contains("Deregistered") || event.type().contains("Dereg")) {
                    color = RED;
                } else if (event.type().contains("Registered") || event.type().contains("Reg")) {
                    color = GREEN;
                } else if (event.type().contains("Custom")) {
                    color = MAGENTA;
                } else if (event.type().contains("NodeEvent")) {
                    color = MAGENTA;
                } else if (event.type().contains("Telemetry")) {
                    color = CYAN;
                }

                String colorized = timeStr + color + eventText + RESET;
                int printedLen = timeStr.length() + eventText.length();
                String padding = " ".repeat(Math.max(0, 50 - printedLen));
                NativeTerminal.printAt(59, eventY, colorized + padding);
                eventY++;
            }
        }
        for (int row = eventY; row < 22; row++) {
            NativeTerminal.printAt(59, row, "                                                  ");
        }

        // 8. Render bottom controls
        StringBuilder controlsStr = new StringBuilder();
        controlsStr.append(" [Tab] Focus");
        if (state.activePanel == PANEL_GATEWAYS) {
            if (!tui.readOnly()) {
                controlsStr.append("  [G] Toggle GW  [A] Route Cluster  [T] Telnet  [H] HTTP  [W] WS");
            }
        } else {
            controlsStr.append("  [Enter] Console");
            if (tui.gatewayManagementEnabled() && !tui.readOnly()) controlsStr.append("  [G] Gateway");
            if (tui.clusterManagementEnabled() && !tui.readOnly()) controlsStr.append("  [C] New Cluster");
        }
        controlsStr.append("  [L] Logs  [Q] Exit");
        
        // Clear row 23 first to prevent visual residue
        NativeTerminal.printAt(2, 23, "                                                                                                              ");
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + controlsStr.toString());
    }
}
