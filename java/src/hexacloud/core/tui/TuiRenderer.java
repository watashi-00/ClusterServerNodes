package hexacloud.core.tui;

import java.util.List;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.ServerNode;
import hexacloud.core.ports.GatewayPort;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles all Terminal UI rendering, drawing screens, boxes, headers, lists, and dynamic controls.
 */
public class TuiRenderer {

    private final TerminalUI tui;

    public TuiRenderer(TerminalUI tui) {
        this.tui = tui;
    }

    public void draw() {
        NativeTerminal.clearScreen();
        TuiState state = tui.state();

        if (state.currentView == VIEW_DASHBOARD) {
            drawHeader(tui.displayName());
            renderDashboardView();
        } else if (state.currentView == VIEW_CLUSTER_DETAIL) {
            drawHeader(state.selectedClusterName + " - Cluster Console");
            renderClusterDetailView();
        } else if (state.currentView == VIEW_FULL_LOGS) {
            drawHeader("Detailed System Logs");
            renderFullLogsView();
        } else if (state.currentView == VIEW_NODE_CONFIG) {
            drawHeader("Node Config Panel");
            renderNodeConfigView();
        }
    }

    public void drawBox(int x1, int y1, int x2, int y2, String title, boolean highlighted) {
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

    private void renderDashboardView() {
        TuiState state = tui.state();

        drawBox(2, 5, 24, 13, "CLUSTERS (" + state.clusterNames.size() + ")", state.activePanel == PANEL_CLUSTERS);
        drawBox(26, 5, 79, 13, "CLUSTER CONFIG & SERVICES", state.activePanel == PANEL_SERVICES);
        drawBox(2, 14, 79, 22, "RECENT SYSTEM LOGS [L: Full Logs]", false);

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
                    GatewayPort gw = tui.activeGateways().get(name);
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
                String clearedLine = logLine + "                                                                                ";
                if (clearedLine.length() > 72) {
                    clearedLine = clearedLine.substring(0, 72);
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

        StringBuilder controlsStr = new StringBuilder();
        controlsStr.append(" [Tab] Focus  [Enter] Console");
        if (tui.gatewayManagementEnabled() && !tui.readOnly()) controlsStr.append("  [G] Gateway");
        if (tui.clusterManagementEnabled() && !tui.readOnly()) controlsStr.append("  [C] New Cluster");
        controlsStr.append("  [L] Logs  [Q] Exit");
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + controlsStr.toString());
    }

    private void renderClusterDetailView() {
        TuiState state = tui.state();

        drawBox(2, 5, 29, 13, "POLICIES & LIMITS", false);
        drawBox(31, 5, 79, 13, "SERVICES / TELEMETRY (" + state.nodes.size() + ")", true);
        drawBox(2, 14, 79, 22, "CONSOLE LOGS FOR " + state.selectedClusterName, false);

        NativeTerminal.printAt(4, 6, WHITE_BOLD + "Active:   " + RESET + state.selectedClusterName);
        NativeTerminal.printAt(4, 7, "Security: " + (state.targetRequireToken ? GREEN + "Token Required" + RESET : YELLOW + "Optional" + RESET));
        
        Cluster currentCluster = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
        String secretDisplay = (currentCluster != null && currentCluster.getSecret() != null && !currentCluster.getSecret().isEmpty()) ? currentCluster.getSecret() : "None";
        if (secretDisplay.length() > 14) secretDisplay = secretDisplay.substring(0, 11) + "...";
        NativeTerminal.printAt(4, 8, "Token:    " + CYAN + secretDisplay + RESET);

        String ips = state.targetAllowedIps.isEmpty() ? "Any Client Allowed" : state.targetAllowedIps;
        if (ips.length() > 14) ips = ips.substring(0, 11) + "...";
        NativeTerminal.printAt(4, 9, "Allowed:  " + CYAN + ips + RESET);
        NativeTerminal.printAt(4, 10, "Limits:   " + YELLOW + state.targetRateLimitRequests + " reqs / " + state.targetRateLimitDurationSeconds + "s" + RESET);
        NativeTerminal.printAt(4, 11, "Timeout:  " + state.targetTimeoutMs + " ms");
        NativeTerminal.printAt(4, 12, "Ping Int: " + state.globalPingInterval + "s");

        int y = 6;
        NativeTerminal.printAt(33, y, WHITE_BOLD + String.format("%-22s %-6s %-12s", "SERVICE HOST", "PORT", "STATUS") + RESET);
        y++;

        tui.adjustServicesViewport(6);

        if (state.nodes.isEmpty()) {
            NativeTerminal.printAt(33, y, RED + "No services registered." + RESET);
        } else {
            for (int i = 0; i < 6; i++) {
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
                String prefix = index == state.selectedNodeIndex ? "➔ " : "  ";

                String hostStr = node.host() + (node.runtime().isEmpty() ? "" : " [" + node.runtime() + "]");
                String statusLabel = coloredStatus + (node.status().name().equals("ONLINE") ? " (" + node.latencyMs() + "ms)" : "");

                String line = String.format("%s%-22s %-6s %-12s", prefix, hostStr, portStr, statusLabel);
                NativeTerminal.printAt(33, y, line);
                y++;
            }
            if (state.servicesViewportStart > 0) {
                NativeTerminal.printAt(77, 7, WHITE_BOLD + "▲" + RESET);
            }
            if (state.servicesViewportStart + 6 < state.nodes.size()) {
                NativeTerminal.printAt(77, 12, WHITE_BOLD + "▼" + RESET);
            }
        }

        y = 14;
        List<DebugUtils.LogEntry> filteredLogs = DebugUtils.getClusterLogs(state.selectedClusterName);
        if (filteredLogs.isEmpty()) {
            NativeTerminal.printAt(4, y, "No logs recorded for this cluster.");
        } else {
            int startIdx = Math.max(0, filteredLogs.size() - 8);
            for (int i = startIdx; i < filteredLogs.size(); i++) {
                DebugUtils.LogEntry entry = filteredLogs.get(i);
                String logLine = entry.toString();
                String clearedLine = logLine + "                                                                                ";
                if (clearedLine.length() > 72) {
                    clearedLine = clearedLine.substring(0, 72);
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

        StringBuilder controlsStr = new StringBuilder();
        controlsStr.append(" [Backspc] Back");
        if (tui.nodeConfigurationEnabled()) controlsStr.append("  [Enter] Node Config");
        if (tui.gatewayManagementEnabled() && !tui.readOnly()) controlsStr.append("  [G] Gateway");
        if (tui.nodeManagementEnabled() && !tui.readOnly()) controlsStr.append("  [A] Add  [D] Delete");
        if (!tui.readOnly()) {
            controlsStr.append("  [I] IPs  [T] Timeout");
            if (tui.tokenManagementEnabled()) {
                controlsStr.append("  [K] Token  [S] Secure");
            }
        }
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + controlsStr.toString());
    }

    private void renderFullLogsView() {
        TuiState state = tui.state();
        drawBox(2, 5, 79, 22, "DETAILED SYSTEM LOGS", true);

        List<DebugUtils.LogEntry> logs = DebugUtils.getAllLogs();
        int y = 6;
        
        tui.adjustLogsViewport(logs.size(), 16);

        if (logs.isEmpty()) {
            NativeTerminal.printAt(4, y, "No logs recorded yet.");
        } else {
            for (int i = 0; i < 16; i++) {
                int index = state.logViewportStart + i;
                if (index >= logs.size()) break;

                DebugUtils.LogEntry entry = logs.get(index);
                String logLine = entry.toString();
                String prefix = index == state.selectedLogIndex ? "➔ " : "  ";
                String clearedLine = prefix + logLine + "                                                                                ";
                if (clearedLine.length() > 72) {
                    clearedLine = clearedLine.substring(0, 72);
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
            if (state.logViewportStart > 0) {
                NativeTerminal.printAt(77, 6, WHITE_BOLD + "▲" + RESET);
            }
            if (state.logViewportStart + 16 < logs.size()) {
                NativeTerminal.printAt(77, 21, WHITE_BOLD + "▼" + RESET);
            }
        }

        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + " [Backspace] Back to Dashboard  [UP/DOWN] Scroll logs");
    }

    private void renderNodeConfigView() {
        TuiState state = tui.state();
        if (state.nodes.isEmpty() || state.selectedNodeIndex >= state.nodes.size()) {
            return;
        }

        ServerNode node = state.nodes.get(state.selectedNodeIndex);

        // Top half: configuration parameters
        drawBox(2, 5, 79, 13, "NODE CONFIGURATION PANEL - " + node.getFullHost(), true);

        NativeTerminal.printAt(4, 6, WHITE_BOLD + "Host:   " + RESET + node.host());
        NativeTerminal.printAt(4, 7, WHITE_BOLD + "Port:   " + RESET + node.port());
        
        String coloredStatus = GREEN + "ONLINE" + RESET;
        if (node.status().name().equals("OFFLINE")) {
            coloredStatus = RED + "OFFLINE" + RESET;
        } else if (node.status().name().equals("UNSTABLE")) {
            coloredStatus = YELLOW + "UNSTABLE" + RESET;
        }
        NativeTerminal.printAt(4, 8, WHITE_BOLD + "Status: " + RESET + coloredStatus);

        boolean canEdit = !tui.readOnly() && tui.nodeConfigurationEnabled();

        String protocolColor = GREEN;
        if (node.pingProtocol() == hexacloud.core.model.PingProtocol.NONE) {
            protocolColor = RED;
        } else if (node.pingProtocol() == hexacloud.core.model.PingProtocol.WEBSOCKET) {
            protocolColor = CYAN;
        } else if (node.pingProtocol() == hexacloud.core.model.PingProtocol.TCP) {
            protocolColor = YELLOW;
        } else if (node.pingProtocol() == hexacloud.core.model.PingProtocol.UDP) {
            protocolColor = CYAN;
        } else if (node.pingProtocol() == hexacloud.core.model.PingProtocol.GRPC) {
            protocolColor = WHITE_BOLD;
        }
        NativeTerminal.printAt(4, 9, (canEdit ? WHITE_BOLD + "[P] " : "") + "Ping Proto: " + RESET + protocolColor + node.pingProtocol() + RESET);
        NativeTerminal.printAt(4, 10, (canEdit ? WHITE_BOLD + "[E] " : "") + "Path/Route: " + RESET + CYAN + node.pingPath() + RESET);
        
        String headerName = node.pingHeaderName() == null ? "None" : node.pingHeaderName();
        String headerVal = node.pingHeaderValue() == null ? "None" : node.pingHeaderValue();
        NativeTerminal.printAt(4, 11, (canEdit ? WHITE_BOLD + "[H] " : "") + "Header: " + RESET + CYAN + headerName + RESET);
        NativeTerminal.printAt(4, 12, (canEdit ? WHITE_BOLD + "[V] " : "") + "Token:  " + RESET + CYAN + headerVal + RESET);

        // Sep & Right half: Live Telemetry
        for (int row = 6; row <= 12; row++) {
            NativeTerminal.printAt(40, row, "│");
        }
        NativeTerminal.printAt(42, 6, WHITE_BOLD + "Live Telemetry Metrics:" + RESET);
        String defaultLang = "HTTP";
        if (node.host().startsWith("ws://") || node.host().startsWith("wss://")) {
            defaultLang = "WebSocket";
        } else if (node.host().startsWith("tcp://")) {
            defaultLang = "TCP";
        }
        String runtimeDisplay = node.runtime().isEmpty() ? defaultLang : node.runtime();
        NativeTerminal.printAt(42, 7, "Runtime/Lang: " + CYAN + runtimeDisplay + RESET);
        
        String latencyStr = node.status().name().equals("ONLINE") ? node.latencyMs() + " ms" : "-";
        NativeTerminal.printAt(42, 8, "Latency(RTT): " + GREEN + latencyStr + RESET);
        
        String cpuStr = node.status().name().equals("ONLINE") ? String.format("%.1f %%", node.cpuUsage()) : "-";
        NativeTerminal.printAt(42, 9, "CPU Load:     " + YELLOW + cpuStr + RESET);
        
        String ramStr = node.status().name().equals("ONLINE") ? String.format("%.1f MB", node.ramUsage()) : "-";
        NativeTerminal.printAt(42, 10, "RAM Memory:   " + CYAN + ramStr + RESET);

        // Bottom half: console logs for this node/service
        drawBox(2, 14, 79, 22, "CONSOLE LOGS FOR SERVICE " + node.getFullHost(), false);

        int y = 15;
        List<DebugUtils.LogEntry> serviceLogs = DebugUtils.getServiceLogs(state.selectedClusterName, node.getFullHost());
        if (serviceLogs.isEmpty()) {
            NativeTerminal.printAt(4, y, "No logs recorded for this service.");
        } else {
            int startIdx = Math.max(0, serviceLogs.size() - 7);
            for (int i = startIdx; i < serviceLogs.size(); i++) {
                DebugUtils.LogEntry entry = serviceLogs.get(i);
                String logLine = entry.toString();
                String clearedLine = logLine + "                                                                                ";
                if (clearedLine.length() > 72) {
                    clearedLine = clearedLine.substring(0, 72);
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

        StringBuilder controlsStr = new StringBuilder();
        controlsStr.append(" [Backspace] Console");
        if (canEdit) {
            controlsStr.append("  [P] Toggle Ping  [E] Change Path  [H] Header Name  [V] Value");
        }
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + controlsStr.toString());
    }
}
