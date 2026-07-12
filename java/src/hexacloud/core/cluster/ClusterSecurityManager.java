package hexacloud.core.cluster;

import hexacloud.core.config.EnvLoader;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.RateLimiter;

public class ClusterSecurityManager {

    private final String clusterName;
    private final String secret;
    private final boolean requireToken;
    private final int timeoutMs;
    private final String allowedIps;
    private final RateLimiter rateLimiter;

    public ClusterSecurityManager(String clusterName) {
        this.clusterName = clusterName;
        this.secret = EnvLoader.get(clusterName, "secret", null);
        this.requireToken = EnvLoader.getBoolean(clusterName, "requireToken", true);
        this.timeoutMs = EnvLoader.getInt(clusterName, "timeoutMs", 5000);
        this.allowedIps = EnvLoader.get(clusterName, "allowedIps", "");
        
        int rateLimitRequests = EnvLoader.getInt(clusterName, "rateLimitRequests", 100);
        int rateLimitDurationSeconds = EnvLoader.getInt(clusterName, "rateLimitDurationSeconds", 60);
        this.rateLimiter = new RateLimiter(rateLimitRequests, rateLimitDurationSeconds);

        DebugUtils.info("ClusterSecurityManager initialized for '" + clusterName + "' -> requireToken: " + requireToken + ", timeoutMs: " + timeoutMs + ", allowedIps: [" + allowedIps + "], rateLimit: " + rateLimitRequests + "/" + rateLimitDurationSeconds + "s");
    }

    public boolean authenticate(String token) {
        if(!requireToken) {
            return true;
        }
        if(secret == null || secret.isEmpty()) {
            DebugUtils.error("Cluster '" + clusterName + "' access barred: Token is required but no secret key is configured.");
            return false;
        }
        boolean authorized = secret.equals(token);
        if(!authorized) {
            DebugUtils.error("Cluster '" + clusterName + "' access barred: Invalid API token provided.");
        }
        return authorized;
    }

    public boolean isIpAllowed(String ipAddress) {
        if(allowedIps == null || allowedIps.trim().isEmpty()) {
            return true;
        }
        String[] ips = allowedIps.split(",");
        for(String ip : ips) {
            if(ip.trim().equals(ipAddress)) {
                return true;
            }
        }
        DebugUtils.error("Cluster '" + clusterName + "' access barred: IP '" + ipAddress + "' is not allowed.");
        return false;
    }

    public boolean checkRateLimit(String clientId) {
        boolean allowed = rateLimiter.allowRequest(clientId);
        if(!allowed) {
            DebugUtils.error("Cluster '" + clusterName + "' access barred: Too many requests from '" + clientId + "'.");
        }
        return allowed;
    }

    public boolean isRequireToken() {
        return requireToken;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public String getSecret() {
        return secret;
    }

    public String getAllowedIps() {
        return allowedIps;
    }
}
