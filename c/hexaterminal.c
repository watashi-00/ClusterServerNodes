#include <jni.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <unistd.h>
#include <termios.h>
#include <fcntl.h>

static struct termios orig_termios;
static int raw_mode_active = 0;

JNIEXPORT void JNICALL Java_hexacloud_core_utils_NativeTerminal_initTerminal(JNIEnv *env, jclass clazz) {
    if (raw_mode_active) return;

    // Save current terminal settings
    if (tcgetattr(STDIN_FILENO, &orig_termios) == -1) return;

    struct termios raw = orig_termios;
    // Disable canonical mode (line buffering) and echo
    raw.c_lflag &= ~(ECHO | ICANON);
    
    // Apply raw settings
    if (tcsetattr(STDIN_FILENO, TCSAFLUSH, &raw) == -1) return;

    raw_mode_active = 1;

    // Clear screen and hide cursor
    printf("\033[2J\033[H\033[?25l");
    fflush(stdout);
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_NativeTerminal_resetTerminal(JNIEnv *env, jclass clazz) {
    if (!raw_mode_active) return;

    // Restore original settings and show cursor
    tcsetattr(STDIN_FILENO, TCSAFLUSH, &orig_termios);
    raw_mode_active = 0;
    printf("\033[?25h\033[0m\n");
    fflush(stdout);
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_NativeTerminal_clearScreen(JNIEnv *env, jclass clazz) {
    printf("\033[2J\033[H");
    fflush(stdout);
}

JNIEXPORT void JNICALL Java_hexacloud_core_utils_NativeTerminal_printAt(JNIEnv *env, jclass clazz, jint x, jint y, jstring text) {
    const char *str = (*env)->GetStringUTFChars(env, text, NULL);
    if (str == NULL) return;

    // Move cursor to (y, x) (note: ANSI escape code expects line, column)
    printf("\033[%d;%dH%s", y, x, str);
    fflush(stdout);

    (*env)->ReleaseStringUTFChars(env, text, str);
}

JNIEXPORT jint JNICALL Java_hexacloud_core_utils_NativeTerminal_readKey(JNIEnv *env, jclass clazz) {
    // Configure stdin to non-blocking
    int flags = fcntl(STDIN_FILENO, F_GETFL, 0);
    fcntl(STDIN_FILENO, F_SETFL, flags | O_NONBLOCK);

    char c;
    int n = read(STDIN_FILENO, &c, 1);

    if (n > 0) {
        if (c == 27) { // Escape sequence
            char seq[2];
            if (read(STDIN_FILENO, &seq[0], 1) > 0 && read(STDIN_FILENO, &seq[1], 1) > 0) {
                if (seq[0] == '[') {
                    switch (seq[1]) {
                        case 'A': fcntl(STDIN_FILENO, F_SETFL, flags); return 1000; // UP arrow
                        case 'B': fcntl(STDIN_FILENO, F_SETFL, flags); return 1001; // DOWN arrow
                        case 'C': fcntl(STDIN_FILENO, F_SETFL, flags); return 1002; // RIGHT arrow
                        case 'D': fcntl(STDIN_FILENO, F_SETFL, flags); return 1003; // LEFT arrow
                    }
                }
            }
        }
        fcntl(STDIN_FILENO, F_SETFL, flags);
        return (jint)c;
    }

    // Restore blocking flags
    fcntl(STDIN_FILENO, F_SETFL, flags);
    return -1;
}

JNIEXPORT jboolean JNICALL Java_hexacloud_core_utils_NativeTerminal_saveConfig(JNIEnv *env, jclass clazz, jstring filepath, jstring content) {
    const char *path = (*env)->GetStringUTFChars(env, filepath, NULL);
    const char *body = (*env)->GetStringUTFChars(env, content, NULL);
    if (path == NULL || body == NULL) return JNI_FALSE;

    FILE *file = fopen(path, "w");
    if (file == NULL) {
        if (path) (*env)->ReleaseStringUTFChars(env, filepath, path);
        if (body) (*env)->ReleaseStringUTFChars(env, content, body);
        return JNI_FALSE;
    }

    fprintf(file, "%s", body);
    fclose(file);

    (*env)->ReleaseStringUTFChars(env, filepath, path);
    (*env)->ReleaseStringUTFChars(env, content, body);
    return JNI_TRUE;
}
