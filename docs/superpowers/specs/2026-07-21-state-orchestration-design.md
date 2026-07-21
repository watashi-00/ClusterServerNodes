# Design Spec: GateBridge State Orchestration & Persistence Layer

This document details the refactoring of the cluster state persistence layer to enforce consistency between code-defined (bootstrap) configurations and runtime (TUI/API) dynamic orchestration, and introduce section-based comments in the state properties file.

## 1. Context and Goals
GateBridge dynamically serializes and restores cluster configurations using a file-based `.state` properties mechanism. Currently, this introduces several synchronization bugs:
- **Remote Deletion Regression:** If a user deletes a code-defined server node remotely (via TUI or API/control panel) and the service is restarted, the node is re-registered by the bootstrap code because the system cannot distinguish between a deleted static node and a new static node.
- **Code Deletion Stale State:** If a developer deletes a static node from their bootstrap code (e.g. `Main.java`) and restarts the server, the node is still restored from the `.state` file on disk because the old state is loaded unconditionally.
- **Lack of Readability:** Properties files are serialized as flat, unsorted key-value pairs making manual inspection and debugging difficult.

### Core Goals:
1. Automatically distinguish between code-defined static nodes and runtime-defined dynamic nodes.
2. Maintain strict consistency:
   - Remote node deletions must be persistent and not regression-prone across restarts.
   - Code-level node deletions must prune the disk state on next boot.
3. Output the `.state` files with clean, section-delimited block comments for enhanced readability.

---

## 2. Technical Architecture

### 2.1. ServerNode Class Enhancements
- Introduce a new private immutable property `private final boolean isDynamic;` (defaulting to `false` for backwards compatibility).
- Expose `public boolean isDynamic() { return isDynamic; }`.
- Expose a modifier method `public ServerNode withDynamic(boolean isDynamic)`.

### 2.2. Cluster Class Lifecycle & Registries
We introduce three collections and a lifecycle state to `Cluster.java` to track static nodes:
- `private final Set<String> staticNodes = new ConcurrentHashSet<>();` - Tracks all static nodes registered in the cluster.
- `private final Set<String> persistedStaticNodes = new ConcurrentHashSet<>();` - Tracks the static nodes loaded from the state file.
- `private final Set<String> registeredStaticNodesThisRun = new ConcurrentHashSet<>();` - Tracks the static nodes registered during the current bootstrap execution.
- `private boolean bootstrapMode = true;` - Indicates if the cluster is in the bootstrap configuration phase.

#### Lifecycle Phases:
1. **State Loading:** Sockets are not yet listening. The `ClusterStatePersistence.loadState()` is called. It uses `cluster.registerLoadedServer(ServerNode)` to register nodes from the properties file:
   - If the node has `isDynamic == false` (static), it is added to `staticNodes` and `persistedStaticNodes`.
   - The node is added to the active `cluster` map.
2. **Bootstrap Registration:** The bootstrap code (e.g., `Main.java`) registers static nodes. `bootstrapMode` is `true`.
   - Any registered node is added to `registeredStaticNodesThisRun` and `staticNodes`.
   - If `ClusterStatePersistence.isStateLoaded()` is `true`:
     - We check if the node's full host is in `persistedStaticNodes`.
     - If it was in `persistedStaticNodes` but is **not** currently active in the cluster map (meaning it was deleted remotely), we skip registration.
     - If it was not in `persistedStaticNodes`, it's a new static node added to code, so we proceed to register it.
3. **Execution Start:** When `listen()` is triggered:
   - We invoke `cluster.endBootstrapPhase()`.
   - Any node in `persistedStaticNodes` that was **not** registered in `registeredStaticNodesThisRun` has been deleted from code. We prune it from `cluster` map and from all registries.
   - We transition `bootstrapMode = false`.
   - We write the clean state using `ClusterStatePersistence.saveState()`.
4. **Runtime Operations:** Any node registered after `listen()` is dynamic and has `isDynamic = true`.

---

## 3. State File Serialization Format

We will replace the default `Properties.store()` method with a manual writer to output comments and section blocks.

### File Format Example (`.state/watashi-cluster-state.properties`):
```properties
# =========================================================
# Persisted GateBridge Cluster Gateway State for watashi-cluster
# Generated automatically - DO NOT EDIT MANUALLY unless necessary
# =========================================================

# === BEGIN CLUSTER CONFIG ===
cluster.watashi-cluster.requireToken=true
cluster.watashi-cluster.timeoutMs=5000
cluster.watashi-cluster.allowedIps=127.0.0.1
cluster.watashi-cluster.rateLimitRequests=200
cluster.watashi-cluster.rateLimitDurationSeconds=60
# === END CLUSTER CONFIG ===

# === BEGIN NODE node-http-1 ===
node.watashi-cluster.node-http-1.host=http://localhost
node.watashi-cluster.node-http-1.port=3006
node.watashi-cluster.node-http-1.isExternal=false
node.watashi-cluster.node-http-1.isDynamic=false
node.watashi-cluster.node-http-1.pingProtocol=HTTP
node.watashi-cluster.node-http-1.pingPath=/health
node.watashi-cluster.node-http-1.pingHeaderName=X-Cluster-Token
# === END NODE node-http-1 ===

# === BEGIN NODE node-grpc-1 ===
node.watashi-cluster.node-grpc-1.host=http://localhost
node.watashi-cluster.node-grpc-1.port=3007
node.watashi-cluster.node-grpc-1.isExternal=false
node.watashi-cluster.node-grpc-1.isDynamic=true
node.watashi-cluster.node-grpc-1.pingProtocol=GRPC
node.watashi-cluster.node-grpc-1.pingPath=/grpc-health
# === END NODE node-grpc-1 ===

# === BEGIN NODE LIST ===
cluster.watashi-cluster.nodes=node-http-1,node-grpc-1
cluster.watashi-cluster.staticNodes=node-http-1
# === END NODE LIST ===
```

---

## 4. Verification and Testing Plan
- **State Serialization Integration Test:** Write a test verifying that:
  - Deleting a node remotely writes the updated state file without the node.
  - Restarting does not restore the deleted node.
  - Adding/removing nodes in code-level configuration correctly updates/prunes the state file upon restart.
- **Section Parsing Validation:** Confirm that the standard `Properties.load()` correctly ignores section comments and parses properties accurately.
