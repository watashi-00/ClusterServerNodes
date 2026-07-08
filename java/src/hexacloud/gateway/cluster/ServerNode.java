package hexacloud.gateway.cluster;

public record ServerNode(String host, int port, boolean isOnline, boolean isExternal) {
    @Override
    public String toString() {
        return "ServerNode{" +
                "host='" + host + '\'' +
                ", port=" + port +
                ", isOnline=" + isOnline +
                ", isExternal=" + isExternal +
                '}';
    }

}
