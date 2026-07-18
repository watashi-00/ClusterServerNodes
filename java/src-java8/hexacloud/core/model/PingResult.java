package hexacloud.core.model;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class PingResult {
    NodeStatus status; boolean hasTelemetry;
}
