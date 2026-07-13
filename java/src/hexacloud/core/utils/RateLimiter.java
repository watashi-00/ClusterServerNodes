package hexacloud.core.utils;

import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

public class RateLimiter {

    private int maxRequests;
    private long windowSizeMs;
    private final ConcurrentHashMap<String, Queue<Long>> clientRequestWindows = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, int durationSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = durationSeconds * 1000L;
    }

    public synchronized void updateLimits(int maxRequests, int durationSeconds) {
        this.maxRequests = maxRequests;
        this.windowSizeMs = durationSeconds * 1000L;
    }

    public boolean allowRequest(String clientId) {
        if(maxRequests <= 0 || windowSizeMs <= 0) {
            return true;
        }
        
        long now = System.currentTimeMillis();
        Queue<Long> requestTimestamps = clientRequestWindows.computeIfAbsent(clientId, k -> new ConcurrentLinkedQueue<>());

        synchronized(requestTimestamps) {
            while(!requestTimestamps.isEmpty() && (now - requestTimestamps.peek() > windowSizeMs)) {
                requestTimestamps.poll();
            }

            if(requestTimestamps.size() < maxRequests) {
                requestTimestamps.offer(now);
                return true;
            }
            return false;
        }
    }
}
