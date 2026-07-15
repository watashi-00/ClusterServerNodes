# Logging System

GateBridge provides a modular, production-grade logging architecture designed for both local developer debugging (via the Terminal UI dashboard) and enterprise cloud tracking (via standard logger integrations).

---

## 1. Core Architecture

The GateBridge core uses the **SLF4J (Simple Logging Facade for Java)** API for all internal logging operations. 

```
                               ┌─────────────┐
                        ┌─────►│  Logback    │
                        │      └─────────────┘
  ┌──────────────┐      │      ┌─────────────┐
  │  GateBridge  │──────┼─────►│   Log4j2    │──► Kibana / Datadog
  └──────────────┘      │      └─────────────┘
        │               │      ┌─────────────┐
        ▼               └─────►│slf4j-simple │
   (SLF4J API)                 └─────────────┘
```

Because SLF4J is only a facade, GateBridge does not enforce a specific logger implementation at runtime. Developers using the framework can bind it to any enterprise logging provider (Logback, Log4j2, java.util.logging, etc.) simply by adding the corresponding dependency to their classpath.

---

## 2. Log Levels (`LogLevel` Enum)

All logging events in GateBridge are categorized under the type-safe `LogLevel` enum:

*   `LogLevel.DEBUG`: Verbose diagnostics (e.g., dispatching events, thread allocations).
*   `LogLevel.INFO`: standard operational lifecycle milestones (e.g., transports bound, nodes registered).
*   `LogLevel.WARN`: Non-fatal issues (e.g., connection retries, configuration fallbacks).
*   `LogLevel.ERROR`: Critical failures (e.g., connection refused, failed health checks, parsing syntax errors).

---

## 3. Integrating with Log4j 2

To redirect all GateBridge core logs to a file or standard console formatter using **Log4j 2**:

### Step 1: Add dependencies to your Maven project
Add the Log4j 2 SLF4J binder and core dependencies to your application's `pom.xml`:

```xml
<dependency>
    <groupId>org.slf4j</groupId>
    <artifactId>slf4j-api</artifactId>
    <version>2.0.12</version>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-slf4j-impl</artifactId>
    <version>2.23.1</version>
</dependency>
<dependency>
    <groupId>org.apache.logging.log4j</groupId>
    <artifactId>log4j-core</artifactId>
    <version>2.23.1</version>
</dependency>
```

### Step 2: Configure logging outputs (`log4j2.xml`)
Create a file named `log4j2.xml` under your project's resources directory (`src/main/resources/log4j2.xml`) to format and direct the logs:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
    <Appenders>
        <!-- 1. Console Appender -->
        <Console name="Console" target="SYSTEM_OUT">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
        </Console>

        <!-- 2. Rolling File Appender (with automatic gzip compression) -->
        <RollingFile name="FileAppender" 
                     fileName="logs/gatebridge.log" 
                     filePattern="logs/gatebridge-%d{yyyy-MM-dd}-%i.log.gz">
            <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n"/>
            <Policies>
                <TimeBasedTriggeringPolicy interval="1" modulate="true" />
                <SizeBasedTriggeringPolicy size="10 MB" />
            </Policies>
            <DefaultRolloverStrategy max="10"/>
        </RollingFile>
    </Appenders>

    <Loggers>
        <!-- Route all internal GateBridge logs to Console and File -->
        <Logger name="hexacloud.core.utils.DebugUtils" level="info" additivity="false">
            <AppenderRef ref="Console"/>
            <AppenderRef ref="FileAppender"/>
        </Logger>

        <!-- Default fallback root logger -->
        <Root level="warn">
            <AppenderRef ref="Console"/>
        </Root>
    </Loggers>
</Configuration>
```

---

## 4. TUI Interception & Redirection

When the **DevOps Dashboard TUI** is active (`setTuiModeActive(true)`), GateBridge automatically redirects `System.out` and `System.err` streams to a memory buffer so they do not print raw text and mess up the terminal screen alignment. 

*   **Redirection to TUI Logs Box:** The TUI parses intercepted logs and displays them inside the `RECENT SYSTEM LOGS` panel in real-time.
*   **LogListener Callback:** Whenever a log is recorded, a callback notifies the Terminal UI to trigger a low-latency redraw of the dashboard.
*   **Exception Stacktraces:** When using `DebugUtils.error("Message", throwable)`, the TUI displays a clean, single-line representation of the error, while the SLF4J logger receives the full detailed stacktrace context.
