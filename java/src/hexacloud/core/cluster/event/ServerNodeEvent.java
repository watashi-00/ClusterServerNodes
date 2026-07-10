package hexacloud.core.cluster.event;

public record ServerNodeEvent(String host, boolean status) implements ClusterEvent {}
