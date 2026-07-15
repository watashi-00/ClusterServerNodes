package hexacloud.core.cluster.event;

import hexacloud.core.event.Event;
import hexacloud.core.model.ServerNode;
import hexacloud.core.model.NodeStatus;

/**
 * Marker interface for all Cluster Domain Events.
 * Groups all concrete event payloads as nested records to simplify package structure.
 */
public interface ClusterEvent extends Event {

    record ClusterRegistered(String clusterName) implements ClusterEvent {}

    record NodeRegistered(ServerNode node) implements ClusterEvent {}

    record NodeDeregistered(String host) implements ClusterEvent {}

    record NodeStatusChanged(String host, NodeStatus status) implements ClusterEvent {}

    record NodeTelemetryUpdated(String host) implements ClusterEvent {}
}
