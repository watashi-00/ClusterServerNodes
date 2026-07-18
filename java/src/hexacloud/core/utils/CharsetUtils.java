package hexacloud.core.utils;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

public enum CharsetUtils {
    UTF_8,
    US_ASCII,
    ISO_8859_1;

    public static Charset resolve(CharsetUtils charset) {
        return switch (charset) {
            case UTF_8 -> StandardCharsets.UTF_8;
            case US_ASCII -> StandardCharsets.US_ASCII;
            case ISO_8859_1 -> StandardCharsets.ISO_8859_1;
            default -> throw new IllegalArgumentException("Unsupported charset: " + charset);
        };
    }
}