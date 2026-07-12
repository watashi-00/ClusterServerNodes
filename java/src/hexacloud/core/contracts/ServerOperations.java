package hexacloud.core.contracts;

public interface ServerOperations {
    ServerOperations listen(int port);
    ServerOperations listen();
}
