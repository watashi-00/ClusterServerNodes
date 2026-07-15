package hexacloud.core.tui.view;

import java.util.List;
import hexacloud.core.tui.TerminalUI;
import hexacloud.core.tui.TuiRenderer;
import hexacloud.core.tui.TuiState;
import hexacloud.core.utils.DebugUtils;
import hexacloud.core.utils.NativeTerminal;
import static hexacloud.core.tui.TuiConstants.*;

/**
 * Handles visual rendering for the Full Logs View.
 */
public class FullLogsViewRenderer {
    private final TerminalUI tui;
    private final TuiRenderer mainRenderer;

    public FullLogsViewRenderer(TerminalUI tui, TuiRenderer mainRenderer) {
        this.tui = tui;
        this.mainRenderer = mainRenderer;
    }

    public void draw() {
        TuiState state = tui.state();
        mainRenderer.drawBox(2, 5, 110, 22, "DETAILED SYSTEM LOGS", true);

        List<DebugUtils.LogEntry> logs = DebugUtils.getAllLogs();
        int y = 6;
        
        tui.adjustLogsViewport(logs.size(), 16);

        if (logs.isEmpty()) {
            NativeTerminal.printAt(4, y, "No logs recorded yet.");
        } else {
            for (int i = 0; i < 16; i++) {
                int index = state.logViewportStart + i;
                if (index >= logs.size()) break;

                DebugUtils.LogEntry entry = logs.get(index);
                String logLine = entry.toString();
                String prefix = index == state.selectedLogIndex ? "➔ " : "  ";
                String clearedLine = prefix + logLine + "                                                                                                    ";
                if (clearedLine.length() > 103) {
                    clearedLine = clearedLine.substring(0, 103);
                }

                if (entry.getLevel() == DebugUtils.LogLevel.ERROR) {
                    NativeTerminal.printAt(4, y, RED + clearedLine + RESET);
                } else if (entry.getLevel() == DebugUtils.LogLevel.INFO) {
                    NativeTerminal.printAt(4, y, CYAN + clearedLine + RESET);
                } else {
                    NativeTerminal.printAt(4, y, clearedLine);
                }
                y++;
            }
            if (state.logViewportStart > 0) {
                NativeTerminal.printAt(108, 6, WHITE_BOLD + "▲" + RESET);
            }
            if (state.logViewportStart + 16 < logs.size()) {
                NativeTerminal.printAt(108, 21, WHITE_BOLD + "▼" + RESET);
            }
        }

        NativeTerminal.printAt(2, 23, WHITE_BOLD + "Controls:" + RESET + " [Backspace] Back to Dashboard  [UP/DOWN] Scroll logs");
    }
}
