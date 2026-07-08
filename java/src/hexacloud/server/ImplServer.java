package hexacloud.server;

public interface ImplServer {
    void start(int port,  boolean isExternal);
    void start(int port, String host,  boolean isExternal);
    void stop(int port);
    void stop();
    void stopAll();
    void startAll();
    void setInterval(int interval);
}
