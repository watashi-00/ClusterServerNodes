package hexacloud.core.utils.common;

public class StrUtils {

    private StrUtils() {}
    
    public static String repeat(String str, int count) {
        StringBuilder sb = new StringBuilder(count * str.length());

        for (int i = 0; i < count; i++) {
            sb.append(str);
        }

        return sb.toString();
    }

    public static boolean isBlank(String str) {
        return str == null || str.trim().isEmpty();
    }
}
