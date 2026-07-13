package hexacloud.core.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import hexacloud.core.ports.GatewayPort;

/**
 * Main coordinator for the DevOps TUI console dashboard.
 * Delegates rendering, keyboard processing, and text dialogues to sub-components.
 */
public class TerminalUI {

    private final String displayName;
    private final TuiState state = new TuiState();
    
    private final TuiRenderer renderer;
    private final TuiKeyHandler keyHandler;
    private final TuiPrompts prompts;

    // Feature Flags
    private boolean readOnly = false;
    private boolean gatewayManagementEnabled = true;
    private boolean clusterManagementEnabled = true;
    private boolean nodeManagementEnabled = true;
    private boolean nodeConfigurationEnabled = true;

    private static final Map<String, GatewayPort> activeGateways = new ConcurrentHashMap<>();

    /**
     * Start the Terminal UI client with the default settings.
     */
    public static void startTerminal(String displayName) {
        new TerminalUI(displayName).run();
    }

    /**
     * Start the Terminal UI client seeding it with an already started GatewayPort instance.
     */
    public static void startTerminal(String displayName, GatewayPort gateway) {
        if (gateway != null) {
            activeGateways.put(gateway.getClusterName(), gateway);
        }
        new TerminalUI(displayName).run();
    }

    /**
     * Initialize TerminalUI.
     */
    public TerminalUI(String displayName) {
        this.displayName = displayName != null ? displayName : "GateBridge Control Plane";
        this.renderer = new TuiRenderer(this);
        this.keyHandler = new TuiKeyHandler(this);
        this.prompts = new TuiPrompts(this);
    }

    public TuiState state() {
        return state;
    }

    public TuiRenderer renderer() {
        return renderer;
    }

    public TuiKeyHandler keyHandler() {
        return keyHandler;
    }

    public TuiPrompts prompts() {
        return prompts;
    }

    public Map<String, GatewayPort> activeGateways() {
        return activeGateways;
    }

    public String displayName() {
        return displayName;
    }

    public boolean readOnly() {
        return readOnly;
    }

    public boolean gatewayManagementEnabled() {
        return gatewayManagementEnabled;
    }

    public boolean clusterManagementEnabled() {
        return clusterManagementEnabled;
    }

    public boolean nodeManagementEnabled() {
        return nodeManagementEnabled;
    }

    public boolean nodeConfigurationEnabled() {
        return nodeConfigurationEnabled;
    }

    public TerminalUI readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    public TerminalUI gatewayManagementEnabled(boolean enabled) {
        this.gatewayManagementEnabled = enabled;
        return this;
    }

    public TerminalUI clusterManagementEnabled(boolean enabled) {
        this.clusterManagementEnabled = enabled;
        return this;
    }

    public TerminalUI nodeManagementEnabled(boolean enabled) {
        this.nodeManagementEnabled = enabled;
        return this;
    }

    public TerminalUI nodeConfigurationEnabled(boolean enabled) {
        this.nodeConfigurationEnabled = enabled;
        return this;
    }

    public boolean isGatewayActive(String clusterName) {
        return activeGateways.containsKey(clusterName);
    }

    /**
     * Launch the TUI loop.
     */
    public void run() {
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
            if (!state.clusterNames.isEmpty()) {
                state.selectedClusterName = state.clusterNames.get(0);
                fetchNodeStatus();
                fetchClusterConfig(state.selectedClusterName);
            }

            while (state.running) {
                long now = System.currentTimeMillis();
                if (now - lastFetch >= 1200) {
                    fetchClusterNames();
                    if (!state.selectedClusterName.isEmpty()) {
                        fetchNodeStatus();
                        fetchClusterConfig(state.selectedClusterName);
                    }
                    fetchGlobalConfig();
                    lastFetch = now;
                    needRedraw = true;
                }

                if (needRedraw) {
                    renderer.draw();
                    needRedraw = false;
                }

                int key = NativeTerminal.readKey();
                if (key != -1) {
                    keyHandler.handleKeyPress(key);
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

    public void fetchClusterNames() {
        List<String> names = new ArrayList<>();
        for (Cluster c : ClusterRegistry.getInstance().getClusters()) {
            names.add(c.getClusterName());
        }
        state.clusterNames = names;
    }

    public void fetchClusterConfig(String name) {
        if (name == null || name.isEmpty()) return;
        Cluster c = ClusterRegistry.getInstance().getCluster(name);
        if (c != null) {
            state.targetRequireToken = c.isRequireToken();
            state.targetTimeoutMs = c.getTimeoutMs();
            state.targetAllowedIps = c.getAllowedIps();
            state.targetRateLimitRequests = c.getRateLimitRequests();
            state.targetRateLimitDurationSeconds = c.getRateLimitDurationSeconds();
        }
    }

    public void fetchGlobalConfig() {
        state.globalPingInterval = hexacloud.core.config.ClusterConfig.DEFAULT_PING_INTERVAL_SECONDS;
    }

    public void fetchNodeStatus() {
        if (state.selectedClusterName.isEmpty()) return;
        Cluster c = ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
        if (c != null) {
            state.nodes = c.getCluster();
        } else {
            state.nodes.clear();
        }
    }

    public void adjustServicesViewport(int visibleCount) {
        if (state.nodes.isEmpty()) {
            state.servicesViewportStart = 0;
            return;
        }
        if (state.selectedNodeIndex < 0) state.selectedNodeIndex = 0;
        if (state.selectedNodeIndex >= state.nodes.size()) state.selectedNodeIndex = state.nodes.size() - 1;
        
        if (state.selectedNodeIndex < state.servicesViewportStart) {
            state.servicesViewportStart = state.selectedNodeIndex;
        } else if (state.selectedNodeIndex >= state.servicesViewportStart + visibleCount) {
            state.servicesViewportStart = state.selectedNodeIndex - visibleCount + 1;
        }
    }

    public void adjustLogsViewport(int totalLogs, int visibleCount) {
        if (totalLogs == 0) {
            state.logViewportStart = 0;
            return;
        }
        if (state.selectedLogIndex < 0) state.selectedLogIndex = 0;
        if (state.selectedLogIndex >= totalLogs) state.selectedLogIndex = totalLogs - 1;
        
        if (state.selectedLogIndex < state.logViewportStart) {
            state.logViewportStart = state.selectedLogIndex;
        } else if (state.selectedLogIndex >= state.logViewportStart + visibleCount) {
            state.logViewportStart = state.selectedLogIndex - visibleCount + 1;
        }
    }
}
