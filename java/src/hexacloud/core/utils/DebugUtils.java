package hexacloud.core.utils;

public class DebugUtils {

    private static boolean debugEnabled = false;

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void log(String message) {
        if(debugEnabled) {
            System.out.println("[DEBUG] " + message);
        }
    }

    public static void info(String message) {
        System.out.println("[INFO] " + message);
    }

    public static void error(String message) {
        System.err.println("[ERROR] " + message);
    }

    public static void error(String message, Throwable t) {
        if(t != null) {
            Throwable cause = t.getCause() != null ? t.getCause() : t;
            if(cause instanceof java.net.ConnectException) {
                System.err.println("[ERROR] " + message + " -> Connection refused");
            } else if(cause instanceof java.net.http.HttpConnectTimeoutException || cause instanceof java.io.IOException && cause.getMessage().contains("timed out")) {
                System.err.println("[ERROR] " + message + " -> Connection timeout");
            } else {
                System.err.println("[ERROR] " + message + " -> " + cause.toString());
                t.printStackTrace(System.err);
            }
        } else {
            System.err.println("[ERROR] " + message);
        }
    }
    
}
