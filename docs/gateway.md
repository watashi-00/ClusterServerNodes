# Gateway & Node Configuration

GateBridge gateways are created through `GatewayFactory` and exposed as `GatewayBuilderPort` during configuration and `RunningGatewayPort` at runtime.

## Create a Gateway

The main entry point is:

```java
GatewayBuilderPort builder = GatewayFactory.createGateway("my-cluster")
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
builder.registerNode("http://localhost", 3005)
    .pingProtocol(PingProtocol.HTTP)
    .pingPath("/healthz")
    .pingHeader("Authorization", "Bearer token123")
    .register();
```

*   **`registerNode(host, port)`** — Specifies the host target and socket port.
*   **`pingProtocol(PingProtocol)`** — Toggles and configures the active health check protocol (`HTTP`, `WEBSOCKET`, `TCP`, `UDP`, `GRPC`, or `NONE` for Push-only).
*   **`pingPath(path)`** — Changes the URI path for active health check requests.
*   **`pingHeader(name, value)`** — Appends custom authentication headers to ping checks.

For simpler registrations, you can still register a node quickly:

```java
builder.registerServer(3001, NodeStatus.OFFLINE);
```

## State Persistence Layer

The gateway features automatic state persistence:
- Whenever a cluster is modified, nodes are registered/deregistered, or configurations change (IP lists, rate limits, timeouts), GateBridge serializes the current cluster state to `.state/<clusterName>-state.properties`.
- When `GatewayFactory.createGateway()` is called, it automatically loads any existing configuration. If a state file is loaded, hardcoded developer bootstrap configurations are skipped so user edits are not overwritten.
- Sensitive values are excluded from state files. Cluster secrets and ping header token values are runtime-only and should be supplied through code, environment configuration, or a secret manager.

## Lifecycle Management (`stop()`)

Both `RunningGatewayPort` and `ServerManager` support dynamic lifecycle shutdown:

```java
runningGateway.stop();
```

This immediately releases all active network listeners (HTTP, Telnet, WebSockets) and cleanly terminates the background health-check scheduler without interrupting the JVM.

## Push-Based Passive Telemetry

Nodes can push their telemetry metrics actively using the REST API:
- **HTTP Path:** `/clusters/<clusterName>/telemetry?host=<host>&port=<port>&cpu=<cpu>&ram=<ram>&language=<language>&status=<status>&event=<eventName>&protocol=<protocol>&format=<format>`
- **Telnet Command:** `TELEMETRY <host> <port> cpu=<cpu> ram=<ram> language=<language> status=<status> event=<eventName> protocol=<protocol> format=<format>`

The optional `event` parameter dispatches a `ClusterEvent.NodeEventSubmitted` event. `protocol` and `format` describe how the source service produced the event, for example `protocol=grpc format=json` or `protocol=ws format=cloudevent`. Any extra key/value parameters are forwarded as event attributes, except reserved fields and authentication tokens.

Refer to [Ping Health-Check Contracts](ping-api-contract.md) for full details.

## Complete Bootstrap Example

```java
GatewayBuilderPort builder = GatewayFactory.createGateway("my-cluster")
    .port(3000)
    .pingInterval(5)
    .enableTelnet(true)
    .enableHttp(true)
    .enableWs(true);

builder.registerNode("http://localhost", 3001)
    .pingProtocol(PingProtocol.HTTP)
    .pingPath("/health")
    .pingHeader("X-Token", "secret")
    .register();

RunningGatewayPort runningGateway = builder.listen()
       .startPingScheduler();

// Close down resources when done
// runningGateway.stop();
```
