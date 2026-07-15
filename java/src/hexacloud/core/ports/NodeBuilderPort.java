package hexacloud.core.ports;

public interface NodeBuilderPort {
    
    /**
     * Set whether the scheduler should ping this node.
     */
    NodeBuilderPort pingEnabled(boolean enabled);
    
    /**
     * Set the path used by the ping scheduler (e.g. "/healthz").
     */
    NodeBuilderPort pingPath(String path);
    
    /**
     * Set a custom header name and value to send during ping health-checks.
     */
    NodeBuilderPort pingHeader(String name, String value);
    
    /**
     * Set whether this is an external service node.
     */
    NodeBuilderPort external(boolean external);
    
    /**
     * Register the node in the cluster and return the parent GatewayBuilderPort for fluent chaining.
     */
    GatewayBuilderPort register();
}
