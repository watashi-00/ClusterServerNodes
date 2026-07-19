# GateBridge

[![Java 8](https://img.shields.io/badge/Java-8-orange.svg)](https://openjdk.org/)
[![Java 11](https://img.shields.io/badge/Java-11-orange.svg)](https://openjdk.org/projects/jdk/11/)
[![Java 17](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.org/projects/jdk/17/)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Build Status](https://img.shields.io/badge/Build-Maven-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-MIT-green.svg)](LICENSE)

GateBridge is a lightweight, zero-dependency Java cluster gateway framework featuring multi-protocol transports, Loom-based virtual threading (with seamless Java 8 compatibility overlays), dynamic autodiscovery, and an interactive DevOps TUI telemetry console.

---

## 🚀 Key Framework Services & Capabilities

GateBridge offers a complete suite of modular internal services designed to run on resource-constrained environments (like 1GB RAM vCPUs):

### 1. Multi-Protocol Gateway Transports (`ServerManager`)
The framework can run three independent network services concurrently on a single base port:
*   **Telnet Server:** High-speed line-based command execution port using raw sockets.
*   **HTTP REST Server:** Exposes JSON APIs for metrics, cluster node lists, and control loops (runs on `base_port + 1`).
*   **WebSocket Stream Server:** Pushes real-time JSON event streams (telemetry updates, node status changes) to clients (runs on `base_port + 2`).

### 2. Virtual Thread Concurrency Engine (`ThreadManager`)
GateBridge leverages **Java 21 Virtual Threads (Loom)** for lightweight asynchronous execution:
*   **Zero OS Thread Spawning:** Spawns virtual threads inside JVM Heap memory instead of heavy OS kernel threads.
*   **Fixed Thread Footprint:** The entire JVM stays constrained to exactly **9 platform OS threads** (6 base JVM threads + 3 carrier threads), regardless of how many concurrent WebSocket clients connect or pings are scheduled.
*   **Virtual Schedulers:** Scheduled tasks (like health pings) run on virtual thread executors, yielding the physical CPU core when sleeping (`0% CPU idle footprint`).

### 3. Customizable Route Controllers
Expose new cluster commands and REST/Telnet APIs dynamically. Developers create classes implementing `RouteController` and annotate target handler methods with `@RouteMapping`:
```java
public class MyCustomController implements RouteController {
    @RouteMapping("HELLO")
    public void sayHello(String args, PrintWriter out) {
        out.println("Hello, " + (args.isEmpty() ? "World" : args));
    }
}
```
At startup, GateBridge scans the classpath and registers these mapped commands automatically.

### 4. Custom Events Subsystem
GateBridge supports typed custom events using simple Java `record` declarations:
```java
// Define a custom event payload
public record OrderProcessedEvent(String orderId, double amount) implements Event {}

// Create a listener controller
public class OrderListener implements EventController {
    @Subscribe
    public void onOrderProcessed(OrderProcessedEvent event) {
        System.out.println("Processing order: " + event.orderId());
    }
}
```
Listeners are autodiscovered on startup and bound to the global Event Bus.

### 5. Dynamic Autodiscovery Engine (Zero Configuration)
Eliminates manual bootstrapping:
*   **Route Auto-Discovery:** Automatically scans the classpath for classes implementing `RouteController` and registers their mappings.
*   **Event Auto-Discovery:** Automatically scans the classpath for classes implementing `EventController` and binds `@Subscribe` handlers.
*   **Package-Scoped Scanning:** Restricts classpath scanning to the main application package to maintain sub-millisecond startup times.

### 6. Programmatic Fluent API & Nested Node Builders
Configure gateways and nodes programmatically without properties files:
```java
GatewayBuilderPort builder = GatewayFactory.createGateway("my-cluster")
    .port(3000)
    .requireToken(true, "secret-token")
    .rateLimit(100, 60)
    .allowedIps("127.0.0.1");

// Register a node using the fluent builder
builder.registerNode("http://node-a", 8080)
    .pingEnabled(true)
    .pingPath("/healthz")
    .pingHeader("Authorization", "Bearer token-abc")
    .register();
```

### 7. Asynchronous Health Ping Scheduler
*   Decoupled, multi-threaded node monitoring via customizable HTTP/TCP health checks.
*   Supports custom ping paths, custom headers, and external/internal node flags.
*   Dispatches lifecycle event bus triggers immediately on node status transitions.

### 8. Global Event Bus & Interceptor Subsystem
*   Lightweight, high-performance publish-subscribe event system.
*   Exposes **Global Event Interceptors** to capture, audit, or log all dispatched events globally, providing real-time hooks for logging and metrics.

### 9. DevOps Terminal UI Dashboard (TUI)
An interactive command center for cluster operations:
*   **Live Metrics:** Real-time system resources monitor (RAM Allocation/Usage, CPU, and Thread breakdown).
*   **Explicit Thread Classification:** Displays exactly how many OS threads belong to the application logic versus internal JVM daemon services (e.g. `OS Threads: 9 (App: 1)`).
*   **Recent Events Feed:** Renders dispatched system and custom events dynamically with live relative time tracking (e.g. `[2s] NodeReg: localhost:3001`).
*   **Log Redirection:** Redirects global `System.out` and `System.err` prints into the TUI logs panel to prevent terminal window corruption.
*   **On-Demand Toggle Mode (`startToggleMode`):** Detach the dashboard anytime (resuming standard console stdout log outputs) and reattach dynamically by pressing `ENTER`.

---

## 📦 Project Structure

*   `java/src/hexacloud/application/MinimalApplication.java` — Example standalone application demonstrating programmatic bootstrapping, custom routes, and custom event listeners running in headless and toggle TUI mode.
*   `java/src/hexacloud/application/TerminalMain.java` — Bootstraps a gateway and starts the interactive DevOps Panel.
*   `java/src/hexacloud/core/ports/` — Declares clean segregation boundaries: `GatewayBuilderPort` (configuration) and `RunningGatewayPort` (runtime control).
*   `java/src/hexacloud/core/tui/` — Subsystem for rendering, key handling, and input scanner loops.
*   `java/src/hexacloud/core/utils/ThreadManager.java` — Core virtual thread wrapper class.

---

## 🚀 Quickstart

Compile and start the DevOps interactive terminal console:

```bash
./show_case/run_terminal.sh
```

---

## 📖 Documentation

Review the detailed module guides:

*   [Overview](docs/index.md#overview)
*   [Gateway & Node Configurations](docs/gateway.md)
*   [Terminal UI Dashboard Guides](docs/terminal-ui.md)
*   [Concurrency & ThreadManager API](docs/thread-manager.md)
*   [Custom Events API](docs/events.md)
*   [Framework Extensibility Guide](docs/framework-extensibility.md)
*   [Examples & Client Showcase](docs/examples.md)

---

## License

This project is licensed under the MIT License. See [LICENSE](LICENSE).

---

Created and maintained by watashi-00 (watashi00 | Rodrigo).
