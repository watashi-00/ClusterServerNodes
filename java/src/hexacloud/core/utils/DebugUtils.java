package hexacloud.core.utils;

import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Structured logging utility. Supports filtering by cluster and service host.
 */
public class DebugUtils {

    private static boolean debugEnabled = false;
    private static boolean tuiModeActive = false;
    private static final Queue<LogEntry> recentLogs = new ConcurrentLinkedQueue<>();

    public static class LogEntry {
        private final long timestamp;
        private final String level;
        private final String clusterName;
        private final String serviceHost;
        private final String message;

        public LogEntry(String level, String clusterName, String serviceHost, String message) {
            this.timestamp = System.currentTimeMillis();
            this.level = level;
            this.clusterName = clusterName != null ? clusterName : "";
            this.serviceHost = serviceHost != null ? serviceHost : "";
            this.message = message;
        }

        public long getTimestamp() { return timestamp; }
        public String getLevel() { return level; }
        public String getClusterName() { return clusterName; }
        public String getServiceHost() { return serviceHost; }
        public String getMessage() { return message; }

        @Override
        public String toString() {
            return "[" + level + "] " + message;
        }
    }

    private static final java.io.PrintStream originalOut = System.out;
    private static final java.io.PrintStream originalErr = System.err;

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void setTuiModeActive(boolean active) {
        tuiModeActive = active;
        if (active) {
            System.setOut(new java.io.PrintStream(new RedirectorOutputStream(false), true, java.nio.charset.StandardCharsets.UTF_8));
            System.setErr(new java.io.PrintStream(new RedirectorOutputStream(true), true, java.nio.charset.StandardCharsets.UTF_8));
        } else {
            System.setOut(originalOut);
            System.setErr(originalErr);
        }
    }

    private static class RedirectorOutputStream extends java.io.OutputStream {
        private final boolean isError;
        private final java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();

        public RedirectorOutputStream(boolean isError) {
            this.isError = isError;
        }

        @Override
        public void write(int b) {
            if (b == '\n') {
                flushBuffer();
            } else if (b != '\r') {
                buffer.write(b);
            }
        }

        @Override
        public void write(byte[] b, int off, int len) {
            for (int i = 0; i < len; i++) {
                write(b[off + i]);
            }
        }

        private void flushBuffer() {
            byte[] bytes = buffer.toByteArray();
            buffer.reset();
            if (bytes.length > 0) {
                String line = new String(bytes, java.nio.charset.StandardCharsets.UTF_8).trim();
                if (!line.isEmpty()) {
                    if (isError) {
                        captureLog("ERROR", null, null, line);
                    } else {
                        captureLog("INFO", null, null, line);
                    }
                }
            }
        }
    }

    public static List<LogEntry> getAllLogs() {
        return new ArrayList<>(recentLogs);
    }

    public static List<LogEntry> getDashboardLogs() {
        List<LogEntry> result = new ArrayList<>();
        for (LogEntry entry : recentLogs) {
            if (entry.getLevel().equals("INFO") || entry.getLevel().equals("ERROR")) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<LogEntry> getClusterLogs(String clusterName) {
        List<LogEntry> result = new ArrayList<>();
        if (clusterName == null || clusterName.isEmpty()) return result;
        for (LogEntry entry : recentLogs) {
            if (clusterName.equalsIgnoreCase(entry.getClusterName())) {
                result.add(entry);
            }
        }
        return result;
    }

    public static List<LogEntry> getServiceLogs(String clusterName, String serviceHost) {
        List<LogEntry> result = new ArrayList<>();
        if (clusterName == null || clusterName.isEmpty() || serviceHost == null || serviceHost.isEmpty()) return result;
        String cleanHost = serviceHost.replaceAll("^(http|https)://", "");
        for (LogEntry entry : recentLogs) {
            if (clusterName.equalsIgnoreCase(entry.getClusterName())) {
                String entryHost = entry.getServiceHost() != null ? entry.getServiceHost().replaceAll("^(http|https)://", "") : "";
                if (cleanHost.equalsIgnoreCase(entryHost)) {
                    result.add(entry);
                }
            }
        }
        return result;
    }

    private static void captureLog(String level, String clusterName, String serviceHost, String message) {
        LogEntry entry = new LogEntry(level, clusterName, serviceHost, message);
        recentLogs.offer(entry);
        while (recentLogs.size() > 1000) {
            recentLogs.poll();
        }
        if (!tuiModeActive) {
            String formatted = entry.toString();
            if (level.equals("ERROR")) {
                System.err.println(formatted);
            } else {
                System.out.println(formatted);
            }
        }
    }

    public static void log(String message) {
        if (debugEnabled) {
            captureLog("DEBUG", null, null, message);
        }
    }

    public static void info(String message) {
        captureLog("INFO", null, null, message);
    }

    public static void info(String clusterName, String serviceHost, String message) {
        captureLog("INFO", clusterName, serviceHost, message);
    }

    public static void error(String message) {
        captureLog("ERROR", null, null, message);
    }

    public static void error(String clusterName, String serviceHost, String message) {
        captureLog("ERROR", clusterName, serviceHost, message);
    }

    public static void error(String message, Throwable t) {
        error(null, null, message, t);
    }

    public static void error(String clusterName, String serviceHost, String message, Throwable t) {
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
            captureLog("ERROR", clusterName, serviceHost, message + details);
        } else {
            captureLog("ERROR", clusterName, serviceHost, message);
        }
    }
}
