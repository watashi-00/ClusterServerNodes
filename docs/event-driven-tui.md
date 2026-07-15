# Event-Driven Terminal UI Architecture

The GateBridge Terminal UI operates on a fully **event-driven model** to ensure instant screen updates, zero idle CPU consumption, and unified event propagation across local domains and network adapters.

---

## 1. Threading Model

Instead of running on a periodic polling loop that consumes CPU cycles, the Terminal UI separates processing into two lightweight threads:

1.  **Main TUI Thread:** Blocks on a JVM `Semaphore.acquire()` call. It consumes 0% CPU when idle and only wakes up when a redraw is requested.
2.  **Input Reader Thread:** A lightweight virtual thread started via `ThreadManager.startVirtual("TuiInputReader", ...)` that runs a low-latency keyboard polling loop (every 50ms) using `NativeTerminal.readKey()`. When a key is pressed, it handles the action synchronously and releases the redraw semaphore.

---

## 2. Event Propagation (Event Bubbling)

All domain events from the local gateway adapters propagate up to a central event bus to trigger TUI updates:

```
[Cluster Local Event Bus] ----(Bubbles Up)----> [Global Event Bus] ----(Triggers)----> [Terminal UI Redraw]
```

### 2.1. Central Event Bus
Inside `EventBusManager`, a static `GLOBAL` event bus is defined. All individual cluster event managers automatically bubble up their dispatched events to the global event bus.

### 2.2. Supported Events & Recents Feed
The `TerminalUI` subscribes to the following events on the global event bus:
- **`NodeStatusChanged`** — Fired when a node changes its connectivity status.
- **`NodeTelemetryUpdated`** — Fired when a node's CPU, RAM, or latency metrics are updated.
- **`NodeRegistered`** — Fired when a new node is registered.
- **`NodeDeregistered`** — Fired when an existing node is removed.
- **`ClusterRegistered`** — Fired when a new cluster is created or registered.

In addition to these structural redraw triggers, a **Global Event Interceptor** is registered inside the TUI loop to intercept all custom and system events. Intercepted events are displayed in the **RECENT EVENTS** panel of the Dashboard with dynamic relative time tracking (e.g. `[15s] CustomEvent: myMessage`).

---

## 3. Coalescing & Debouncing Redraws

To prevent terminal screen flickering when multiple events fire in rapid succession (e.g., when multiple node statuses update simultaneously during startup or active pinging):

1.  When the main thread wakes up from `semaphore.acquire()`, it sleeps for `15ms`.
2.  It then calls `redrawSemaphore.drainPermits()` to clear any pending redraw tokens accumulated during the sleep.
3.  It performs exactly **one** screen refresh, grouping all recent changes into a single visual update.
