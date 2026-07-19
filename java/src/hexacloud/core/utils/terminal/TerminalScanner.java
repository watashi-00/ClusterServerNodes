package hexacloud.core.utils.terminal;

import java.util.Scanner;

public class TerminalScanner {

    private static final Scanner SCANNER = new Scanner(System.in);

    private TerminalScanner() {}

    /**
     * Reads a full line of text from standard input safely.
     * 
     * @return the trimmed input line, or an empty string if EOF.
     */
    public static synchronized String readLine() {
        if (SCANNER.hasNextLine()) {
            return SCANNER.nextLine().trim();
        }
        return "";
    }

    /**
     * Reads the next word/token from standard input safely.
     * 
     * @return the trimmed token, or an empty string if EOF.
     */
    public static synchronized String readToken() {
        if (SCANNER.hasNext()) {
            return SCANNER.next().trim();
        }
        return "";
    }
}
