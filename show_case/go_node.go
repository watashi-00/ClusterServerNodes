package main

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"os"
	"os/signal"
	"syscall"
	"time"
)

const (
	port        = 3003
	gatewayHttp = "http://localhost:3001"
	cluster     = "watashi-00"
	token       = "watashi_secretKey"
)

func registerService() {
	url := fmt.Sprintf("%s/clusters/%s/register?%d", gatewayHttp, cluster, port)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		fmt.Printf("[Go Node] Error creating request: %v\n", err)
		return
	}
	req.Header.Set("X-Cluster-Token", token)

	client := &http.Client{Timeout: 3 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		fmt.Printf("[Go Node] Failed to register: %v\n", err)
		return
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	fmt.Printf("[Go Node] Registration response: %s\n", string(body))
}

func deregisterService() {
	url := fmt.Sprintf("%s/clusters/%s/deregister?localhost:%d", gatewayHttp, cluster, port)
	req, err := http.NewRequest("GET", url, nil)
	if err != nil {
		fmt.Printf("[Go Node] Error creating request: %v\n", err)
		return
	}
	req.Header.Set("X-Cluster-Token", token)

	client := &http.Client{Timeout: 3 * time.Second}
	resp, err := client.Do(req)
	if err != nil {
		fmt.Printf("[Go Node] Failed to deregister: %v\n", err)
		return
	}
	defer resp.Body.Close()

	body, _ := io.ReadAll(resp.Body)
	fmt.Printf("[Go Node] Deregistration response: %s\n", string(body))
}

func main() {
	fmt.Printf("[Go Node] Starting HTTP Mock Service on port %d...\n", port)

	mux := http.NewServeMux()
	mux.HandleFunc("/", func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		w.WriteHeader(http.StatusOK)
		w.Write([]byte(`{"status": "UP", "language": "Go", "message": "Hello from Go mock service"}`))
	})

	server := &http.Server{
		Addr:    fmt.Sprintf(":%d", port),
		Handler: mux,
	}

	go func() {
		if err := server.ListenAndServe(); err != http.ErrServerClosed {
			fmt.Printf("[Go Node] Server error: %v\n", err)
		}
	}()

	// Register service to the gateway
	registerService()
	fmt.Println("[Go Node] Service is ONLINE. Press Ctrl+C to exit and deregister.")

	// Set up channel to listen for shutdown signals
	stop := make(chan os.Signal, 1)
	signal.Notify(stop, os.Interrupt, syscall.SIGTERM)

	<-stop

	fmt.Println("\n[Go Node] Shutting down...")
	deregisterService()

	ctx, cancel := context.WithTimeout(context.Background(), 5*time.Second)
	defer cancel()
	server.Shutdown(ctx)
	fmt.Println("[Go Node] Service stopped.")
}
