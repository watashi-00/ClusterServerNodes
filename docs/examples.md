# Usage Examples & Showcase

This page collects common GateBridge bootstrapping examples and highlights the polyglot client nodes available in the showcase directory.

## Bootstrapping a Gateway & TUI

Using `TerminalUiFactory` and `TerminalUiPort` makes launching the console dashboard fluent:

```java
import hexacloud.core.ports.GatewayBuilderPort;
import hexacloud.core.ports.RunningGatewayPort;
import hexacloud.core.ports.TerminalUiPort;
import hexacloud.core.tui.TerminalUiFactory;
import hexacloud.infra.gateway.GatewayFactory;

public class AppBootstrap {
    public static void main(String[] args) {
        // 1. Build and configure the gateway listener ports
        GatewayBuilderPort builder = GatewayFactory.createGateway("production-cluster")
            .port(8080)
            .pingInterval(10)
            .enableHttp(true)
            .enableTelnet(true);

        // 2. Launch listeners and checks
        RunningGatewayPort gateway = builder.listen().startPingScheduler();

        // 3. Obtain the TUI control panel and lock permissions
        TerminalUiPort controlPanel = TerminalUiFactory.createTui("Hexacloud Admin Desk")
            .seedGateway(gateway)
            .readOnly(false)
            .nodeManagementEnabled(true)
            .gatewayManagementEnabled(true);

        // 4. Run the interactive terminal interface
        controlPanel.start();
    }
}
```

## Zero-Configuration Dynamic Startup

If your gateway state is already persisted inside `java/resources/hexacloud-state.properties`, you can bootstrap the entire monitor and gateway server network with a single line of code:

```java
import hexacloud.core.tui.TerminalUiFactory;

public class PureTerminalLauncher {
    public static void main(String[] args) {
        TerminalUiFactory.createTui("Hexacloud Dynamic Dashboard").start();
    }
}
```

## Polyglot Node Showcase (`show_case/`)

The repository contains concrete client nodes written in various languages inside the [`show_case/`](../show_case/) folder to simulate real production service workloads:

### 1. Node.js Client Node (`node_node.js`)
*   **Protocol:** Connects to the gateway via a Telnet TCP socket connection.
*   **Functionality:** Registers itself by sending dynamic command lines and spins up a local HTTP listener on port `4001` that answers health pings.
*   **Command:** `node show_case/node_node.js`

### 2. Python Client Node (`python_node.py`)
*   **Protocol:** Registers itself via HTTP POST endpoint requests to `/register`.
*   **Functionality:** Starts an HTTP server on port `4002` that answers health checks with a custom auth header token (`Bearer showCaseToken`).
*   **Command:** `python3 show_case/python_node.py`

### 3. Go Client Node (`go_node.go`)
*   **Protocol:** Registers via HTTP POST request.
*   **Functionality:** Standardized Go HTTP router listening on port `4003` responding with JSON status values on `/` route checks.
*   **Command:** `go run show_case/go_node.go`

### 4. Bash DevOps Script (`bash_control.sh`)
*   **Protocol:** Telnet Netcat socket and raw curl checks.
*   **Functionality:** DevOps querying automation showing list registers and status telemetry.
*   **Command:** `./show_case/bash_control.sh`
