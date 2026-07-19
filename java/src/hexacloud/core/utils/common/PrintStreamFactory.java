package hexacloud.core.utils.common;

import java.io.PrintStream;
import hexacloud.core.utils.network.CharsetUtils;

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