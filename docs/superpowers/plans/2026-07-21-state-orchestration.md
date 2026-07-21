# State Orchestration & CRUD Persistence Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Refactor the state persistence and dynamic CRUD logic to differentiate between static bootstrap nodes and dynamic runtime nodes, ensuring deletion/addition consistency across service restarts, and serialize states with clean section comments.

**Architecture:** Add an `isDynamic` property to `ServerNode`. Distinguish between bootstrap-phase registration and runtime dynamic registration in `Cluster`. Maintain two distinct node keys (`nodes` and `staticNodes`) in properties files. Serializing uses manual formatting block comments.

**Tech Stack:** Java 21, JUnit 5, Maven

## Global Constraints
- Target Java Version: 21 (base sources in `java/src`).
- All code changes must compile and pass cleanly without deprecation warnings (specifically avoiding `new URL(String)`).
- Preserve existing comments/docstrings in target files.

---

### Task 1: Differentiate Nodes Mutability with `isDynamic` in `ServerNode`

**Files:**
- Modify: `java/src/hexacloud/core/model/ServerNode.java`
- Modify: `java/test/hexacloud/core/model/ServerNodeTest.java`

**Interfaces:**
- Consumes: None
- Produces: `ServerNode.isDynamic()`, `ServerNode.withDynamic(boolean)`

- [ ] **Step 1: Write a failing unit test in `ServerNodeTest.java`**

Add this test at the end of the file:
```java
    @org.junit.jupiter.api.Test
    public void testIsDynamicDefaultAndModifier() {
        ServerNode node = new ServerNode("node-test", "http://localhost", 9091, NodeStatus.OFFLINE, false);
        org.junit.jupiter.api.Assertions.assertFalse(node.isDynamic(), "Should default to false");
        
        ServerNode dynamicNode = node.withDynamic(true);
        org.junit.jupiter.api.Assertions.assertTrue(dynamicNode.isDynamic(), "Modified node should be dynamic");
        org.junit.jupiter.api.Assertions.assertEquals("node-test", dynamicNode.name());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn test -Dtest=ServerNodeTest`
Expected: Compilation failure (no `isDynamic` method)

- [ ] **Step 3: Write implementation in `ServerNode.java`**

Add the field `private final boolean isDynamic;` to `ServerNode.java`:
```java
    private final boolean isDynamic;
```
Initialize it to `false` in all constructors, and add a primary constructor overload that accepts `isDynamic`:
```java
    public ServerNode(String name, String host, int port, NodeStatus status, boolean isExternal,
                      PingProtocol pingProtocol, String pingPath, String pingHeaderName, String pingHeaderValue, boolean isDynamic) {
        this.name = name != null && !name.isEmpty() ? name : (host + ":" + port);
        this.host = host;
        this.port = port;
        this.status = status;
        this.isExternal = isExternal;
        this.pingProtocol = pingProtocol != null ? pingProtocol : PingProtocol.HTTP;
        this.pingPath = pingPath != null ? pingPath : "/";
        this.pingHeaderName = pingHeaderName;
        this.pingHeaderValue = pingHeaderValue;
        this.isDynamic = isDynamic;
    }
```
Update legacy constructors to default `isDynamic = false` by chaining to the primary constructor:
```java
    public ServerNode(String name, String host, int port, NodeStatus status, boolean isExternal,
                      PingProtocol pingProtocol, String pingPath, String pingHeaderName, String pingHeaderValue) {
        this(name, host, port, status, isExternal, pingProtocol, pingPath, pingHeaderName, pingHeaderValue, false);
    }
```
Implement the new methods:
```java
    public boolean isDynamic() {
        return isDynamic;
    }

    public ServerNode withDynamic(boolean isDynamic) {
        ServerNode node = new ServerNode(this.name, this.host, this.port, this.status, this.isExternal,
                this.pingProtocol, this.pingPath, this.pingHeaderName, this.pingHeaderValue, isDynamic);
        node.setLatencyMs(this.latencyMs);
        node.setCpuUsage(this.cpuUsage);
        node.setRamUsage(this.ramUsage);
        node.setRuntime(this.runtime);
        return node;
    }
```
Also update `withStatus` and `withPingProtocol` in `ServerNode.java` to propagate the `isDynamic` flag:
```java
    public ServerNode withStatus(NodeStatus newStatus) {
        ServerNode node = new ServerNode(this.name, this.host, this.port, newStatus, this.isExternal,
                this.pingProtocol, this.pingPath, this.pingHeaderName, this.pingHeaderValue, this.isDynamic);
        ...
```
```java
    public ServerNode withPingProtocol(PingProtocol newProtocol) {
        ServerNode node = new ServerNode(this.name, this.host, this.port, this.status, this.isExternal,
                newProtocol, this.pingPath, this.pingHeaderName, this.pingHeaderValue, this.isDynamic);
        ...
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn test -Dtest=ServerNodeTest`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/core/model/ServerNode.java java/test/hexacloud/core/model/ServerNodeTest.java
git commit -m "feat: add isDynamic field and logic to ServerNode"
```

---

### Task 2: Implement Static/Dynamic Lifecycle in `Cluster`

**Files:**
- Modify: `java/src/hexacloud/core/cluster/Cluster.java`

**Interfaces:**
- Consumes: `ServerNode.isDynamic()`, `ServerNode.withDynamic(boolean)`
- Produces: `Cluster.registerLoadedServer(ServerNode)`, `Cluster.endBootstrapPhase()`, `Cluster.getStaticNodes()`, `Cluster.getPersistedStaticNodes()`

- [ ] **Step 1: Declare lifecycle registries and getters in `Cluster.java`**

Add these fields to `Cluster.java` class body:
```java
    private final java.util.Set<String> staticNodes = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().newKeySet();
    private final java.util.Set<String> persistedStaticNodes = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().newKeySet();
    private final java.util.Set<String> registeredStaticNodesThisRun = new java.util.concurrent.ConcurrentHashMap<String, Boolean>().newKeySet();
    private boolean bootstrapMode = true;
```
Add public methods to expose these sets:
```java
    public java.util.Set<String> getStaticNodes() {
        return staticNodes;
    }

    public java.util.Set<String> getPersistedStaticNodes() {
        return persistedStaticNodes;
    }
```

- [ ] **Step 2: Add `registerLoadedServer` and update `registerServer`**

Implement `registerLoadedServer` in `Cluster.java`:
```java
    public void registerLoadedServer(ServerNode node) {
        lock.lock();
        try {
            String fullHost = node.getFullHost();
            if (!node.isDynamic()) {
                staticNodes.add(fullHost);
                persistedStaticNodes.add(fullHost);
            }
            addClusterNode(node);
        } finally {
            lock.unlock();
        }
    }
```
Update `registerServer(ServerNode node)` to enforce bootstrap/loading rules:
```java
    public void registerServer(ServerNode node) {
        lock.lock();
        try {
            String fullHost = node.getFullHost();
            if (bootstrapMode) {
                registeredStaticNodesThisRun.add(fullHost);
                staticNodes.add(fullHost);
                
                // If a state file was loaded, respect remote deletions of static nodes
                if (hexacloud.core.config.ClusterStatePersistence.isStateLoaded()) {
                    if (persistedStaticNodes.contains(fullHost) && !cluster.containsKey(fullHost)) {
                        return; // Ignore/Respect remote deletion
                    }
                }
            } else {
                // Dynamically registered at runtime
                node = node.withDynamic(true);
            }
            addClusterNode(node);
        } finally {
            lock.unlock();
        }
    }
```

- [ ] **Step 3: Implement `endBootstrapPhase`**

Implement `endBootstrapPhase` in `Cluster.java`:
```java
    public void endBootstrapPhase() {
        lock.lock();
        try {
            this.bootstrapMode = false;
            if (hexacloud.core.config.ClusterStatePersistence.isStateLoaded()) {
                java.util.List<String> toPrune = new java.util.ArrayList<>();
                for (String fullHost : persistedStaticNodes) {
                    if (!registeredStaticNodesThisRun.contains(fullHost)) {
                        toPrune.add(fullHost);
                    }
                }
                for (String fullHost : toPrune) {
                    cluster.remove(fullHost);
                    staticNodes.remove(fullHost);
                    persistedStaticNodes.remove(fullHost);
                }
                if (!toPrune.isEmpty()) {
                    hexacloud.core.config.ClusterStatePersistence.saveState();
                }
            }
        } finally {
            lock.unlock();
        }
    }
```

- [ ] **Step 4: Verify Compilation**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/core/cluster/Cluster.java
git commit -m "feat: add bootstrap lifecycle and static registries to Cluster"
```

---

### Task 3: Connect Lifecycle and Remove State Bypasses in Adapter/Builder

**Files:**
- Modify: `java/src/hexacloud/infra/gateway/LocalGatewayAdapter.java`
- Modify: `java/src/hexacloud/infra/gateway/NodeBuilder.java`

**Interfaces:**
- Consumes: `Cluster.endBootstrapPhase()`, `Cluster.registerServer(ServerNode)`
- Produces: Corrected builder/adapter calls

- [ ] **Step 1: Update `LocalGatewayAdapter.java`**

Remove the state loaded bypass in `registerServer` methods:
```java
    @Override
    public LocalGatewayAdapter registerServer(ServerNode node) {
        clusterManager.registerServer(node);
        return this;
    }
```
Update `registerServer(int port, NodeStatus status)` too:
```java
    public LocalGatewayAdapter registerServer(int port, NodeStatus status) {
        clusterManager.registerServer(port, status);
        return this;
    }
```
Inside `listen(int port)` (around line 215), invoke `endBootstrapPhase()` right before starting listeners:
```java
    @Override
    public LocalGatewayAdapter listen(int port) {
        this.port = port;
        ensureServerManagerInitialized();
        this.clusterManager.getCluster().endBootstrapPhase(); // Transition cluster to runtime
        DebugUtils.log("LocalGatewayAdapter: Starting server listeners on port " + port);
```

- [ ] **Step 2: Update `NodeBuilder.java`**

Remove the `isStateLoaded` check in `register()` (lines 70-73):
```java
    @Override
    public hexacloud.core.ports.GatewayBuilderPort register() {
        ServerNode node = new ServerNode(
            name, host, port, NodeStatus.OFFLINE, isExternal,
            pingEnabled, pingPath, pingHeaderName, pingHeaderValue
        );
        cluster.registerServer(node);
        return parent;
    }
```

- [ ] **Step 3: Run the existing tests**

Run: `mvn test`
Expected: BUILD SUCCESS (Verify that current tests still pass)

- [ ] **Step 4: Commit**

```bash
git add java/src/hexacloud/infra/gateway/LocalGatewayAdapter.java java/src/hexacloud/infra/gateway/NodeBuilder.java
git commit -m "refactor: connect gateway lifecycle to Cluster bootstrap phase"
```

---

### Task 4: Custom Comments Serialization and Parsing in `ClusterStatePersistence`

**Files:**
- Modify: `java/src/hexacloud/core/config/ClusterStatePersistence.java`

**Interfaces:**
- Consumes: `Cluster.getStaticNodes()`, `Cluster.getPersistedStaticNodes()`, `ServerNode.isDynamic()`
- Produces: Sectioned file serialization and distinct dynamic deserialization

- [ ] **Step 1: Modify `saveClusterState` in `ClusterStatePersistence.java`**

Rewrite `saveClusterState(Cluster cluster)` to write comments and sections line-by-line:
```java
    private static void saveClusterState(Cluster cluster) {
        String name = cluster.getClusterName();
        String filePath = resolveStateFilePath(name);

        try (java.io.PrintWriter writer = new java.io.PrintWriter(new java.io.FileOutputStream(filePath))) {
            writer.println("# =========================================================");
            writer.println("# Persisted GateBridge Cluster Gateway State for " + name);
            writer.println("# Generated automatically - DO NOT EDIT MANUALLY unless necessary");
            writer.println("# =========================================================");
            writer.println();

            writer.println("# === BEGIN CLUSTER CONFIG ===");
            String prefix = "cluster." + name + ".";
            writer.println(prefix + "requireToken=" + cluster.isRequireToken());
            writer.println(prefix + "timeoutMs=" + cluster.getTimeoutMs());
            writer.println(prefix + "allowedIps=" + (cluster.getAllowedIps() != null ? cluster.getAllowedIps() : ""));
            writer.println(prefix + "rateLimitRequests=" + cluster.getRateLimitRequests());
            writer.println(prefix + "rateLimitDurationSeconds=" + cluster.getRateLimitDurationSeconds());
            writer.println("# === END CLUSTER CONFIG ===");
            writer.println();

            java.util.List<String> nodeKeys = new java.util.ArrayList<>();
            for (ServerNode node : cluster.getCluster()) {
                String nodeKey = node.getFullHost();
                nodeKeys.add(nodeKey);

                writer.println("# === BEGIN NODE " + nodeKey + " ===");
                String nodePrefix = "node." + name + "." + nodeKey + ".";
                writer.println(nodePrefix + "host=" + node.host());
                writer.println(nodePrefix + "port=" + node.port());
                writer.println(nodePrefix + "isExternal=" + node.isExternal());
                writer.println(nodePrefix + "isDynamic=" + node.isDynamic());
                writer.println(nodePrefix + "pingEnabled=" + node.pingEnabled());
                writer.println(nodePrefix + "pingProtocol=" + node.pingProtocol().name());
                writer.println(nodePrefix + "pingPath=" + node.pingPath());
                if (node.pingHeaderName() != null) {
                    writer.println(nodePrefix + "pingHeaderName=" + node.pingHeaderName());
                }
                writer.println("# === END NODE " + nodeKey + " ===");
                writer.println();
            }

            writer.println("# === BEGIN NODE LIST ===");
            writer.println(prefix + "nodes=" + String.join(",", nodeKeys));
            
            java.util.List<String> staticKeys = new java.util.ArrayList<>(cluster.getStaticNodes());
            writer.println(prefix + "staticNodes=" + String.join(",", staticKeys));
            writer.println("# === END NODE LIST ===");
            writer.println();

            DebugUtils.log("DevOps Panel: Saved active configurations state to " + filePath);
        } catch (IOException e) {
            DebugUtils.error("DevOps Panel: Failed to save state file for cluster " + name, e);
        }
    }
```

- [ ] **Step 2: Modify `loadClusterStateProperties` to parse new fields**

Update `loadClusterStateProperties(Properties props, String name)`:
```java
        // Load persistedStaticNodes list
        String staticNodesStr = props.getProperty(prefix + "staticNodes");
        if (staticNodesStr != null && !staticNodesStr.trim().isEmpty()) {
            for (String staticKey : staticNodesStr.split(",")) {
                cluster.getPersistedStaticNodes().add(staticKey.trim());
            }
        }

        String nodesStr = props.getProperty(prefix + "nodes");
        if (nodesStr != null && !nodesStr.trim().isEmpty()) {
            for (String nodeKey : nodesStr.split(",")) {
                nodeKey = nodeKey.trim();
                if (nodeKey.isEmpty()) continue;

                String nodePrefix = "node." + name + "." + nodeKey + ".";
                String host = props.getProperty(nodePrefix + "host");
                int port = Integer.parseInt(props.getProperty(nodePrefix + "port"));
                boolean isExternal = Boolean.parseBoolean(props.getProperty(nodePrefix + "isExternal", "false"));
                boolean isDynamic = Boolean.parseBoolean(props.getProperty(nodePrefix + "isDynamic", "false"));
                String pingPath = props.getProperty(nodePrefix + "pingPath", "/");
                String pingHeaderName = props.getProperty(nodePrefix + "pingHeaderName", null);

                String protoStr = props.getProperty(nodePrefix + "pingProtocol");
                PingProtocol pingProtocol;
                if (protoStr != null) {
                    try {
                        pingProtocol = PingProtocol.valueOf(protoStr.toUpperCase());
                    } catch (Exception e) {
                        pingProtocol = PingProtocol.HTTP;
                    }
                } else {
                    boolean pingEnabled = Boolean.parseBoolean(props.getProperty(nodePrefix + "pingEnabled", "true"));
                    pingProtocol = pingEnabled ? PingProtocol.HTTP : PingProtocol.NONE;
                }

                ServerNode node = new ServerNode(
                    host, port, NodeStatus.OFFLINE, isExternal,
                    pingProtocol, pingPath, pingHeaderName, null
                ).withDynamic(isDynamic);
                
                boolean alreadyRegistered = cluster.getCluster().stream()
                    .anyMatch(n -> n.getFullHost().equals(node.getFullHost()));
                if (!alreadyRegistered) {
                    cluster.registerLoadedServer(node);
                }
            }
        }
```

- [ ] **Step 3: Run the compiler to check**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add java/src/hexacloud/core/config/ClusterStatePersistence.java
git commit -m "feat: serialize staticNodes and format state files with block comments"
```

---

### Task 5: Integration Tests and Verification

**Files:**
- Create: `java/test/hexacloud/core/config/StateOrchestrationTest.java`

**Interfaces:**
- Consumes: `ClusterStatePersistence.loadState()`, `ClusterStatePersistence.saveState()`, `LocalGatewayAdapter`
- Produces: Verification assertions

- [ ] **Step 1: Create `StateOrchestrationTest.java`**

Create `java/test/hexacloud/core/config/StateOrchestrationTest.java` with test cases verifying remote deletion, code deletion, and dynamic registration behaviors:
```java
package hexacloud.core.config;

import static org.junit.jupiter.api.Assertions.*;

import java.io.File;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.infra.gateway.LocalGatewayAdapter;

public class StateOrchestrationTest {

    private final String clusterName = "test-orchestration-cluster";
    private final String stateDirPath = "./.test_state";

    @BeforeEach
    public void setUp() {
        System.setProperty("hexacloud.state.dir", stateDirPath);
        deleteTestStateDir();
        ClusterRegistry.getInstance().clear();
    }

    @AfterEach
    public void tearDown() {
        deleteTestStateDir();
        System.clearProperty("hexacloud.state.dir");
    }

    private void deleteTestStateDir() {
        File dir = new File(stateDirPath);
        if (dir.exists()) {
            File[] files = dir.listFiles();
            if (files != null) {
                for (File f : files) {
                    f.delete();
                }
            }
            dir.delete();
        }
    }

    @Test
    public void testRemoteDeletionAndBootstrapPersistence() {
        // 1. Initial run: bootstrap registers two static nodes A & B
        LocalGatewayAdapter gateway = new LocalGatewayAdapter(clusterName);
        gateway.registerServer(new ServerNode("node-a", "http://localhost", 7001, NodeStatus.OFFLINE, false));
        gateway.registerServer(new ServerNode("node-b", "http://localhost", 7002, NodeStatus.OFFLINE, false));
        
        // Starts listening (end of bootstrap)
        gateway.listen(7000);
        
        Cluster cluster = gateway.getCluster();
        assertEquals(2, cluster.getCluster().size());
        
        // 2. User deletes node A remotely (simulated)
        gateway.deregisterServer("http://localhost:7001");
        assertEquals(1, cluster.getCluster().size());
        
        // Stops gateway
        gateway.stop();
        
        // 3. Restart: Reload state from disk and run bootstrap code registering A & B again
        LocalGatewayAdapter restartedGateway = new LocalGatewayAdapter(clusterName);
        // Bootstrap tries to register A & B again
        restartedGateway.registerServer(new ServerNode("node-a", "http://localhost", 7001, NodeStatus.OFFLINE, false));
        restartedGateway.registerServer(new ServerNode("node-b", "http://localhost", 7002, NodeStatus.OFFLINE, false));
        
        restartedGateway.listen(7000);
        
        // Node A should NOT return because the remote deletion is respected
        Cluster restartedCluster = restartedGateway.getCluster();
        assertEquals(1, restartedCluster.getCluster().size(), "Remote deletion of node-a must be persistent");
        assertNull(restartedCluster.getCluster().stream().filter(n -> n.port() == 7001).findFirst().orElse(null));
        assertNotNull(restartedCluster.getCluster().stream().filter(n -> n.port() == 7002).findFirst().orElse(null));
        
        restartedGateway.stop();
    }

    @Test
    public void testCodeDeletionPruning() {
        // 1. Initial run: bootstrap registers static nodes A & B
        LocalGatewayAdapter gateway = new LocalGatewayAdapter(clusterName);
        gateway.registerServer(new ServerNode("node-a", "http://localhost", 7001, NodeStatus.OFFLINE, false));
        gateway.registerServer(new ServerNode("node-b", "http://localhost", 7002, NodeStatus.OFFLINE, false));
        gateway.listen(7000);
        gateway.stop();

        // 2. Developer removes A from Main.java (bootstrap code)
        LocalGatewayAdapter restartedGateway = new LocalGatewayAdapter(clusterName);
        // Only B is registered by code this time
        restartedGateway.registerServer(new ServerNode("node-b", "http://localhost", 7002, NodeStatus.OFFLINE, false));
        restartedGateway.listen(7000);

        // A must be pruned because it was deleted from code
        Cluster restartedCluster = restartedGateway.getCluster();
        assertEquals(1, restartedCluster.getCluster().size(), "Code-deleted node-a must be pruned");
        assertNull(restartedCluster.getCluster().stream().filter(n -> n.port() == 7001).findFirst().orElse(null));
        
        restartedGateway.stop();
    }
}
```

- [ ] **Step 2: Run the test to verify it passes**

Run: `mvn test -Dtest=StateOrchestrationTest`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add java/test/hexacloud/core/config/StateOrchestrationTest.java
git commit -m "test: add integration tests for bootstrap and remote state orchestration"
```
