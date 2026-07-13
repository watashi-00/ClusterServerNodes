# GateBridge

GateBridge is a lightweight Java-based cluster gateway framework from Hexacloud, with an interactive Terminal User Interface (TUI) for cluster monitoring, custom event handling, and real-time node state management.

The repository contains:

- `java/` — core Java packages for cluster management, gateway creation, event dispatch, and terminal monitoring.
- `c/` — native helper code for terminal rendering via JNI (used by `NativeTerminal`).
- `show_case/` — polyglot client scripts (Python, Go, Node.js, Bash) demonstrating real-world cluster nodes.
- `run_terminal.sh` — utility shell script to build and launch the control plane and monitor.

## What this project does

Hexacloud provides a modular foundation to:

- Create a local gateway cluster using `GatewayFactory`.
- Configure custom node parameters (health-check path, auth tokens, custom headers) using `NodeBuilder`.
- Persist cluster settings, nodes list, and gateway states automatically to `java/resources/hexacloud-state.properties`.
- Dispatch custom events with simple event listener controllers.
- Launch an interactive, modular Terminal UI (TUI) via a clean `TerminalUiPort` configuration interface.

## Quickstart

To build and launch the complete DevOps terminal console panel:

```bash
./show_case/run_terminal.sh
```

## Documentation

Read the extended documentation for full details:

- [Overview](docs/index.md#overview)
- [Gateway & Node Configuration](docs/gateway.md)
- [Terminal UI Dashboard](docs/terminal-ui.md)
- [Custom Events](docs/events.md)
- [Examples & Showcase](docs/examples.md)

## Project structure

- `java/src/hexacloud/application/TerminalMain.java` — main bootstrap entry point containing mock nodes and starting the TUI.
- `java/src/hexacloud/application/OnlyTerminalMain.java` — single-line TUI entry point loading all states dynamically from properties file.
- `java/src/hexacloud/core/tui/` — modular terminal user interface subsystem.
- `java/src/hexacloud/core/ports/TerminalUiPort.java` — clean configuration and bootstrap port for the TUI.
- `java/src/hexacloud/core/config/ClusterStatePersistence.java` — automatic state persistence layer.
- `java/src/hexacloud/core/event/` — simple event bus, event contract, and subscription annotation.
- `java/src/hexacloud/infra/gateway/` — gateway adapter, factory, and fluent node builder.

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).
