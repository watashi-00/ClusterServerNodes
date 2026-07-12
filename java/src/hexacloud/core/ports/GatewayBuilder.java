package hexacloud.core.ports;

import hexacloud.infra.gateway.GatewayFactory;

public class GatewayBuilder {
    private final String clusterName;
    private int port = 3000;
    private int pingInterval = 5;
    private boolean telnetEnabled = false;
    private boolean httpEnabled = false;
    private boolean wsEnabled = false;

    public GatewayBuilder(String clusterName) {
        this.clusterName = clusterName;
    }

    public GatewayBuilder port(int port) {
        this.port = port;
        return this;
    }

    public GatewayBuilder pingInterval(int pingInterval) {
        this.pingInterval = pingInterval;
        return this;
    }

    public GatewayBuilder enableTelnet(boolean enabled) {
        this.telnetEnabled = enabled;
        return this;
    }

    public GatewayBuilder enableHttp(boolean enabled) {
        this.httpEnabled = enabled;
        return this;
    }

    public GatewayBuilder enableWs(boolean enabled) {
        this.wsEnabled = enabled;
        return this;
    }

    public GatewayPort build() {
        GatewayPort gateway = GatewayFactory.createGateway(clusterName, port);
        gateway.enableTelnet(telnetEnabled)
               .enableHttp(httpEnabled)
               .enableWs(wsEnabled);
        gateway.setPingInterval(pingInterval);
        return gateway;
    }
}
