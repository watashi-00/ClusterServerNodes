package hexacloud.core.ports;

/**
 * Port interface for configuring and launching the DevOps Terminal UI.
 */
public interface TerminalUiPort {
    
    /**
     * Set the display title shown in the terminal header.
     */
    TerminalUiPort displayName(String displayName);
    
    /**
     * Set read-only mode to prevent any modification operations from the TUI.
     */
    TerminalUiPort readOnly(boolean readOnly);
    
    /**
     * Enable or disable gateway startup/shutdown setup features.
     */
    TerminalUiPort gatewayManagementEnabled(boolean enabled);
    
    /**
     * Enable or disable creating and removing clusters.
     */
    TerminalUiPort clusterManagementEnabled(boolean enabled);
    
    /**
     * Enable or disable registering and deregistering service nodes.
     */
    TerminalUiPort nodeManagementEnabled(boolean enabled);
    
    /**
     * Enable or disable custom node configurations (ping routes, authentication headers).
     */
    TerminalUiPort nodeConfigurationEnabled(boolean enabled);
    
    /**
     * Enable or disable cluster secret token management (key changes, security toggles).
     */
    TerminalUiPort tokenManagementEnabled(boolean enabled);
    
    /**
     * Seed the TUI with an already started RunningGatewayPort instance.
     */
    TerminalUiPort seedGateway(RunningGatewayPort gateway);
    
    /**
     * Launch the interactive terminal loop.
     */
    void start();
    
    /**
     * Check if token management is enabled.
     */
    boolean tokenManagementEnabled();
}
