package hexacloud.core.tui;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import hexacloud.core.ports.RunningGatewayPort;

/**
 * Main coordinator for the DevOps TUI console dashboard.
 * Delegates rendering, keyboard processing, and text dialogues to sub-components.
 */
public class TerminalUI implements hexacloud.core.ports.TerminalUiPort {

    private String displayName;
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
    private boolean tokenManagementEnabled = true;

    private static final Map<String, RunningGatewayPort> activeGateways = new ConcurrentHashMap<>();
    private final java.util.concurrent.Semaphore redrawSemaphore = new java.util.concurrent.Semaphore(0);

    public void triggerRedraw() {
        redrawSemaphore.release();
    }

    /**
     * Start the Terminal UI client with the default settings.
     */
    public static void startTerminal(String displayName) {
        new TerminalUI(displayName).run();
    }

    /**
     * Start the Terminal UI client seeding it with an already started RunningGatewayPort instance.
     */
    public static void startTerminal(String displayName, RunningGatewayPort gateway) {
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

    public Map<String, RunningGatewayPort> activeGateways() {
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

    @Override
    public boolean tokenManagementEnabled() {
        return tokenManagementEnabled;
    }

    @Override
    public hexacloud.core.ports.TerminalUiPort displayName(String displayName) {
        this.displayName = displayName;
        return this;
    }

    @Override
    public hexacloud.core.ports.TerminalUiPort readOnly(boolean readOnly) {
        this.readOnly = readOnly;
        return this;
    }

    @Override
    public hexacloud.core.ports.TerminalUiPort gatewayManagementEnabled(boolean enabled) {
        this.gatewayManagementEnabled = enabled;
        return this;
    }

    @Override
    public hexacloud.core.ports.TerminalUiPort clusterManagementEnabled(boolean enabled) {
        this.clusterManagementEnabled = enabled;
        return this;
    }

    @Override
    public hexacloud.core.ports.TerminalUiPort nodeManagementEnabled(boolean enabled) {
        this.nodeManagementEnabled = enabled;
        return this;
    }

    @Override
    public hexacloud.core.ports.TerminalUiPort nodeConfigurationEnabled(boolean enabled) {
        this.nodeConfigurationEnabled = enabled;
        return this;
    }

    @Override
    public hexacloud.core.ports.TerminalUiPort tokenManagementEnabled(boolean enabled) {
        this.tokenManagementEnabled = enabled;
        return this;
    }

    @Override
    public hexacloud.core.ports.TerminalUiPort seedGateway(RunningGatewayPort gateway) {
        if (gateway != null) {
            activeGateways.put(gateway.getClusterName(), gateway);
        }
        return this;
    }

    @Override
    public void start() {
        this.run();
    }

    public boolean isGatewayActive(String clusterName) {
        return activeGateways.containsKey(clusterName);
    }

    /**
     * Launch the TUI loop.
     */
    public void run() {
        state.running = true;
        DebugUtils.setTuiModeActive(true);

        NativeTerminal.initTerminal();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            NativeTerminal.resetTerminal();
        }));

        try {
            // Load persisted state configuration files from disk
            hexacloud.core.config.ClusterStatePersistence.loadState();

            // Fetch initial configuration & clusters list
            fetchClusterNames();
            fetchGlobalConfig();
            if (!state.clusterNames.isEmpty()) {
                state.selectedClusterName = state.clusterNames.get(0);
                fetchNodeStatus();
                fetchClusterConfig(state.selectedClusterName);
            }

            // Subscribe to Global Event Bus for event-driven redraw triggers
            hexacloud.core.event.EventBusManager.getGlobal().sub(hexacloud.core.cluster.event.ClusterEvent.NodeStatusChanged.class, event -> {
                triggerRedraw();
            });

            hexacloud.core.event.EventBusManager.getGlobal().sub(hexacloud.core.cluster.event.ClusterEvent.NodeTelemetryUpdated.class, event -> {
                triggerRedraw();
            });

            hexacloud.core.event.EventBusManager.getGlobal().sub(hexacloud.core.cluster.event.ClusterEvent.NodeRegistered.class, event -> {
                triggerRedraw();
            });

            hexacloud.core.event.EventBusManager.getGlobal().sub(hexacloud.core.cluster.event.ClusterEvent.NodeDeregistered.class, event -> {
                triggerRedraw();
            });

            hexacloud.core.event.EventBusManager.getGlobal().sub(hexacloud.core.cluster.event.ClusterEvent.ClusterRegistered.class, event -> {
                triggerRedraw();
            });

            // Start low-latency non-blocking input listener on a lightweight virtual thread
            hexacloud.core.utils.ThreadManager.startVirtual("TuiInputReader", () -> {
                while (state.running) {
                    int key = NativeTerminal.readKey();
                    if (key != -1) {
                        synchronized (state) {
                            keyHandler.handleKeyPress(key);
                        }
                        triggerRedraw();
                    }
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        break;
                    }
                }
            });

            // Initial render
            renderer.draw();

            // Event-driven loop
            while (state.running) {
                try {
                    // Block until an event releases the semaphore
                    redrawSemaphore.acquire();
                    
                    // Debounce/Coalesce: sleep 15ms to group rapid multiple events
                    Thread.sleep(15);
                    redrawSemaphore.drainPermits();

                    if (state.running) {
                        // Dynamically update state data before drawing
                        fetchClusterNames();
                        if (!state.selectedClusterName.isEmpty()) {
                            fetchNodeStatus();
                            fetchClusterConfig(state.selectedClusterName);
                        }
                        fetchGlobalConfig();

                        renderer.draw();
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        } catch (Exception e) {
            NativeTerminal.resetTerminal();
            e.printStackTrace();
        } finally {
            NativeTerminal.resetTerminal();
            DebugUtils.setTuiModeActive(false);
            if (!readOnly) {
                // Stop all gateways if not read-only
                for (RunningGatewayPort gw : activeGateways.values()) {
                    try {
                        gw.stop();
                    } catch (Exception ex) {
                        // Ignore
                    }
                }
                System.exit(0);
            }
        }
    }

    public void fetchClusterNames() {
        List<String> onlineNames = new ArrayList<>();
        List<String> offlineNames = new ArrayList<>();
        for (Cluster c : ClusterRegistry.getInstance().getClusters()) {
            String name = c.getClusterName();
            if (isGatewayActive(name)) {
                onlineNames.add(name);
            } else {
                offlineNames.add(name);
            }
        }
        onlineNames.sort(String::compareTo);
        offlineNames.sort(String::compareTo);
        
        List<String> sortedNames = new ArrayList<>();
        sortedNames.addAll(onlineNames);
        sortedNames.addAll(offlineNames);
        
        String previousSelected = state.selectedClusterName;
        state.clusterNames = sortedNames;
        
        if (!sortedNames.isEmpty()) {
            int index = sortedNames.indexOf(previousSelected);
            if (index != -1) {
                state.selectedClusterIndex = index;
            } else {
                state.selectedClusterIndex = 0;
                state.selectedClusterName = sortedNames.get(0);
            }
        } else {
            state.selectedClusterIndex = 0;
            state.selectedClusterName = "";
        }
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
