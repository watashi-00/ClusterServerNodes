package hexacloud.core.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import hexacloud.core.cluster.Cluster;
import hexacloud.core.cluster.ClusterRegistry;
import hexacloud.core.model.NodeStatus;
import hexacloud.core.model.ServerNode;
import hexacloud.core.model.PingProtocol;
import hexacloud.core.utils.common.DebugUtils;

/**
 * Persistence layer to serialize and deserialize cluster and node gateway configurations.
 * Saves configuration files separately per cluster/gateway (e.g. clusterName-state.properties).
 */
public class ClusterStatePersistence {

    private static boolean stateLoaded = false;
    private static boolean loading = false;

    private ClusterStatePersistence() {}

    /**
     * Check if the gateway state has been successfully loaded from the persistence files.
     */
    public static boolean isStateLoaded() {
        return stateLoaded;
    }

    private static String getStateDirectory() {
        String dir = System.getProperty("hexacloud.state.dir");
        if(dir == null) {
            dir = System.getenv("HEXACLOUD_STATE_DIR");
        }

        if(dir == null || dir.trim().isEmpty()) {
            dir = ".state";
        }

        File dirFile = new File(dir);
        if (!dirFile.exists()) {
            dirFile.mkdirs();
        }

        return dir;
    }

    private static String resolveStateFilePath(String clusterName) {
        return getStateDirectory()+ File.separator + clusterName + "-state.properties";
    }

    /**
     * Save the current active clusters, nodes, and configurations to their respective properties files.
     */
    public static synchronized void saveState() {
        if (loading) return;
        for (Cluster cluster : ClusterRegistry.getInstance().getClusters()) {
            saveClusterState(cluster);
        }
    }

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

    /**
     * Load clusters, registered nodes, and configurations from all state properties files.
     */
    public static synchronized void loadState() {
        loading = true;
        try {
            List<File> filesToLoad = new ArrayList<>();
            
            File stateDir = new File(getStateDirectory());
            findStateFiles(stateDir, filesToLoad);

            if (filesToLoad.isEmpty()) {
                DebugUtils.log("DevOps Panel: No *-state.properties configuration files found in '" + stateDir.getPath() + "'. Checking classpath resources...");
                
                // Fallback: try to load default state file "c1" packaged inside the JAR resources
                boolean loadedFromClasspath = tryLoadFromClasspath("c1");
                if (!loadedFromClasspath) {
                    DebugUtils.log("DevOps Panel: No default state files found on classpath. Starting clean.");
                    stateLoaded = false;
                    return;
                }
                stateLoaded = true;
                return;
            }

            // Clear default code-constructed registry to avoid duplicates
            ClusterRegistry.getInstance().clear();

            List<String> loadedClusters = new ArrayList<>();
            for (File file : filesToLoad) {
                String filename = file.getName();
                String clusterName = filename.substring(0, filename.length() - "-state.properties".length());
                if (loadedClusters.contains(clusterName)) {
                    continue;
                }
                loadClusterStateFile(file, clusterName);
                loadedClusters.add(clusterName);
            }
            stateLoaded = !loadedClusters.isEmpty();
        } finally {
            loading = false;
        }
    }

    private static boolean tryLoadFromClasspath(String name) {
        String resourceName = name + "-state.properties";
        Properties props = new Properties();
        try (java.io.InputStream in = ClusterStatePersistence.class.getClassLoader().getResourceAsStream(resourceName)) {
            if (in == null) {
                return false;
            }
            props.load(in);
        } catch (IOException e) {
            DebugUtils.error("DevOps Panel: Failed to read classpath resource: " + resourceName, e);
            return false;
        }

        // Clear default registry
        ClusterRegistry.getInstance().clear();

        // Load cluster state from classpath resource properties
        loadClusterStateProperties(props, name);
        
        // Materialize properties file to disk state directory so it becomes an editable configuration file
        Cluster cluster = ClusterRegistry.getInstance().getCluster(name);
        if (cluster != null) {
            saveClusterState(cluster);
        }
        return true;
    }

    private static void findStateFiles(File dir, List<File> files) {
        if (dir.isDirectory()) {
            File[] list = dir.listFiles((d, name) -> name.endsWith("-state.properties"));
            if (list != null) {
                for (File f : list) {
                    if (f.isFile()) {
                        files.add(f);
                    }
                }
            }
        }
    }

    private static void loadClusterStateFile(File file, String name) {
        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            DebugUtils.error("DevOps Panel: Failed to read configuration state file for " + name, e);
            return;
        }
        loadClusterStateProperties(props, name);
        DebugUtils.log("DevOps Panel: Configuration state restored for cluster '" + name + "' from " + file.getPath());
    }

    private static void loadClusterStateProperties(Properties props, String name) {
        Cluster cluster = ClusterRegistry.getInstance().getCluster(name);
        if (cluster == null) {
            cluster = ClusterRegistry.getInstance().createCluster(name);
        }

        String prefix = "cluster." + name + ".";

        boolean requireToken = Boolean.parseBoolean(props.getProperty(prefix + "requireToken", "true"));
        int timeoutMs = Integer.parseInt(props.getProperty(prefix + "timeoutMs", "5000"));
        String allowedIps = props.getProperty(prefix + "allowedIps", "");
        int rateLimitRequests = Integer.parseInt(props.getProperty(prefix + "rateLimitRequests", "100"));
        int rateLimitDurationSeconds = Integer.parseInt(props.getProperty(prefix + "rateLimitDurationSeconds", "60"));

        cluster.setRequireToken(requireToken);
        cluster.setAllowedIps(allowedIps);
        cluster.setTimeoutMs(timeoutMs);
        cluster.setRateLimit(rateLimitRequests, rateLimitDurationSeconds);

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
    }
}
