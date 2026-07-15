# Release Notes: v1.0.0.4-beta

We are proud to release version **1.0.0.4-beta** of the GateBridge framework! This release introduces major enhancements to JNI platform portability, responsive fullscreen terminal rendering, standard enterprise logging integrations (SLF4J/Log4j2), and a pure Java raw mode fallback for non-x86/non-Linux systems.

---

## 🚀 Key Features & Enhancements

### 1. High-Performance Platform Portability & Custom JNI Paths
*   **Custom Library Path Configuration**: Developers can now load custom JNI terminal binaries (e.g. macOS `.dylib` or Windows `.dll`) using the `-Dgatebridge.jni.path` JVM argument, `GATEBRIDGE_JNI_PATH` environment variable, or programmatically via `NativeTerminal.loadJni("/path/to/lib")`.
*   **Automatic JAR Resource Packaging & Extraction**: Packaged native binaries inside the JAR resources directory (`/native/*`) are automatically extracted to a local temp file and loaded at startup.
*   **Unix `stty` Fallback (No Compilation Needed)**: If JNI is missing or disabled on Unix systems (Linux/macOS), the terminal automatically falls back to spawning process-based raw mode controls (`stty raw -echo < /dev/tty`).

### 2. Responsive Fullscreen Dashboard Layout
*   **Dynamic Width & Height Resolution**: Renderers query terminal dimensions using Unix JNI `ioctl` or `GetConsoleScreenBufferInfo` on Windows to dynamically adapt layouts to 100% of the terminal size.
*   **Scrolling Content expansion**: When expanded, panels (System Logs, Events list) automatically grow to fit the maximum rows, showing significantly more information.

### 3. Core SLF4J Delegation & Log4j 2 Support
*   **Abstraction Layer**: Refactored the core logger `DebugUtils` to route framework logs through the standard SLF4J facade, enabling direct integrations with Log4j 2, Logback, and cloud providers (Datadog, Kibana).
*   **Type-Safe `LogLevel` Enum**: Replaced raw String log levels with a type-safe `LogLevel` enum (`DEBUG`, `INFO`, `WARN`, `ERROR`).
*   **Full Exception Stacktraces**: Logging calls with exceptions now forward the complete stacktrace to SLF4J while keeping the TUI logs panel clean with single-line summaries.

### 4. Git Housekeeping
*   **Ignored Files**: Configured `.gitignore` to whitelist packaged resources in `java/resources/native/**` while ignoring `CODE_REVIEW.md` developer notes from being committed.

---

## 📦 Commit Changelog (since v1.0.0.3-beta)

*   `b02f8a0` - build: bump version to 1.0.0.4-beta
*   `4392926` - feat: allow custom JNI path configuration and add cross-compilation documentation
*   `38514b2` - feat: implement pure Java stty Unix raw mode fallback with ANSI key parser
*   `a85b8de` - feat: package JNI library in JAR resources and auto-extract/load it dynamically at runtime
*   `b7633c7` - feat: implement native ioctl JNI size detection and Unix tty process fallback
*   `d51c629` - feat: make TUI layout completely dynamic and responsive to full terminal window size W and H
*   `e02bde3` - docs: add Logging System documentation covering SLF4J, Log4j2 and TUI redirection
*   `4c1e318` - refactor: introduce LogLevel enum to replace raw String logging levels
*   `4ae06d7` - feat: delegate framework logging to SLF4J facade and ignore CODE_REVIEW.md in .gitignore
