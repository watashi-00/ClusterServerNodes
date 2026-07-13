package hexacloud.core.tui;

import hexacloud.core.ports.TerminalUiPort;

/**
 * Factory class for bootstrapping and obtaining a TerminalUiPort instance.
 */
public class TerminalUiFactory {
    
    /**
     * Create a TerminalUiPort instance with the default display title.
     */
    public static TerminalUiPort createTui() {
        return new TerminalUI("GateBridge Control Plane");
    }

    /**
     * Create a TerminalUiPort instance with a custom display title.
     */
    public static TerminalUiPort createTui(String displayName) {
        return new TerminalUI(displayName);
    }
}
