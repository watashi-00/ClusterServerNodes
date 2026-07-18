package hexacloud.core.event;

import lombok.Value;
import lombok.experimental.Accessors;

@Value
@Accessors(fluent = true)
public class TuiEvent {
    String type; String detail; long timestamp;
}
