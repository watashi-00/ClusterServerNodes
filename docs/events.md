# Custom Events

GateBridge includes a lightweight event system in `java/src/hexacloud/core/event/`.

## Core event concepts

- `Event` — marker interface for all event types.
- `EventController` — marker interface for listener containers.
- `@Subscribe` — annotates event handler methods.
- `EventBusManager` — event registration and dispatch manager.

## Registering event listeners

Create a controller class and annotate methods with `@Subscribe`.
Each subscribed method must accept exactly one parameter that implements `Event`.

Example:

```java
record UserCustomEvent(String message) implements Event {}

class CustomEventListener implements EventController {
    @Subscribe
    public void onCustomEvent(UserCustomEvent event) {
        DebugUtils.info("UserCustomEvent received: " + event.message());
    }
}
```

Register the listener with a gateway event manager:

```java
hexacloud.eventManager().registerListener(new CustomEventListener());
```

## Dispatching events

Events are dispatched through the same event manager:

```java
hexacloud.eventManager().dispatch(new UserCustomEvent("Hello from Hexacloud"));
```

The event bus finds all registered subscribers for the event type and invokes them.

## Built-in events

The event subsystem can also handle framework events such as cluster and node lifecycle events.

For example, `NodeRegistered` is dispatched when a node is successfully registered with the cluster:

```java
@Subscribe
public void onNodeRegistered(NodeRegistered event) {
    DebugUtils.info("Node registered: " + event.node().getFullHost());
}
```

## Best practices

- register event listeners before performing actions that emit events
- keep event handlers stateless where possible
- use event records for simple payloads

## Example from `Main.java`

Event examples are available in [Examples](examples.md), including custom event records and controller registration.
