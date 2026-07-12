# Usage Examples

This page collects common GateBridge usage examples that are intentionally separated from the framework entry-point code.

> Example logic has been moved out of `Main.java` and is documented here instead of being embedded in the application entry point.

## Gateway setup

Create a gateway and configure transports:

```java
GatewayPort gateway = GatewayFactory.createGateway("my-cluster")
    .port(3000)
    .pingInterval(5)
    .enableTelnet(true)
    .enableHttp(true)
    .enableWs(true);
```

## Registering servers

Register cluster nodes using the `GatewayPort` API:

```java
gateway.registerServer(3001, NodeStatus.OFFLINE)
       .registerServer(3002, NodeStatus.OFFLINE)
       .registerServer(3003, NodeStatus.OFFLINE);
```

Then start listening and health checks:

```java
gateway.listen()
       .startPingScheduler();
```

## Using the terminal monitor

`MonitorMain` is provided as an example launcher for the GateBridge monitor. It is part of the sample application layer and not required for core framework integration.

```bash
./run.sh
```

The monitor renders an interactive dashboard with telemetry, cluster state, and configuration screens.

## Custom events

Define event records and event controllers separately from your main entry point.

```java
record UserCustomEvent(String message) implements Event {}

class CustomEventListener implements EventController {
    @Subscribe
    public void onCustomEvent(UserCustomEvent event) {
        DebugUtils.info("UserCustomEvent received: " + event.message());
    }
}
```

Register listeners and dispatch events with the gateway event manager:

```java
gateway.eventManager().registerListener(new CustomEventListener());

gateway.eventManager().dispatch(new UserCustomEvent("Startup complete"));
```

## Recommended structure

- keep `Main.java` and `MonitorMain.java` focused on startup and bootstrapping
- place concrete examples in docs or sample source files
- use the event system for custom hooks rather than embedding example logic in the entry point
