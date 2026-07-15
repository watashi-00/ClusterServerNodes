package hexacloud.core.tui.view;

import java.util.List;
import hexacloud.core.model.ServerNode;
import hexacloud.core.tui.TerminalUI;
import hexacloud.core.tui.TuiRenderer;
import hexacloud.core.tui.TuiState;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles visual rendering for the Node Configuration View.
 */
public class NodeConfigViewRenderer {
    private final TerminalUI tui;
    private final TuiRenderer mainRenderer;

    public NodeConfigViewRenderer(TerminalUI tui, TuiRenderer mainRenderer) {
        this.tui = tui;
        this.mainRenderer = mainRenderer;
    }

    public void draw() {
        TuiState state = tui.state();
        if (state.nodes.isEmpty() || state.selectedNodeIndex >= state.nodes.size()) {
            return;
        }

        ServerNode node = state.nodes.get(state.selectedNodeIndex);

        // Top half: configuration parameters
        mainRenderer.drawBox(2, 5, 110, 13, "NODE CONFIGURATION PANEL - " + node.getFullHost(), true);

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
            NativeTerminal.printAt(55, row, "│");
        }
        NativeTerminal.printAt(57, 6, WHITE_BOLD + "Live Telemetry Metrics:" + RESET);
        String runtimeDisplay = node.runtime().isEmpty() ? node.pingProtocol().getFriendlyName() : node.runtime();
        NativeTerminal.printAt(57, 7, "Runtime/Lang: " + CYAN + runtimeDisplay + RESET);
        
        String latencyStr = node.status().name().equals("ONLINE") ? node.latencyMs() + " ms" : "-";
        NativeTerminal.printAt(57, 8, "Latency(RTT): " + GREEN + latencyStr + RESET);
        
        String cpuStr = node.status().name().equals("ONLINE") ? String.format("%.1f %%", node.cpuUsage()) : "-";
        NativeTerminal.printAt(57, 9, "CPU Load:     " + YELLOW + cpuStr + RESET);
        
        String ramStr = node.status().name().equals("ONLINE") ? String.format("%.1f MB", node.ramUsage()) : "-";
        NativeTerminal.printAt(57, 10, "RAM Memory:   " + CYAN + ramStr + RESET);

        // Bottom half: console logs for this node/service
        mainRenderer.drawBox(2, 14, 110, 22, "CONSOLE LOGS FOR SERVICE " + node.getFullHost(), false);

        int y = 15;
        List<DebugUtils.LogEntry> serviceLogs = DebugUtils.getServiceLogs(state.selectedClusterName, node.getFullHost());
        if (serviceLogs.isEmpty()) {
            NativeTerminal.printAt(4, y, "No logs recorded for this service.");
        } else {
            int startIdx = Math.max(0, serviceLogs.size() - 7);
            for (int i = startIdx; i < serviceLogs.size(); i++) {
                DebugUtils.LogEntry entry = serviceLogs.get(i);
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
        controlsStr.append(" [Backspace] Console");
        if (canEdit) {
            controlsStr.append("  [P] Toggle Ping  [E] Change Path  [H] Header Name  [V] Value");
        }
        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + controlsStr.toString());
    }
}
