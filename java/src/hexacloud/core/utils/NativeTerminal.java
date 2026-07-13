package hexacloud.core.utils;

import java.io.File;
import java.io.FileWriter;

public class NativeTerminal {
    private static boolean loaded = false;

    static {
        String[] possiblePaths = {
            "libhexaterminal.so",
            "java/libhexaterminal.so",
            "/tmp/libhexaterminal.so",
            "libhexaterminal.dylib",
            "java/libhexaterminal.dylib",
            "/tmp/libhexaterminal.dylib",
            "hexaterminal.dll",
            "java/hexaterminal.dll"
        };
        for (String path : possiblePaths) {
            try {
                File file = new File(path);
                if (file.exists()) {
                    System.load(file.getAbsolutePath());
                    loaded = true;
                    break;
                }
            } catch (Throwable t) {
                // Try next path
            }
        }
        if (!loaded) {
            try {
                System.loadLibrary("hexaterminal");
                loaded = true;
            } catch (Throwable t) {
                System.err.println("Warning: libhexaterminal could not be loaded. Native JNI raw input disabled. Falling back to Java console emulation.");
            }
        }
    }

    private static native void initTerminal0();
    private static native void resetTerminal0();
    private static native void clearScreen0();
    private static native void printAt0(int x, int y, String text);
    private static native int readKey0();
    private static native boolean saveConfig0(String filepath, String content);

    public static void initTerminal() {
        if (loaded) {
            try {
                initTerminal0();
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
    }

    public static void resetTerminal() {
        if (loaded) {
            try {
                resetTerminal0();
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
    }

    public static void clearScreen() {
        if (loaded) {
            try {
                clearScreen0();
                return;
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
        // ANSI escape sequence to clear screen, move cursor home and clear scrollback buffer
        System.out.print("\u001B[2J\u001B[H\u001B[3J");
        System.out.flush();
    }

    public static void printAt(int x, int y, String text) {
        if (loaded) {
            try {
                printAt0(x, y, text);
                return;
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
        // ANSI escape sequence to position cursor at y, x and print text
        System.out.print("\u001B[" + y + ";" + x + "H" + text);
        System.out.flush();
    }

    public static int readKey() {
        if (loaded) {
            try {
                return readKey0();
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
        try {
            if (System.in.available() > 0) {
                int c = System.in.read();
                if (c == 27) { // Escape sequence parser for fallback mode
                    if (System.in.available() > 0) {
                        int c2 = System.in.read();
                        if (c2 == '[') {
                            if (System.in.available() > 0) {
                                int c3 = System.in.read();
                                if (c3 == 'A') return 1000; // UP Arrow
                                if (c3 == 'B') return 1001; // DOWN Arrow
                            }
                        }
                    }
                }
                return c;
            }
        } catch (Exception e) {
            // Ignored
        }
        return -1;
    }

    public static boolean saveConfig(String filepath, String content) {
        if (loaded) {
            try {
                return saveConfig0(filepath, content);
            } catch (UnsatisfiedLinkError e) {
                // Fallback
            }
        }
        try (FileWriter fw = new FileWriter(filepath)) {
            fw.write(content);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
