package hexacloud.core.contracts;

import java.util.List;

import hexacloud.core.model.ServerNode;

public interface ImplSchedueler {
    void startPingScheduler();
    void startPingScheduler(int intervalInSeconds);
    void startPingScheduler(List<ServerNode> cluster);
    void startPingScheduler(int intervalInSeconds, List<ServerNode> cluster);

    void setPingInterval(int intervalInSeconds);
    
    void stopPingScheduler();
}
