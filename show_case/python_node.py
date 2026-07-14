import sys
import http.server
import socketserver
import urllib.request
import urllib.error
import threading
import time

PORT = 3005
GATEWAY_HTTP = "http://localhost:3001"
CLUSTER = "watashi-00"
TOKEN = "watashi_secretKey"

class MockServiceHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self):
        import random
        cpu = round(random.uniform(1.0, 9.0), 1)
        ram = round(random.uniform(25.0, 45.0), 1)
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.end_headers()
        response_json = f'{{"status": "UP", "language": "Python", "cpu": {cpu}, "ram": {ram}, "message": "Hello from Python mock service"}}'
        self.wfile.write(response_json.encode('utf-8'))

    def log_message(self, format, *args):
        # Prevent default logging to keep terminal output clean
        pass

def register_service():
    url = f"{GATEWAY_HTTP}/clusters/{CLUSTER}/register?{PORT}"
    req = urllib.request.Request(url)
    req.add_header("X-Cluster-Token", TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=3) as response:
            print(f"[Python Node] Registration response: {response.read().decode().strip()}")
    except urllib.error.URLError as e:
        print(f"[Python Node] Failed to register: {e}")

def deregister_service():
    url = f"{GATEWAY_HTTP}/clusters/{CLUSTER}/deregister?localhost:{PORT}"
    req = urllib.request.Request(url)
    req.add_header("X-Cluster-Token", TOKEN)
    try:
        with urllib.request.urlopen(req, timeout=3) as response:
            print(f"[Python Node] Deregistration response: {response.read().decode().strip()}")
    except urllib.error.URLError as e:
        print(f"[Python Node] Failed to deregister: {e}")

if __name__ == "__main__":
    print(f"[Python Node] Starting HTTP Mock Service on port {PORT}...")
    handler = MockServiceHandler
    httpd = socketserver.TCPServer(("", PORT), handler)

    # Run HTTP ping responder server in background thread
    server_thread = threading.Thread(target=httpd.serve_forever)
    server_thread.daemon = True
    server_thread.start()

    # Register service to the gateway
    register_service()

    print("[Python Node] Service is ONLINE. Press Ctrl+C to exit and deregister.")
    
    try:
        while True:
            time.sleep(1)
    except KeyboardInterrupt:
        print("\n[Python Node] Shutting down...")
        deregister_service()
        httpd.shutdown()
        print("[Python Node] Service stopped.")
