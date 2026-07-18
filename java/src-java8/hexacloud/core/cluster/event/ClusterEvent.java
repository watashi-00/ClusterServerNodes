package hexacloud.core.cluster.event;

import java.util.Map;

import hexacloud.core.event.Event;
import hexacloud.core.model.ServerNode;
import hexacloud.core.model.NodeStatus;
import lombok.Value;
import lombok.experimental.Accessors;

public interface ClusterEvent extends Event{
    
    @Value
    @Accessors(fluent = true)
    public class ClusterRegistered implements ClusterEvent {
        String clusterName;
    }

    @Value
    @Accessors(fluent = true)
    public class NodeRegistered implements ClusterEvent {
        ServerNode node;
    }
    
    @Value
    @Accessors(fluent = true)
    public class NodeDeregistered implements ClusterEvent {
        String host;
    }

    @Value
    @Accessors(fluent = true)
    public class NodeStatusChanged implements ClusterEvent {
        String host; NodeStatus status;
    }

    @Value
    @Accessors(fluent = true)
    public class NodeTelemetryUpdated implements ClusterEvent {
        String host;
    }

    @Value
    @Accessors(fluent = true)
    public class NodeEventSubmitted implements ClusterEvent {
        String host; int port; String protocol; 
        String format; String event;
        Map<String, String> attributes;
    }

}
