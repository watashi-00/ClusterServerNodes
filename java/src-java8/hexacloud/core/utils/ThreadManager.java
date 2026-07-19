package hexacloud.core.utils;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadFactory;

public class ThreadManager {

    public static ExecutorService newVirtualThreadPool() {
        return Executors.newCachedThreadPool();
    }

    public static Thread startVirtual(Runnable task) {
        Thread t = new Thread(task);
        t.setDaemon(true);
        t.start();
        return t;
    }

    public static Thread startVirtual(String name, Runnable task) {
        Thread t = new Thread(task);
        t.setName(name);
        t.setDaemon(true);
        t.start();
        return t;
    }

    public static ThreadFactory virtualThreadFactory(String namePrefix) {
        return new ThreadFactory() {
            private int counter = 0;

            @Override
            public synchronized Thread newThread(Runnable r) {
                Thread t = new Thread(r);
                t.setName(namePrefix + "-" + counter++);
                t.setDaemon(true);
                return t;
            }
        };
    }

    public static ScheduledExecutorService newScheduledThreadPool(int corePoolSize, String namePrefix) {
        return Executors.newScheduledThreadPool(corePoolSize, virtualThreadFactory(namePrefix));
    }

    public static void spinWait() {
        try {
            Thread.sleep(1);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
