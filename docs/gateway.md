# Gateway & Node Configuration

GateBridge gateways are created through `GatewayFactory` and exposed as `GatewayPort` to orchestrate network transports and cluster operations.

## Create a Gateway

The main entry point is:

```java
GatewayPort gateway = GatewayFactory.createGateway("my-cluster")
    .port(3000)
    .pingInterval(5)
    .enableTelnet(true)
    .enableHttp(true)
    .enableWs(true);
```

This creates a local gateway adapter for a named cluster, sets the base transport port, and selects transport protocol listeners.

## Registering Nodes with NodeBuilder

To support advanced telemetry, authorization, and custom health check paths, GateBridge provides a fluent `NodeBuilder` API:

```java
gateway.registerNode("http://localhost", 3005)
    .pingEnabled(true)
    .pingPath("/healthz")
    .pingHeader("Authorization", "Bearer token123")
    .register();
```

*   **`registerNode(host, port)`** — Specifies the host target and socket port.
*   **`pingEnabled(boolean)`** — Toggles active health check polling for this node.
*   **`pingPath(path)`** — Changes the URI path for health check requests.
*   **`pingHeader(name, value)`** — Appends custom authentication headers to ping checks.

For simpler registrations, you can still register a node quickly:

```java
gateway.registerServer(3001, NodeStatus.OFFLINE);
```

## State Persistence Layer

The gateway features automatic state persistence:
- Whenever a cluster is modified, nodes are registered/deregistered, or configurations change (IP lists, rate limits, timeouts), GateBridge serializes the current cluster state to `java/resources/hexacloud-state.properties`.
- When `GatewayFactory.createGateway()` is called, it automatically loads any existing configuration. If a state file is loaded, hardcoded developer bootstrap configurations are skipped so user edits are not overwritten.

## Lifecycle Management (`stop()`)

Both `GatewayPort` and `ServerManager` support dynamic lifecycle shutdown:

```java
gateway.stop();
```

This immediately releases all active network listeners (HTTP, Telnet, WebSockets) and cleanly terminates the background health-check scheduler without interrupting the JVM.

## Complete Bootstrap Example

```java
GatewayPort gateway = GatewayFactory.createGateway("my-cluster")
    .port(3000)
    .pingInterval(5)
    .enableTelnet(true)
    .enableHttp(true)
    .enableWs(true);

gateway.registerNode("http://localhost", 3001)
    .pingEnabled(true)
    .pingPath("/health")
    .pingHeader("X-Token", "secret")
    .register();

gateway.listen()
       .startPingScheduler();

// Close down resources when done
// gateway.stop();
```
