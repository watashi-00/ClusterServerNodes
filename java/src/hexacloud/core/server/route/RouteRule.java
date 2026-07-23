package hexacloud.core.server.route;

public class RouteRule {
    private final String host;
    private final String pathPattern;
    private final String clusterName;

    public RouteRule(String host, String pathPattern, String clusterName) {
        this.host = host;
        this.pathPattern = pathPattern;
        this.clusterName = clusterName;
    }

    public String getHost() { return host; }
    public String getPathPattern() { return pathPattern; }
    public String getClusterName() { return clusterName; }

    public boolean matches(String requestHost, String requestPath) {
        String matchedHost = requestHost;
        if (matchedHost != null && matchedHost.contains(":")) {
            matchedHost = matchedHost.split(":")[0];
        }

        if (this.host != null && !this.host.equals("*")) {
            if (matchedHost == null || !matchedHost.equalsIgnoreCase(this.host)) {
                return false;
            }
        }

        if (requestPath == null) {
            return false;
        }

        if (this.pathPattern == null || this.pathPattern.equals("/**") || this.pathPattern.equals("/*")) {
            return true;
        }

        String pattern = this.pathPattern;
        if (pattern.endsWith("/**")) {
            String prefix = pattern.substring(0, pattern.length() - 3);
            return requestPath.equals(prefix) || requestPath.equals(prefix + "/") || requestPath.startsWith(prefix + "/");
        } else if (pattern.endsWith("/*")) {
            String prefix = pattern.substring(0, pattern.length() - 2);
            return requestPath.equals(prefix) || requestPath.equals(prefix + "/") || requestPath.startsWith(prefix + "/");
        }

        return requestPath.equals(pattern);
    }
}
