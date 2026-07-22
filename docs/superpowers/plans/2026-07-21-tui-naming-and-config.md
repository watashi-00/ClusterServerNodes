# TUI Naming & Project Configuration Display Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enhance the DevOps TUI console to display the custom Gateway/Cluster names and project configurations (transports like L4 TCP Proxy, ports, routing modes, node type/mutability) by increasing view box heights and layouts cleanly.

**Architecture:** Extend TUI view renderers to draw boxes with 1-row increased height (up to y=14) to fit new fields. Add TCP Proxy to the dashboard transport table, Routing Mode to cluster configuration headers/details, and node mutability (Static vs Dynamic) to the node configuration panel.

**Tech Stack:** Java 21, Maven

## Global Constraints
- Target Java Version: 21.
- All code changes must compile and pass cleanly.
- Ensure terminal y-coordinate alignments are shifted consistently (boxes to 14, lists to 14, logs/events to start at 15).

---

### Task 1: Expose and Populate TCP Proxy transport status in TUI

**Files:**
- Modify: `java/src/hexacloud/core/ports/RunningGatewayPort.java`
- Modify: `java/src/hexacloud/infra/gateway/LocalGatewayAdapter.java`
- Modify: `java/src/hexacloud/core/tui/TuiState.java`
- Modify: `java/src/hexacloud/core/tui/TerminalUI.java`

**Interfaces:**
- Consumes: `LocalGatewayAdapter.isTcpProxyEnabled()`, `TuiState.GatewayConfig`
- Produces: `RunningGatewayPort.isTcpProxyEnabled()`, TUI state populated with TCP Proxy enabled state

- [ ] **Step 1: Verify current changes are present in workspace**

Check `git diff` for `RunningGatewayPort.java`, `LocalGatewayAdapter.java`, `TuiState.java`, and `TerminalUI.java`.

- [ ] **Step 2: Compile the project to verify changes are correct**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit the baseline gateway config exposure**

```bash
git add java/src/hexacloud/core/ports/RunningGatewayPort.java java/src/hexacloud/infra/gateway/LocalGatewayAdapter.java java/src/hexacloud/core/tui/TuiState.java java/src/hexacloud/core/tui/TerminalUI.java
git commit -m "feat: expose and populate tcpProxyEnabled in TUI state"
```

---

### Task 2: Enhance Dashboard View layout and show TCP Proxy & Routing Mode

**Files:**
- Modify: `java/src/hexacloud/core/tui/view/DashboardViewRenderer.java`

**Interfaces:**
- Consumes: `TuiState.GatewayConfig.tcpProxyEnabled`, `Cluster.getRoutingMode()`
- Produces: Expanded boxes layout (y=14) and renders TCP Proxy status on port + 3

- [ ] **Step 1: Increase box y-coordinates and shift bottom panel starting positions**

In `DashboardViewRenderer.java`, update the box dimensions (around lines 40-50):
- Change `GATEWAYS` box bottom to `14`.
- Change middle box bottom to `14`.
- Change `GATEWAYS & SYSTEM` box bottom to `14`.
- Change `RECENT SYSTEM LOGS` box top to `15`.
- Change `RECENT EVENTS` box top to `15`.

Also:
- Update `GATEWAYS` list boundary: check `yGateway >= 14` instead of `yGateway >= 13`, and clear list up to `14` (lines 88 and 108).
- Update logs start y to `16` and `logsLimit = (H - 2) - 15 - 1` (lines 215 and 217).
- Update events start y to `16` (line 305).

- [ ] **Step 2: Display TCP Proxy in the Gateways config table**

In `DashboardViewRenderer.java` (around lines 135-150), calculate the status for TCP Proxy and print it:
```java
                String wsStatus = (gw.running && gw.wsEnabled) ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;
                String tcpProxyStatus = (gw.running && gw.tcpProxyEnabled) ? GREEN + "ONLINE" + RESET : RED + "OFFLINE" + RESET;

                NativeTerminal.printAt(28, yGw++, String.format("  %-" + hostColWidth + "s %-8d %-14s", "Telnet Console (CLI)", port, telnetStatus));
                NativeTerminal.printAt(28, yGw++, String.format("  %-" + hostColWidth + "s %-8d %-14s", "HTTP REST API (JSON)", port + 1, httpStatus));
                NativeTerminal.printAt(28, yGw++, String.format("  %-" + hostColWidth + "s %-8d %-14s", "WebSocket Stream (JSON)", port + 2, wsStatus));
                NativeTerminal.printAt(28, yGw++, String.format("  %-" + hostColWidth + "s %-8d %-14s", "TCP Proxy (L4 LoadBalancer)", port + 3, tcpProxyStatus));
```
Update the clear loop to go up to `13` (line 148).

- [ ] **Step 3: Display Routing Mode in the Clusters config header**

In `DashboardViewRenderer.java` (around line 159), display the cluster's routing mode:
```java
            hexacloud.core.cluster.Cluster currentCluster = hexacloud.core.cluster.ClusterRegistry.getInstance().getCluster(state.selectedClusterName);
            String modeStr = (currentCluster != null) ? currentCluster.getRoutingMode().name() : "N/A";
            NativeTerminal.printAt(28, 6, WHITE_BOLD + "Active:   " + RESET + state.selectedClusterName + " | Mode: " + YELLOW + modeStr + RESET + " | Gateway: " + gwStatus + portSuffix + RESET);
```
Increase the middle services viewport adjustment and display limit to `4` (since box is taller):
- Change `tui.adjustServicesViewport(3)` to `tui.adjustServicesViewport(4)`.
- Change loop limit `i < 3` to `i < 4`.
- Change scroll check limit `start + 3` to `start + 4`, and print arrow at `13` (lines 205-206).
- Update services clear loop to go up to `13`.

- [ ] **Step 4: Verify Compile**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add java/src/hexacloud/core/tui/view/DashboardViewRenderer.java
git commit -m "feat: display TCP proxy status, routing mode, and expand dashboard layout"
```

---

### Task 3: Enhance Cluster Detail View and show Routing Mode

**Files:**
- Modify: `java/src/hexacloud/core/tui/view/ClusterDetailViewRenderer.java`

**Interfaces:**
- Consumes: `Cluster.getRoutingMode()`
- Produces: Expanded detail view layout and renders Routing Mode policy

- [ ] **Step 1: Increase box y-coordinates and shift bottom panel starting positions**

In `ClusterDetailViewRenderer.java`, update the box dimensions (around lines 35-37):
- Change `POLICIES & LIMITS` box bottom to `14`.
- Change `SERVICES / TELEMETRY` box bottom to `14`.
- Change `CONSOLE LOGS` box top to `15`.

Also:
- Update logs start y to `16` (line 103).
- Update services scroll indicators check to check `start + 7 < size` and print arrow at `13` (line 94).
- Update clear loop to go up to `13` (line 98).
- Change `tui.adjustServicesViewport(6)` to `tui.adjustServicesViewport(7)`.
- Change loop limit `i < 6` to `i < 7`.

- [ ] **Step 2: Display Routing Mode in policies box**

Add routing mode parameter to the bottom of policies box (Row 13):
```java
        NativeTerminal.printAt(4, 11, "Timeout:  " + state.targetTimeoutMs + " ms");
        NativeTerminal.printAt(4, 12, "Ping Int: " + state.globalPingInterval + "s");
        String routeMode = currentCluster != null ? currentCluster.getRoutingMode().name() : "N/A";
        NativeTerminal.printAt(4, 13, "RouteMode:" + YELLOW + routeMode + RESET);
```

- [ ] **Step 3: Verify Compile**

Run: `mvn compile`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add java/src/hexacloud/core/tui/view/ClusterDetailViewRenderer.java
git commit -m "feat: display Routing Mode in Cluster Detail View"
```

---

### Task 4: Enhance Node Configuration View and show mutability (Static vs Dynamic)

**Files:**
- Modify: `java/src/hexacloud/core/tui/view/NodeConfigViewRenderer.java`

**Interfaces:**
- Consumes: `ServerNode.isDynamic()`
- Produces: Expanded configuration view and renders node type (STATIC vs DYNAMIC)

- [ ] **Step 1: Increase box y-coordinates and shift bottom panel starting positions**

In `NodeConfigViewRenderer.java`, update the box dimensions (around lines 39-94):
- Change `NODE CONFIGURATION` box bottom to `14`.
- Change `CONSOLE LOGS FOR SERVICE` box top to `15`.

Also:
- Update logs start y to `16` (line 96).

- [ ] **Step 2: Display Node Mutability Type**

Add node type line to configuration panel (Row 13):
```java
        NativeTerminal.printAt(4, 11, (canEdit ? WHITE_BOLD + "[H] " : "") + "Header: " + RESET + CYAN + headerName + RESET);
        NativeTerminal.printAt(4, 12, (canEdit ? WHITE_BOLD + "[V] " : "") + "Token:  " + RESET + CYAN + headerVal + RESET);
        NativeTerminal.printAt(4, 13, "Mutability: " + (node.isDynamic() ? YELLOW + "DYNAMIC" : GREEN + "STATIC") + RESET);
```

- [ ] **Step 3: Run full tests to verify**

Run: `mvn clean test`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add java/src/hexacloud/core/tui/view/NodeConfigViewRenderer.java
git commit -m "feat: display Mutability Type in Node Config View"
```
