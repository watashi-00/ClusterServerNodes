package hexacloud.core.event;

import hexacloud.core.utils.common.StrUtils;

/**
 * Supported formats for cluster telemetry node events.
 */
public enum EventFormat {
    TEXT("text"),
    JSON("json"),
    XML("xml"),
    BINARY("binary"),
    UNKNOWN("unknown");

    private final String value;

    EventFormat(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    @Override
    public String toString() {
        return value;
    }

    public static EventFormat fromString(String requestedFormat) {
        if (StrUtils.isBlank(requestedFormat)) {
            return TEXT;
        }
        String normalized = requestedFormat.trim().toLowerCase();
        for (EventFormat format : values()) {
            if (format.value.equals(normalized)) {
                return format;
            }
        }
        return UNKNOWN;
    }
}
