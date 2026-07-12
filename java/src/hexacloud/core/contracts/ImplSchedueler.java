package hexacloud.core.contracts;

public interface ImplSchedueler {
    void startPingScheduler();
    void startPingScheduler(int intervalInSeconds);
    void setPingInterval(int intervalInSeconds);
    
    void stopPingScheduler();
}
