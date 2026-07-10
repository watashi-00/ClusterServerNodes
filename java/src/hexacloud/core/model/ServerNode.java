package hexacloud.core.model;

public record ServerNode(String host, int port, NodeStatus isOnline, boolean isExternal) {
    @Override
    public String toString() {
        return "ServerNode{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", isOnline=" + isOnline +
                ", isExternal=" + isExternal +
                '}';
    }

    public ServerNode withStatus(NodeStatus newStatus) {
        return new ServerNode(this.host, this.port, newStatus, this.isExternal);
    }

}
