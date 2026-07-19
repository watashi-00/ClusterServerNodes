package hexacloud.core.cluster;

import java.util.LinkedHashMap;
import java.util.Map;

import hexacloud.core.model.TelemetryParameter;
import hexacloud.core.utils.common.DebugUtils;
import hexacloud.core.utils.common.StrUtils;

/**
 * Data carrier class that handles parsing raw telemetry input parameters.
 */
public class TelemetryRequest {
    private String host;
    private int port;
    private Double cpu;
    private Double ram;
    private String lang;
    private Integer latency;
    private String statusStr;
    private String eventName;
    private String eventProtocol;
    private String eventFormat;
    private final Map<String, String> eventAttributes = new LinkedHashMap<>();

    private TelemetryRequest() {}

    /**
     * Parses raw telemetry argument string into a structured request object.
     */
    public static TelemetryRequest parse(String clusterName, String args) {
        TelemetryRequest req = new TelemetryRequest();
        if (args == null || args.trim().isEmpty()) {
            return req;
        }

        if (args.contains("&") || args.contains("host=")) {
            String[] params = args.split("&");
            for (String param : params) {
                if (!param.contains("=")) continue;
                String[] kv = param.split("=", 2);
                String key = kv[0].toLowerCase().trim();
                String val = kv[1].replace("+", " ").replace("%20", " ").trim();
                req.parseParameter(clusterName, key, val);
            }
        } else {
            String decodedArgs = args.replace("+", " ").replace("%20", " ");
            String[] parts = decodedArgs.trim().split("\\s+");
            if (parts.length >= 2) {
                req.host = parts[0];
                try {
                    req.port = Integer.parseInt(parts[1]);
                } catch (NumberFormatException e) {
                    DebugUtils.error(clusterName, null, "Failed to parse port parameter: " + parts[1], e);
                }

                for (int i = 2; i < parts.length; i++) {
                    String kv = parts[i];
                    if (!kv.contains("=")) continue;
                    String[] kvParts = kv.split("=", 2);
                    String key = kvParts[0].toLowerCase().trim();
                    String val = kvParts[1].trim();
                    req.parseParameter(clusterName, key, val);
                }
            }
        }
        return req;
    }

    private void parseParameter(String clusterName, String key, String val) {
        collectEventAttribute(key, val);
        TelemetryParameter telemetryParam = TelemetryParameter.fromKey(key);
        if (telemetryParam != null) {
            try {
                switch (telemetryParam) {
                    case HOST:
                        this.host = val;
                        break;
                    case PORT:
                        this.port = Integer.parseInt(val);
                        break;
                    case CPU:
                        this.cpu = Double.parseDouble(val);
                        break;
                    case RAM:
                        this.ram = Double.parseDouble(val);
                        break;
                    case LANGUAGE:
                    case LANG:
                        this.lang = val;
                        break;
                    case LATENCY:
                        this.latency = Integer.parseInt(val);
                        break;
                    case STATUS:
                        this.statusStr = val;
                        break;
                    case EVENT:
                        this.eventName = val;
                        break;
                    case PROTOCOL:
                        this.eventProtocol = val;
                        break;
                    case FORMAT:
                        this.eventFormat = val;
                        break;
                    case TOKEN:
                        break;
                }
            } catch (Exception e) {
                DebugUtils.error(clusterName, null, "Failed to parse telemetry parameter: key=" + key + ", value=" + val, e);
            }
        }
    }

    private void collectEventAttribute(String key, String value) {
        if (StrUtils.isBlank(key)) {
            return;
        }
        TelemetryParameter param = TelemetryParameter.fromKey(key);
        if (param == TelemetryParameter.HOST
            || param == TelemetryParameter.PORT
            || param == TelemetryParameter.EVENT
            || param == TelemetryParameter.PROTOCOL
            || param == TelemetryParameter.FORMAT
            || param == TelemetryParameter.TOKEN) {
            return;
        }
        this.eventAttributes.put(key, value);
    }

    public String getHost() {
        return host;
    }

    public int getPort() {
        return port;
    }

    public Double getCpu() {
        return cpu;
    }

    public Double getRam() {
        return ram;
    }

    public String getLang() {
        return lang;
    }

    public Integer getLatency() {
        return latency;
    }

    public String getStatusStr() {
        return statusStr;
    }

    public String getEventName() {
        return eventName;
    }

    public String getEventProtocol() {
        return eventProtocol;
    }

    public String getEventFormat() {
        return eventFormat;
    }

    public Map<String, String> getEventAttributes() {
        return eventAttributes;
    }
}
