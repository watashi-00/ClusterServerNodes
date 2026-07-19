package hexacloud.core.event;

import java.util.Objects;

public final class TuiEvent {
    private final String type;
    private final String detail;
    private final long timestamp;

    public TuiEvent(String type, String detail, long timestamp) {
        this.type = type;
        this.detail = detail;
        this.timestamp = timestamp;
    }

    public String type() {
        return type;
    }

    public String detail() {
        return detail;
    }

    public long timestamp() {
        return timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        TuiEvent tuiEvent = (TuiEvent) o;
        return timestamp == tuiEvent.timestamp &&
                Objects.equals(type, tuiEvent.type) &&
                Objects.equals(detail, tuiEvent.detail);
    }

    @Override
    public int hashCode() {
        return Objects.hash(type, detail, timestamp);
    }

    @Override
    public String toString() {
        return "TuiEvent[type=" + type + ", detail=" + detail + ", timestamp=" + timestamp + "]";
    }
}
