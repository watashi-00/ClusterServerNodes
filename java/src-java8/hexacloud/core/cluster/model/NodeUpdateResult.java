package hexacloud.core.model;

import lombok.Value;

@Value
public class NodeUpdateResult {
    String host; String protocol; boolean statusChanged; boolean telemetryUpdated;
}
