package hexacloud.core.cluster.event;

import hexacloud.core.model.ServerNode;

public record NodeRegistered(ServerNode node) implements ClusterEvent {}
