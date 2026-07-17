package hexacloud.core.event;

import lombok.Value;

@Value
public class TuiState {
    String type; String detail; long timestamp;
}
