package hexacloud.core.tui;

import java.util.List;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.ServerNode;
import hexacloud.core.utils.DebugUtils;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles keyboard input routing and command dispatching based on active UI views.
 */
public class TuiKeyHandler {

    private final TerminalUI tui;

    public TuiKeyHandler(TerminalUI tui) {
        this.tui = tui;
    }

    public void handleKeyPress(int key) {
        TuiState state = tui.state();
        if (state.currentView == VIEW_DASHBOARD) {
            handleKeyPressDashboard(key);
        } else if (state.currentView == VIEW_CLUSTER_DETAIL) {
            handleKeyPressClusterDetail(key);
        } else if (state.currentView == VIEW_FULL_LOGS) {
            handleKeyPressFullLogs(key);
        } else if (state.currentView == VIEW_NODE_CONFIG) {
            handleKeyPressNodeConfig(key);
        }
    }

    private void handleKeyPressDashboard(int key) {
        TuiState state = tui.state();
        if (key == 9) { // Tab: Switch Focus
            state.activePanel = (state.activePanel == PANEL_CLUSTERS) ? PANEL_SERVICES : PANEL_CLUSTERS;
        } else if (key == 1000) { // UP Arrow
            if (state.activePanel == PANEL_CLUSTERS) {
                state.selectedClusterIndex--;
                if (state.selectedClusterIndex < 0) {
                    state.selectedClusterIndex = Math.max(0, state.clusterNames.size() - 1);
                }
                if (!state.clusterNames.isEmpty()) {
                    state.selectedClusterName = state.clusterNames.get(state.selectedClusterIndex);
                    state.selectedNodeIndex = 0;
                    tui.fetchNodeStatus();
                    tui.fetchClusterConfig(state.selectedClusterName);
                }
            } else {
                state.selectedNodeIndex--;
                if (state.selectedNodeIndex < 0) {
                    state.selectedNodeIndex = Math.max(0, state.nodes.size() - 1);
                }
            }
        } else if (key == 1001) { // DOWN Arrow
            if (state.activePanel == PANEL_CLUSTERS) {
                state.selectedClusterIndex++;
                if (state.selectedClusterIndex >= state.clusterNames.size()) {
                    state.selectedClusterIndex = 0;
                }
                if (!state.clusterNames.isEmpty()) {
                    state.selectedClusterName = state.clusterNames.get(state.selectedClusterIndex);
                    state.selectedNodeIndex = 0;
                    tui.fetchNodeStatus();
                    tui.fetchClusterConfig(state.selectedClusterName);
                }
            } else {
                state.selectedNodeIndex++;
                if (state.selectedNodeIndex >= state.nodes.size()) {
                    state.selectedNodeIndex = 0;
                }
            }
        } else if (key == 10 || key == 13) { // Enter: Open Cluster Console Detail View
            if (!state.selectedClusterName.isEmpty()) {
                state.currentView = VIEW_CLUSTER_DETAIL;
                state.selectedNodeIndex = 0;
                state.servicesViewportStart = 0;
            }
        } else if ((key == 'g' || key == 'G') && tui.gatewayManagementEnabled() && !tui.readOnly()) {
            tui.prompts().manageGatewayPrompt();
        } else if ((key == 'c' || key == 'C') && tui.clusterManagementEnabled() && !tui.readOnly()) {
            tui.prompts().createNewClusterPrompt();
        } else if (key == 'l' || key == 'L') {
            state.currentView = VIEW_FULL_LOGS;
            state.selectedLogIndex = DebugUtils.getAllLogs().size() - 1;
            state.logViewportStart = 0;
        } else if (key == 'q' || key == 'Q' || key == 27) {
            state.running = false;
        }
    }

    private void handleKeyPressClusterDetail(int key) {
        TuiState state = tui.state();
        if (key == 1000) { // UP Arrow
            state.selectedNodeIndex--;
            if (state.selectedNodeIndex < 0) {
                state.selectedNodeIndex = Math.max(0, state.nodes.size() - 1);
            }
        } else if (key == 1001) { // DOWN Arrow
            state.selectedNodeIndex++;
            if (state.selectedNodeIndex >= state.nodes.size()) {
                state.selectedNodeIndex = 0;
            }
        } else if (key == 10 || key == 13) { // Enter: Open Selected Node Config Screen
            if (!state.nodes.isEmpty() && tui.nodeConfigurationEnabled()) {
                state.currentView = VIEW_NODE_CONFIG;
            }
        } else if ((key == 'g' || key == 'G') && tui.gatewayManagementEnabled() && !tui.readOnly()) {
            tui.prompts().manageGatewayPrompt();
        } else if ((key == 'a' || key == 'A') && tui.nodeManagementEnabled() && !tui.readOnly()) {
            tui.prompts().addNewNodePrompt();
        } else if ((key == 'd' || key == 'D') && tui.nodeManagementEnabled() && !tui.readOnly()) {
            if (!state.nodes.isEmpty()) {
                deregisterSelectedNode();
            }
        } else if ((key == 'i' || key == 'I') && !tui.readOnly()) {
            tui.prompts().changeAllowedIpsPrompt();
        } else if ((key == 't' || key == 'T') && !tui.readOnly()) {
            tui.prompts().changeTimeoutPrompt();
        } else if ((key == 'l' || key == 'L') && !tui.readOnly()) {
            tui.prompts().changeRateLimitPrompt();
        } else if ((key == 'k' || key == 'K') && !tui.readOnly() && tui.tokenManagementEnabled()) {
            tui.prompts().changeSecretPrompt();
        } else if ((key == 's' || key == 'S') && !tui.readOnly() && tui.tokenManagementEnabled()) {
            Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
            if (c != null) {
                c.setRequireToken(!c.isRequireToken());
                tui.fetchClusterConfig(state.selectedClusterName);
            }
        } else if (key == 127 || key == 8 || key == 27) { // Backspace or Escape
            state.currentView = VIEW_DASHBOARD;
        }
    }

    private void handleKeyPressFullLogs(int key) {
        TuiState state = tui.state();
        List<DebugUtils.LogEntry> logs = DebugUtils.getAllLogs();
        if (key == 1000) { // UP Arrow
            state.selectedLogIndex--;
            if (state.selectedLogIndex < 0) {
                state.selectedLogIndex = Math.max(0, logs.size() - 1);
            }
        } else if (key == 1001) { // DOWN Arrow
            state.selectedLogIndex++;
            if (state.selectedLogIndex >= logs.size()) {
                state.selectedLogIndex = 0;
            }
        } else if (key == 127 || key == 8 || key == 27) { // Backspace or Escape
            state.currentView = VIEW_DASHBOARD;
        }
    }

    private void handleKeyPressNodeConfig(int key) {
        TuiState state = tui.state();
        if (state.nodes.isEmpty() || state.selectedNodeIndex >= state.nodes.size()) {
            state.currentView = VIEW_CLUSTER_DETAIL;
            return;
        }

        if (key == 127 || key == 8 || key == 27) { // Backspace or Escape
            state.currentView = VIEW_CLUSTER_DETAIL;
            return;
        }

        if (tui.readOnly() || !tui.nodeConfigurationEnabled()) {
            return;
        }

        Cluster cluster = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
        if (cluster == null) return;

        ServerNode node = state.nodes.get(state.selectedNodeIndex);

        if (key == 'p' || key == 'P') {
            ServerNode updated = new ServerNode(
                node.host(), node.port(), node.status(), node.isExternal(),
                !node.pingEnabled(), node.pingPath(), node.pingHeaderName(), node.pingHeaderValue()
            );
            cluster.updateServerNode(updated);
            tui.fetchNodeStatus();
        } else if (key == 'e' || key == 'E') {
            tui.prompts().changeNodePingPathPrompt(cluster, node);
        } else if (key == 'h' || key == 'H') {
            tui.prompts().changeNodePingHeaderNamePrompt(cluster, node);
        } else if (key == 'v' || key == 'V') {
            tui.prompts().changeNodePingHeaderValuePrompt(cluster, node);
        }
    }

    private void deregisterSelectedNode() {
        TuiState state = tui.state();
        Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
        if (c != null && !state.nodes.isEmpty()) {
            ServerNode node = state.nodes.get(state.selectedNodeIndex);
            c.deregisterServer(node.getFullHost());
            tui.fetchNodeStatus();
            state.selectedNodeIndex = 0;
        }
    }
}
