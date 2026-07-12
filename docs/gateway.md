# Gateway Usage

GateBridge gateways are created through `GatewayFactory` and exposed as `GatewayPort`.

## Create a gateway

The main entry point is:

```java
GatewayPort gateway = GatewayFactory.createGateway("my-cluster")
    .port(3000)
    .pingInterval(5)
    .enableTelnet(true)
    .enableHttp(true)
    .enableWs(true);
```

This creates a local gateway adapter for a named cluster and configures the transport layer.

## Register server nodes

You can register node endpoints using one of the `GatewayPort` methods:

```java
gateway.registerServer(3001, NodeStatus.OFFLINE)
       .registerServer(3002, NodeStatus.OFFLINE);
```

Each registered server is tracked by the cluster and can participate in the built-in event flow.

## Start listening and ping scheduler

After registering nodes, call:

```java
gateway.listen()
       .startPingScheduler();
```

This starts the local HTTP/Telnet/WebSocket listeners and begins periodic node health checks.

## Event manager integration

`GatewayPort` exposes `eventManager()` so you can attach event controllers:

```java
gateway.eventManager().registerListener(new MyEventListener());
```

Events can be dispatched manually or emitted by cluster lifecycle actions.

## Example

```java
GatewayPort gateway = GatewayFactory.createGateway("my-cluster")
    .port(3000)
    .pingInterval(5)
    .enableTelnet(true)
    .enableHttp(true)
    .enableWs(true)
    .registerServer(3001, NodeStatus.OFFLINE)
    .registerServer(3002, NodeStatus.OFFLINE)
    .listen()
    .startPingScheduler();

gateway.eventManager().dispatch(new UserCustomEvent("Gateway ready"));
```

## Notes

- `GatewayFactory.createGateway(clusterName)` creates a local gateway adapter.
- `GatewayPort` returns itself on most calls so configuration can be chained.
- The gateway is designed to work with `TerminalUI` for easy monitoring and with the custom event system for application-specific hooks.

For full bootstrap examples, see [Examples](examples.md).
