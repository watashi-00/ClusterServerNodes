package hexacloud.core.cluster.event;

public record NodeStatusChanged(String host, boolean status) implements ClusterEvent {}
