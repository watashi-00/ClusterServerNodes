package hexacloud.core.config;

import java.net.http.HttpClient;
import java.time.Duration;

public class ClusterConfig {

    // Cluster Defaults
    public static final int MAX_CLUSTER_SIZE = 5;
    public static final String DEFAULT_CLUSTER_URI = "http://localhost";
    public static final String DEFAULT_CLUSTER_NAME= "DefaultCluster";

    // Server Defaults
    public static final int MAX_WORKERS = 20;
    public static final int DEFAULT_SERVER_PORT = 8080;

    // Scheduler & Network Defaults
    public static final int DEFAULT_PING_INTERVAL_SECONDS = 5;
    public static final int SCHEDULER_THREAD_POOL_SIZE = 1;
    public static final long AWAIT_TERMINATION_TIMEOUT_MS = 800;

    // HTTP Defaults
    public static final Duration HTTP_CONNECT_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration HTTP_REQUEST_TIMEOUT = Duration.ofSeconds(2);
    public static final HttpClient.Version HTTP_VERSION = HttpClient.Version.HTTP_1_1;

    private ClusterConfig() {}
}
