package hexacloud.core.cluster.event;

import hexacloud.core.model.NodeStatus;

public record NodeStatusChanged(String host, NodeStatus status) implements ClusterEvent {}
