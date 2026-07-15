# GateBridge Framework Extensibility & Programmatic Config API

This document details how developers using the GateBridge framework can programmatically configure security parameters, request rate limits, connection timeouts, and register custom API/Command route endpoints.

---

## 🔐 Programmatic Gateway Configuration

The `GatewayBuilderPort` interface provides fluent builder APIs to configure the gateway node directly inside your application bootstrap code, avoiding forced reliance on external properties files:

```java
GatewayBuilderPort builder = GatewayFactory.createGateway("my-cluster")
    .port(3000)
    .pingInterval(5)
    .enableHttp(true)
    .enableTelnet(true)
    // Configure security token validation
    .requireToken(true, "developer-secret-token-key")
    // Configure maximum request limits per time window
    .rateLimit(200, 60) // 200 requests per 60 seconds
    // White-list IP patterns
    .allowedIps("127.0.0.1, 192.168.1.*")
    // Set request/ping connection timeout
    .timeout(4500);

// Transition to execution runtime
RunningGatewayPort runningGateway = builder.listen();
```

---

## 🚦 Registering Custom Route Controllers

Developers can write custom API/Command endpoints by implementing the `RouteController` interface and marking handlers with `@RouteMapping`.

### 1. Write the Controller

Define a class implementing `RouteController`. Declare methods that accept a `String` (for arguments) and a `PrintWriter` (for response output):

```java
import hexacloud.core.server.route.RouteController;
import hexacloud.core.server.route.RouteMapping;
import java.io.PrintWriter;

public class CustomApiController implements RouteController {

    @RouteMapping("HELLO")
    public void sayHello(String args, PrintWriter out) {
        out.println("HELLO WORLD! Args received: " + args);
    }

    @RouteMapping("CUSTOM_STATUS")
    public void handleStatus(String args, PrintWriter out) {
        out.println("CUSTOM_STATUS: OK");
    }
}
```

### 2. Automatic Registration (Classpath Scanner - Recommended)

The framework automatically scans the application classpath at startup for any public class implementing `RouteController` (that has a default no-argument constructor or a constructor accepting a `Cluster` parameter). 

No registration code is needed! Simply define the class, compile it, and the framework will automatically register its `@RouteMapping` endpoints.

### 3. Manual Registration (Alternative)

If you prefer to register controller instances manually (for example, to inject custom dependencies or manage instantiation manually), you can use the `.registerController()` method on your gateway builder:

```java
builder.registerController(new CustomApiController());
```

Once registered (either automatically or manually), commands matching the `@RouteMapping` value (e.g. `HELLO <arguments>`) will automatically be routed to your handler when received via HTTP, Telnet, or WebSocket connection listeners!
