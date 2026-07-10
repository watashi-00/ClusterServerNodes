package hexacloud.messages.cluster;

import java.util.ArrayList;
import java.util.List;

public class ClusterEventManager {
    private final List<ClusterListener> listeners = new ArrayList<>();

    public void sub(ClusterListener listener) {
        listeners.add(listener);
    }    

    public void dispatch(ClusterEvent event) {
        for (var listener : listeners) {
            listener.onClusterEvent(event);
        }
    }
}
