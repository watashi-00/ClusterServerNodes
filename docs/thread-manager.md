# Concurrency & ThreadManager API

The `ThreadManager` is the GateBridge framework's concurrency utility class, located in `java/src/hexacloud/core/utils/ThreadManager.java`. 

It abstracts Java 21 Virtual Threads and Scheduled Executors to provide a lightweight, non-blocking runtime suitable for resource-constrained environments (like 1GB RAM vCPUs).

---

## 🚀 Core Philosophy: Virtual Threads over Platform Threads

Traditional Java applications allocate one Operating System (OS) platform thread per task. Since OS threads are expensive (consuming ~1MB of memory each for stack size and requiring context-switching overhead), scaling to thousands of concurrent pings or client connections consumes substantial system resources.

GateBridge utilizes **Virtual Threads (Project Loom)**, which:
*   Are managed by the Java Virtual Machine (JVM) in Heap memory instead of the OS kernel.
*   Are extremely lightweight (~a few kilobytes of memory) and can scale to millions of instances.
*   Automatically yield execution (park) when performing blocking operations (e.g. `Thread.sleep` or socket E/S), allowing the underlying physical carrier thread to run other tasks.

---

## 🛠️ API Reference & Usage

### 1. Starting a Virtual Thread

Use `startVirtual` to spawn a lightweight background worker. It is highly recommended to name threads for debugging clarity:

```java
import hexacloud.core.utils.ThreadManager;

// Anonymous Virtual Thread
ThreadManager.startVirtual(() -> {
    System.out.println("Running task in lightweight thread");
});

// Named Virtual Thread (Recommended)
ThreadManager.startVirtual("MyWorker", () -> {
    System.out.println("Running task under 'MyWorker' thread namespace");
});
```

### 2. Scheduled Executor Service backed by Virtual Threads

Traditional `ScheduledExecutorService` instances pin platform threads during delayed cycles. `ThreadManager` overrides this by backing the scheduler with a Virtual Thread Factory:

```java
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import hexacloud.core.utils.ThreadManager;

// Creates a scheduled pool using virtual threads instead of OS threads
ScheduledExecutorService scheduler = ThreadManager.newScheduledThreadPool(1, "PingTask");

scheduler.scheduleAtFixedRate(() -> {
    System.out.println("Checking node health...");
}, 0, 5, TimeUnit.SECONDS);
```
*Benefits:* During the 5-second sleep interval, the physical OS carrier thread is fully released back to the OS pool, reducing CPU idle consumption to `0%`.

### 3. Virtual Thread Pool Executor

For high-throughput, on-demand concurrent task execution (such as handling incoming HTTP request endpoints), use the per-task virtual thread executor:

```java
import java.util.concurrent.ExecutorService;
import hexacloud.core.utils.ThreadManager;

ExecutorService executor = ThreadManager.newVirtualThreadPool();

executor.submit(() -> {
    // Heavy network or disk E/S task
    performHeavyTask();
});
```

---

## 📊 Understanding Thread Footprint in GateBridge

When executing a GateBridge application, checking the OS Thread count (via the DevOps TUI panel or standard OS monitor tools) will display exactly **9 threads** (6 base JVM threads + 3 virtual thread management carrier threads). 

These 3 infrastructure carrier threads act as the scheduling engine for all virtual threads:
1.  **`ForkJoinPool-1-worker-X`**: The physical carrier thread running your virtual thread code.
2.  **`ForkJoinPool-1-delayScheduler`**: Handles delayed tasks (`Thread.sleep()`) without locking platform threads.
3.  **`VirtualThread-unblocker`**: Handles waking up virtual threads parked on blocking socket E/S.

Spawning new virtual workers via `ThreadManager.startVirtual()` will **not** increase the OS Thread count beyond these base carrier threads.
