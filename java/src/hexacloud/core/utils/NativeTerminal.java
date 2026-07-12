package hexacloud.core.utils;

import java.io.File;

public class NativeTerminal {
    static {
        try {
            File libFile = new File("libhexaterminal.so");
            if (libFile.exists()) {
                System.load(libFile.getAbsolutePath());
            } else {
                File tmpFile = new File("/tmp/libhexaterminal.so");
                if (tmpFile.exists()) {
                    System.load(tmpFile.getAbsolutePath());
                } else {
                    System.loadLibrary("hexaterminal");
                }
            }
        } catch (UnsatisfiedLinkError e) {
            System.err.println("Warning: libhexaterminal.so could not be loaded. Native terminal disabled.");
        }
    }

    public static native void initTerminal();
    public static native void resetTerminal();
    public static native void clearScreen();
    public static native void printAt(int x, int y, String text);
    public static native int readKey();
    public static native boolean saveConfig(String filepath, String content);
}
