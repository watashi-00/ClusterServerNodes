# GateBridge

GateBridge is a lightweight Java-based cluster gateway framework from Hexacloud, with a terminal user interface (TUI) for cluster monitoring and custom event handling.

The repository contains:

- `java/` — core Java packages for cluster management, gateway creation, event dispatch, and terminal monitoring.
- `c/` — native helper code for terminal rendering via JNI.
- `run.sh` — build and launch script for the control plane and monitor.
- `run_monitor.sh` — helper script for monitor execution.

## What this project does

Hexacloud provides a modular foundation to:

- create a local gateway cluster using `GatewayFactory`
- register and monitor server nodes
- dispatch custom events with simple event listener controllers
- render a terminal-based dashboard for telemetry, cluster state, and configuration

## Quickstart

```bash
./run.sh
```

This script compiles the native terminal library, compiles Java sources into `/tmp`, and launches the application entry point.

## Documentation

Read the extended documentation for full details:

- [Overview](docs/index.md#overview)
- [Gateway Usage](docs/gateway.md)
- [Terminal UI](docs/terminal-ui.md)
- [Custom Events](docs/events.md)
- [Examples](docs/examples.md)

## Project structure

- `java/src/hexacloud/application/Main.java` — example application entry point.
- `java/src/hexacloud/application/MonitorMain.java` — example monitor launcher for GateBridge.
- `java/src/hexacloud/application/TerminalUI.java` — terminal rendering and interactive menu logic.
- `java/src/hexacloud/core/event/` — simple event bus, event contract, and subscription annotation.
- `java/src/hexacloud/infra/gateway/` — gateway adapter and factory.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
