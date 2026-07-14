const net = require('net');
const http = require('http');

const PORT = 3004;
const GATEWAY_TELNET_PORT = 3000;
const TOKEN = 'watashi_secretKey';

// 1. Create HTTP server to respond to gateway pings
const server = http.createServer((req, res) => {
    const cpu = (1.5 + Math.random() * 8.5).toFixed(1);
    const ram = (35.2 + Math.random() * 15.6).toFixed(1);
    res.writeHead(200, { 'Content-Type': 'application/json' });
    res.end(JSON.stringify({ 
        status: 'UP', 
        language: 'NodeJS', 
        cpu: parseFloat(cpu), 
        ram: parseFloat(ram),
        message: 'Hello from Node mock service' 
    }));
});

// 2. Function to register to Telnet Gateway
function registerService() {
    const socket = net.createConnection({ port: GATEWAY_TELNET_PORT, host: '127.0.0.1' }, () => {
        socket.write(`${TOKEN} REGISTER ${PORT}\n`);
    });

    socket.on('data', (data) => {
        console.log(`[Node Node] Registration response: ${data.toString().trim()}`);
        socket.destroy();
    });

    socket.on('error', (err) => {
        console.error(`[Node Node] Failed to register: ${err.message}`);
    });
}

// 3. Function to deregister from Telnet Gateway
function deregisterService(callback) {
    const socket = net.createConnection({ port: GATEWAY_TELNET_PORT, host: '127.0.0.1' }, () => {
        socket.write(`${TOKEN} DEREGISTER localhost:${PORT}\n`);
    });

    socket.on('data', (data) => {
        console.log(`[Node Node] Deregistration response: ${data.toString().trim()}`);
        socket.destroy();
        if (callback) callback();
    });

    socket.on('error', (err) => {
        console.error(`[Node Node] Failed to deregister: ${err.message}`);
        if (callback) callback();
    });
}

// 4. Start HTTP Server
server.listen(PORT, () => {
    console.log(`[Node Node] Starting HTTP Mock Service on port ${PORT}...`);
    registerService();
    console.log(`[Node Node] Service is ONLINE. Press Ctrl+C to exit and deregister.`);
});

// 5. Graceful shutdown
process.on('SIGINT', () => {
    console.log('\n[Node Node] Shutting down...');
    deregisterService(() => {
        server.close(() => {
            console.log('[Node Node] Service stopped.');
            process.exit(0);
        });
    });
});
