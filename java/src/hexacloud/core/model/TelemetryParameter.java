package hexacloud.core.model;

/**
 * Standard telemetry request parameter names.
 */
public enum TelemetryParameter {
    CPU("cpu"),
    RAM("ram"),
    LANGUAGE("language"),
    LANG("lang"),
    LATENCY("latency"),
    STATUS("status"),
    EVENT("event"),
    PROTOCOL("protocol"),
    FORMAT("format"),
    HOST("host"),
    PORT("port"),
    TOKEN("token");

    private final String key;

    TelemetryParameter(String key) {
        this.key = key;
    }

    public String getKey() {
        return key;
    }

    public static TelemetryParameter fromKey(String key) {
        if (key == null) return null;
        String normalized = key.trim().toLowerCase();
        for (TelemetryParameter param : values()) {
            if (param.key.equals(normalized)) {
                return param;
            }
        }
        return null;
    }
}
