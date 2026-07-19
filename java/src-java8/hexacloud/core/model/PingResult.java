package hexacloud.core.model;

import java.util.Objects;

public final class PingResult {
    private final NodeStatus status;
    private final boolean hasTelemetry;

    public PingResult(NodeStatus status, boolean hasTelemetry) {
        this.status = status;
        this.hasTelemetry = hasTelemetry;
    }

    public NodeStatus status() {
        return status;
    }

    public boolean hasTelemetry() {
        return hasTelemetry;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PingResult that = (PingResult) o;
        return hasTelemetry == that.hasTelemetry && status == that.status;
    }

    @Override
    public int hashCode() {
        return Objects.hash(status, hasTelemetry);
    }

    @Override
    public String toString() {
        return "PingResult[status=" + status + ", hasTelemetry=" + hasTelemetry + "]";
    }
}
