package hexacloud.core.utils;

public class DebugUtils {

    private static boolean debugEnabled = true;

    public static void setDebugEnabled(boolean enabled) {
        debugEnabled = enabled;
    }

    public static void log(String message) {
        if(debugEnabled) {
            System.out.println("[DEBUG] " + message);
        }
    }
    
}
