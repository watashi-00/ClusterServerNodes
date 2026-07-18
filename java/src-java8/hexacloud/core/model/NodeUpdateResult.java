package hexacloud.core.model;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class NodeUpdateResult {
    String host; String protocol; boolean statusChanged; boolean telemetryUpdated;
}
