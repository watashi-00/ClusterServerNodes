# Terminal UI

The GateBridge terminal UI is implemented in `java/src/hexacloud/application/TerminalUI.java`.

## Purpose

`TerminalUI` abstracts the terminal rendering and keyboard interaction so application code does not need to manage low-level terminal state.

It provides a simple entry point for launching the monitor:

```java
TerminalUI.startTerminal("watashi-00");
```

or with a custom HTTP port:

```java
TerminalUI.startTerminal("watashi-00", 3001);
```

## Main features

- interactive TUI with main menu, telemetry, cluster details, and configuration screens
- on-screen keyboard commands for refresh, adding nodes, exporting reports, and navigation
- polling node status only while the telemetry screen is active
- automatic terminal reset on shutdown

## UI interaction

The monitor supports the following keys:

- `UP` / `DOWN` — navigate the main menu
- `ENTER` — select a menu item
- `Q` or `ESC` — return to the main menu or exit
- `R` — refresh telemetry when viewing the telemetry screen
- `A` — add a new local node from the telemetry screen
- `S` — export the current telemetry report

## Application entry points

- `java/src/hexacloud/application/Main.java` uses `GatewayFactory` and demonstrates event handling.
- `java/src/hexacloud/application/MonitorMain.java` launches the terminal monitor.

## Notes

`TerminalUI` depends on `NativeTerminal` for low-level screen control and `DebugUtils` for logging.

It fetches node data from the local control plane using the configured cluster secret and HTTP port.
