package hexacloud.core.tui.view;

import java.util.List;
import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.ServerNode;
import hexacloud.core.tui.TerminalUI;
import hexacloud.core.tui.TuiRenderer;
import hexacloud.core.tui.TuiState;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles visual rendering for the Cluster Detail View.
 */
public class ClusterDetailViewRenderer {
    private final TerminalUI tui;
    private final TuiRenderer mainRenderer;

    public ClusterDetailViewRenderer(TerminalUI tui, TuiRenderer mainRenderer) {
        this.tui = tui;
        this.mainRenderer = mainRenderer;
    }

    public void draw() {
        TuiState state = tui.state();

        mainRenderer.drawBox(2, 5, 29, 13, "POLICIES & LIMITS", false);
        mainRenderer.drawBox(31, 5, 110, 13, "SERVICES / TELEMETRY (" + state.nodes.size() + ")", true);
        mainRenderer.drawBox(2, 14, 110, 22, "CONSOLE LOGS FOR " + state.selectedClusterName, false);

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
                NativeTerminal.printAt(108, 7, WHITE_BOLD + "▲" + RESET);
            }
            if (state.servicesViewportStart + 6 < state.nodes.size()) {
                NativeTerminal.printAt(108, 12, WHITE_BOLD + "▼" + RESET);
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
                String clearedLine = logLine + "                                                                                                    ";
                if (clearedLine.length() > 103) {
                    clearedLine = clearedLine.substring(0, 103);
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
}
