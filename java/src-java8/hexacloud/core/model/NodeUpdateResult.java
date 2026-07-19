package hexacloud.core.model;

import java.util.Objects;

public final class NodeUpdateResult {
    private final String host;
    private final String protocol;
    private final boolean statusChanged;
    private final boolean telemetryUpdated;

    public NodeUpdateResult(String host, String protocol, boolean statusChanged, boolean telemetryUpdated) {
        this.host = host;
        this.protocol = protocol;
        this.statusChanged = statusChanged;
        this.telemetryUpdated = telemetryUpdated;
    }

    public String host() {
        return host;
    }

    public String protocol() {
        return protocol;
    }

    public boolean statusChanged() {
        return statusChanged;
    }

    public boolean telemetryUpdated() {
        return telemetryUpdated;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeUpdateResult that = (NodeUpdateResult) o;
        return statusChanged == that.statusChanged &&
                telemetryUpdated == that.telemetryUpdated &&
                Objects.equals(host, that.host) &&
                Objects.equals(protocol, that.protocol);
    }

    @Override
    public int hashCode() {
        return Objects.hash(host, protocol, statusChanged, telemetryUpdated);
    }

    @Override
    public String toString() {
        return "NodeUpdateResult[host=" + host + ", protocol=" + protocol +
                ", statusChanged=" + statusChanged + ", telemetryUpdated=" + telemetryUpdated + "]";
    }
}
