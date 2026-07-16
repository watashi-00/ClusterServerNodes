package hexacloud.core.event;

import hexacloud.core.event.Event;
import lombok.Value;

public interface ClusterEvent extends Event{
    
    @Value
    class ClusterRegistered implements ClusterEvent {
        String clusterName;
    }

    @Value
    class NodeRegistered implements ClusterEvent {
        ServerNode node;
    }

    @Value
    class NodeDeregistered implements ClusterEvent {
        String host;
    }

    @Value
    class NodeStatusChanged implements ClusterEvent {
        String host; NodeStatus status;
    }

    @Value
    class NodeTelemetryUpdated implements ClusterEvent {
        String host;
    }

    @Value
    class NodeEventSubmitted implements ClusterEvent {
        String host; int port; String protocol; 
        String format; String event;
        Map<String, String> attributes;
    }

}
