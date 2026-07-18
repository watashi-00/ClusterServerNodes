package hexacloud.core.utils;

import java.io.PrintStream;

public final class PrintStreamFactory {

    private PrintStreamFactory() {}

    public static PrintStream create(boolean error) {
        return new PrintStream(
            new DebugUtils.RedirectorOutputStream(error),
            true,
            CharsetUtils.resolve(CharsetUtils.UTF_8)
        );
    }
}