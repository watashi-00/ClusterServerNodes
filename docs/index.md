# GateBridge Documentation

## Overview

GateBridge is a small Java framework for cluster gateway management and terminal-based telemetry monitoring.

It is designed to let users compose cluster gateways and monitor node state without exposing low-level terminal rendering details.

## Documentation links

- [Gateway Usage](gateway.md) — how to configure a gateway, register servers, and start the local control plane.
- [Terminal UI](terminal-ui.md) — how `TerminalUI` works, its screens, and how to run the monitor.
- [Concurrency & Threads](thread-manager.md) — how the Loom-based `ThreadManager` schedules lightweight virtual tasks.
- [Framework Extensibility](framework-extensibility.md) — how to programmatically configure security, rates, and register route controllers.
- [Custom Events](events.md) — how to define and dispatch custom events, how to register event listener controllers, and built-in event behavior.
- [Examples](examples.md) — practical bootstrap and event wiring patterns for GateBridge.
