package hexacloud.core.tui;

import java.util.List;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles visual rendering for the main DevOps Dashboard View.
 */
class DashboardViewRenderer {
    private final TerminalUI tui;
    private final TuiRenderer mainRenderer;

    public DashboardViewRenderer(TerminalUI tui, TuiRenderer mainRenderer) {
        this.tui = tui;
        this.mainRenderer = mainRenderer;
    }

    public void draw() {
        TuiState state = tui.state();

        mainRenderer.drawBox(2, 5, 24, 13, "CLUSTERS (" + state.clusterNames.size() + ")", state.activePanel == PANEL_CLUSTERS);
        mainRenderer.drawBox(26, 5, 79, 13, "CLUSTER CONFIG & SERVICES", state.activePanel == PANEL_SERVICES);
        mainRenderer.drawBox(2, 14, 55, 22, "RECENT SYSTEM LOGS [L: Full Logs]", false);
        mainRenderer.drawBox(81, 5, 110, 13, "GATEWAYS & SYSTEM", false);
        mainRenderer.drawBox(57, 14, 110, 22, "RECENT EVENTS", false);

        int y = 6;
        if (state.clusterNames.isEmpty()) {
            NativeTerminal.printAt(4, y, RED + "No clusters." + RESET);
        } else {
            for (int i = 0; i < state.clusterNames.size(); i++) {
                if (y >= 13) break;
                String name = state.clusterNames.get(i);
                
                String displayName = name;
                String gwIndicator;
                if (tui.isGatewayActive(name)) {
                    RunningGatewayPort gw = tui.activeGateways().get(name);
                    int port = (gw != null) ? gw.getPort() : 3000;
                    String suffix = " [" + port + "]";
                    int maxLen = 16 - suffix.length();
                    if (displayName.length() > maxLen) {
                        displayName = displayName.substring(0, maxLen - 3) + "...";
                    }
                    displayName = displayName + suffix;
                    gwIndicator = GREEN + "●" + RESET;
                } else {
                    int maxLen = 16;
                    if (displayName.length() > maxLen) {
                        displayName = displayName.substring(0, maxLen - 3) + "...";
                    }
                    gwIndicator = RED + "○" + RESET;
                }
                
                if (i == state.selectedClusterIndex) {
                    NativeTerminal.printAt(4, y, CYAN + "➔ " + WHITE_BOLD + displayName + " " + gwIndicator + RESET);
                } else {
                    NativeTerminal.printAt(4, y, "  " + displayName + " " + gwIndicator + RESET);
                }
                y++;
            }
        }

        String ips = state.targetAllowedIps.isEmpty() ? "Any Client" : state.targetAllowedIps;
        if (ips.length() > 22) ips = ips.substring(0, 19) + "...";

        String gwStatus = tui.isGatewayActive(state.selectedClusterName) ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;

        NativeTerminal.printAt(28, 6, WHITE_BOLD + "Active:   " + RESET + state.selectedClusterName + " | Gateway: " + gwStatus);
        NativeTerminal.printAt(28, 7, "Security: " + (state.targetRequireToken ? GREEN + "Required (Key Hidden)" + RESET : YELLOW + "Optional" + RESET) + " | Allowed: " + CYAN + ips + RESET);
        NativeTerminal.printAt(28, 8, "Limits:   " + YELLOW + state.targetRateLimitRequests + " reqs / " + state.targetRateLimitDurationSeconds + "s" + RESET + " | Timeout: " + state.targetTimeoutMs + " ms");
        
        StringBuilder sep = new StringBuilder();
        for (int i = 27; i < 79; i++) sep.append("─");
        NativeTerminal.printAt(26, 9, CYAN + "├" + sep.substring(1, sep.length() - 1) + "┤" + RESET);

        y = 10;
        NativeTerminal.printAt(28, 9, WHITE_BOLD + String.format("%-26s %-8s %-14s", "SERVICE HOST", "PORT", "STATUS") + RESET);
        y++;

        tui.adjustServicesViewport(2);

        if (state.nodes.isEmpty()) {
            NativeTerminal.printAt(28, y, RED + "No services registered." + RESET);
        } else {
            for (int i = 0; i < 2; i++) {
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
                NativeTerminal.printAt(28, y, line);
                y++;
            }
            if (state.servicesViewportStart > 0) {
                NativeTerminal.printAt(77, 10, WHITE_BOLD + "▲" + RESET);
            }
            if (state.servicesViewportStart + 2 < state.nodes.size()) {
                NativeTerminal.printAt(77, 12, WHITE_BOLD + "▼" + RESET);
            }
        }

        y = 15;
        List<DebugUtils.LogEntry> dashboardLogs = DebugUtils.getDashboardLogs();
        if (dashboardLogs.isEmpty()) {
            NativeTerminal.printAt(4, y, "No logs recorded yet.");
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
                    NativeTerminal.printAt(4, y, RED + clearedLine + RESET);
                } else if (entry.getLevel().equals("INFO")) {
                    NativeTerminal.printAt(4, y, CYAN + clearedLine + RESET);
                } else {
                    NativeTerminal.printAt(4, y, clearedLine);
                }
                y++;
            }
        }

        // Render GATEWAYS & SYSTEM Live Metrics
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
        } catch (Throwable t) {}
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

        // Render RECENT EVENTS
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

        StringBuilder controlsStr = new StringBuilder();
        controlsStr.append(" [Tab] Focus  [Enter] Console");
        if (tui.gatewayManagementEnabled() && !tui.readOnly()) controlsStr.append("  [G] Gateway");
        if (tui.clusterManagementEnabled() && !tui.readOnly()) controlsStr.append("  [C] New Cluster");
        controlsStr.append("  [L] Logs  [Q] Exit");
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + controlsStr.toString());
    }
}
