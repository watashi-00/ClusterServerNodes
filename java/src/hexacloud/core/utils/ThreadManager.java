package hexacloud.core.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public class ThreadManager {

    public static ExecutorService newVirtualThreadPool() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }

    public static Thread startVirtual(Runnable task) {
        return Thread.startVirtualThread(task);
    }

    public static Thread startVirtual(String name, Runnable task) {
        return Thread.ofVirtual()
                .name(name)
                .start(task);
    }

    public static ThreadFactory virtualThreadFactory(String namePrefix) {
        return Thread.ofVirtual()
                .name(namePrefix, 0)
                .factory();
    }

    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, String namePrefix) {
        return Executors.newScheduledThreadPool(corePoolSize, virtualThreadFactory(namePrefix));
    }

    public static void spinWait() {
        Thread.onSpinWait();
    }
}
