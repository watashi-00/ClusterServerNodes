package hexacloud.core.model;

import lombok.Value;

@Value
public class PingResult {
    NodeStatus status; boolean hasTelemetry;
}
