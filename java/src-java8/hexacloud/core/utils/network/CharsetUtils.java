package hexacloud.core.utils.network;

public enum CharsetUtils {
    UTF_8,
    US_ASCII,
    ISO_8859_1;

    public static String resolve(CharsetUtils charset) {
        switch (charset) {
            case UTF_8:
                return "UTF-8";

            case US_ASCII:
                return "US-ASCII";

            case ISO_8859_1:
                return "ISO-8859-1";

            default:
                throw new IllegalArgumentException("Unsupported charset: " + charset);
        }
    }
}