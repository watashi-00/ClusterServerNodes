package hexacloud.core.utils;

import java.io.PrintStream;
import java.io.UnsupportedEncodingException;

public final class PrintStreamFactory {

    private PrintStreamFactory() {}

    public static PrintStream create(boolean error) {
        try {
            return new PrintStream(
            new DebugUtils.RedirectorOutputStream(error),
            true,
            CharsetUtils.resolve(CharsetUtils.UTF_8)
        );
        } catch (UnsupportedEncodingException e) {
            throw new AssertionError(e);
        }

    }
}