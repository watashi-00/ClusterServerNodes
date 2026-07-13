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
import hexacloud.core.utils.DebugUtils;

public class ClusterStatePersistence {

    private static final String STATE_FILE_PATH = resolveStateFilePath();
    private static boolean stateLoaded = false;

    private static String resolveStateFilePath() {
        if (new File("java/resources").isDirectory()) {
            return "java/resources/hexacloud-state.properties";
        } else if (new File("resources").isDirectory()) {
            return "resources/hexacloud-state.properties";
        }
        return "hexacloud-state.properties";
    }

    private ClusterStatePersistence() {}

    /**
     * Check if the gateway state has been successfully loaded from the persistence file.
     */
    public static boolean isStateLoaded() {
        return stateLoaded;
    }

    /**
     * Save the current active clusters, nodes, and configurations to the properties file.
     */
    public static synchronized void saveState() {
        Properties props = new Properties();
        List<String> clusterNames = new ArrayList<>();
        
        for (Cluster cluster : ClusterRegistry.getInstance().getClusters()) {
            String name = cluster.getClusterName();
            clusterNames.add(name);
            
            String prefix = "cluster." + name + ".";
            props.setProperty(prefix + "requireToken", String.valueOf(cluster.isRequireToken()));
            props.setProperty(prefix + "timeoutMs", String.valueOf(cluster.getTimeoutMs()));
            props.setProperty(prefix + "allowedIps", cluster.getAllowedIps());
            props.setProperty(prefix + "rateLimitRequests", String.valueOf(cluster.getRateLimitRequests()));
            props.setProperty(prefix + "rateLimitDurationSeconds", String.valueOf(cluster.getRateLimitDurationSeconds()));
            
            List<String> nodeKeys = new ArrayList<>();
            for (ServerNode node : cluster.getCluster()) {
                String nodeKey = node.getFullHost();
                nodeKeys.add(nodeKey);
                
                String nodePrefix = "node." + name + "." + nodeKey + ".";
                props.setProperty(nodePrefix + "host", node.host());
                props.setProperty(nodePrefix + "port", String.valueOf(node.port()));
                props.setProperty(nodePrefix + "isExternal", String.valueOf(node.isExternal()));
                props.setProperty(nodePrefix + "pingEnabled", String.valueOf(node.pingEnabled()));
                props.setProperty(nodePrefix + "pingPath", node.pingPath());
                if (node.pingHeaderName() != null) {
                    props.setProperty(nodePrefix + "pingHeaderName", node.pingHeaderName());
                }
                if (node.pingHeaderValue() != null) {
                    props.setProperty(nodePrefix + "pingHeaderValue", node.pingHeaderValue());
                }
            }
            props.setProperty(prefix + "nodes", String.join(",", nodeKeys));
        }
        props.setProperty("clusters.list", String.join(",", clusterNames));

        try (FileOutputStream out = new FileOutputStream(STATE_FILE_PATH)) {
            props.store(out, "Persisted GateBridge Cluster Gateway State");
            DebugUtils.log("DevOps Panel: Saved active configurations state to " + STATE_FILE_PATH);
        } catch (IOException e) {
            DebugUtils.error("DevOps Panel: Failed to save state file", e);
        }
    }

    /**
     * Load clusters, registered nodes, and configurations from the properties file.
     */
    public static synchronized void loadState() {
        File file = new File(STATE_FILE_PATH);
        if (!file.exists()) {
            DebugUtils.log("DevOps Panel: No configuration file found at " + STATE_FILE_PATH + ". Using bootstrapper defaults.");
            stateLoaded = false;
            return;
        }

        Properties props = new Properties();
        try (FileInputStream in = new FileInputStream(file)) {
            props.load(in);
        } catch (IOException e) {
            DebugUtils.error("DevOps Panel: Failed to read configuration state file", e);
            stateLoaded = false;
            return;
        }

        String clustersList = props.getProperty("clusters.list");
        if (clustersList == null || clustersList.trim().isEmpty()) {
            stateLoaded = false;
            return;
        }

        // Clear default code-constructed registry
        ClusterRegistry.getInstance().clear();

        for (String name : clustersList.split(",")) {
            name = name.trim();
            if (name.isEmpty()) continue;

            Cluster cluster = ClusterRegistry.getInstance().createCluster(name);
            String prefix = "cluster." + name + ".";
            
            int timeoutMs = Integer.parseInt(props.getProperty(prefix + "timeoutMs", "5000"));
            String allowedIps = props.getProperty(prefix + "allowedIps", "");
            int rateLimitRequests = Integer.parseInt(props.getProperty(prefix + "rateLimitRequests", "100"));
            int rateLimitDurationSeconds = Integer.parseInt(props.getProperty(prefix + "rateLimitDurationSeconds", "60"));
            
            cluster.setAllowedIps(allowedIps);
            cluster.setTimeoutMs(timeoutMs);
            cluster.setRateLimit(rateLimitRequests, rateLimitDurationSeconds);

            String nodesStr = props.getProperty(prefix + "nodes");
            if (nodesStr != null && !nodesStr.trim().isEmpty()) {
                for (String nodeKey : nodesStr.split(",")) {
                    nodeKey = nodeKey.trim();
                    if (nodeKey.isEmpty()) continue;

                    String nodePrefix = "node." + name + "." + nodeKey + ".";
                    String host = props.getProperty(nodePrefix + "host");
                    int port = Integer.parseInt(props.getProperty(nodePrefix + "port"));
                    boolean isExternal = Boolean.parseBoolean(props.getProperty(nodePrefix + "isExternal", "false"));
                    boolean pingEnabled = Boolean.parseBoolean(props.getProperty(nodePrefix + "pingEnabled", "true"));
                    String pingPath = props.getProperty(nodePrefix + "pingPath", "/");
                    String pingHeaderName = props.getProperty(nodePrefix + "pingHeaderName", null);
                    String pingHeaderValue = props.getProperty(nodePrefix + "pingHeaderValue", null);

                    ServerNode node = new ServerNode(
                        host, port, NodeStatus.OFFLINE, isExternal,
                        pingEnabled, pingPath, pingHeaderName, pingHeaderValue
                    );
                    cluster.registerServer(node);
                }
            }
        }
        DebugUtils.log("DevOps Panel: Configuration state successfully restored from " + STATE_FILE_PATH);
        stateLoaded = true;
    }
}
