#!/bin/bash
# Compile JNI Shared Library if needed
if [ ! -f "java/libhexaterminal.so" ] || [ ! -f "/tmp/libhexaterminal.so" ]; then
    echo "Compiling Native JNI Terminal Library..."
    gcc -shared -fPIC -o java/libhexaterminal.so -I/usr/lib/jvm/java-26-openjdk/include -I/usr/lib/jvm/java-26-openjdk/include/linux c/hexaterminal.c
    cp java/libhexaterminal.so /tmp/libhexaterminal.so
fi

# Compile Java project
echo "Compiling Java Sources..."
find java/src -name "*.java" | xargs javac -d /tmp

# Launch TUI monitor
echo "Launching Hexacloud Telemetry Monitor..."
java -cp /tmp hexacloud.application.MonitorMain
