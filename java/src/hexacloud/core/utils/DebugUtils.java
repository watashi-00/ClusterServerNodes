package hexacloud.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class DebugUtils {

    private static boolean debugEnabled = false;
    private static boolean tuiModeActive = false;
    private static final Queue<String> recentLogs = new ConcurrentLinkedQueue<>();

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void setTuiModeActive(boolean active) {
        tuiModeActive = active;
    }

    public static List<String> getRecentLogs() {
        return new ArrayList<>(recentLogs);
    }

    private static void captureLog(String prefix, String message) {
        String formatted = "[" + prefix + "] " + message;
        recentLogs.offer(formatted);
        while (recentLogs.size() > 10) {
            recentLogs.poll();
        }
        if (!tuiModeActive) {
            if (prefix.equals("ERROR")) {
                System.err.println(formatted);
            } else {
                System.out.println(formatted);
            }
        }
    }

    public static void log(String message) {
        if (debugEnabled) {
            captureLog("DEBUG", message);
        }
    }

    public static void info(String message) {
        captureLog("INFO", message);
    }

    public static void error(String message) {
        captureLog("ERROR", message);
    }

    public static void error(String message, Throwable t) {
        if (t != null) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            String details;
            if (cause instanceof java.net.ConnectException) {
                details = " -> Connection refused";
            } else if (cause instanceof java.net.http.HttpConnectTimeoutException || (cause instanceof java.io.IOException && cause.getMessage() != null && cause.getMessage().contains("timed out"))) {
                details = " -> Connection timeout";
            } else {
                details = " -> " + cause.toString();
            }
            captureLog("ERROR", message + details);
        } else {
            captureLog("ERROR", message);
        }
    }
}
