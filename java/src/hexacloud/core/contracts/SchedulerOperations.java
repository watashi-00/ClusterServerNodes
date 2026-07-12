package hexacloud.core.contracts;

public interface SchedulerOperations {
    SchedulerOperations startPingScheduler();
    SchedulerOperations startPingScheduler(int intervalInSeconds);

    SchedulerOperations setPingInterval(int intervalInSeconds);
    
    SchedulerOperations stopPingScheduler();
}
