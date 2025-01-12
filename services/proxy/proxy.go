package main

import (
	"crypto/tls"
	"fmt"
	"io"
	"log"
	"net"
	"net/http"
	"net/http/httputil"
	"net/url"
	"os"
	"strings"

	"dflipse.nl/fit-proxy/tracing"
	"golang.org/x/net/http2"
	"golang.org/x/net/http2/h2c"
)

func getSpanUID(traceparent tracing.TraceParentData) string {
	queryHost := os.Getenv("COLLECTOR_HOST")
	queryUrl := fmt.Sprintf("http://%s/v1/spanid/%s", queryHost, traceparent.String())
	resp, err := http.Get(queryUrl)

	if err != nil {
		log.Printf("Failed to get span ID: %v\n", err)
		return ""
	}
	defer resp.Body.Close()

	spanID, err := io.ReadAll(resp.Body)
	if err != nil {
		log.Printf("Failed to read span ID response: %v\n", err)
		return ""
	}

	return string(spanID)
}

func parseFautload(tracestate tracing.TraceStateData) []string {
	faultload := tracestate.GetWithDefault("faultload", "")
	if faultload == "" {
		return nil
	}

	var decodedFaults []string
	for _, fault := range strings.Split(faultload, ":") {
		decodedFault, err := url.QueryUnescape(fault)
		if err != nil {
			log.Printf("Failed to decode fault: %v\n", err)
			continue
		}
		decodedFaults = append(decodedFaults, decodedFault)
	}

	return decodedFaults
}

// Proxy handler that inspects and forwards HTTP requests and responses
func proxyHandler(targetHost string, useHttp2 bool) http.Handler {
	// Parse the target URL
	targetURL, err := url.Parse(targetHost)
	if err != nil {
		log.Fatalf("Failed to parse target host: %v\n", err)
	}

	// Create the reverse proxy
	proxy := httputil.NewSingleHostReverseProxy(targetURL)

	if useHttp2 {
		proxy.Transport = &http2.Transport{
			AllowHTTP: true,
			DialTLS: func(network, addr string, cfg *tls.Config) (net.Conn, error) {
				return net.Dial(network, addr)
			},
		}
	}

	// Wrap the proxy with a custom handler to inspect requests and responses
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		// Inspect request before forwarding
		fmt.Printf("Received request: %s %s\n", r.Method, r.URL)

		// Get "traceparent" and "tracestate" headers
		traceparent := r.Header.Get("traceparent")
		parent := tracing.ParseTraceParent(traceparent)

		if parent == nil {
			proxy.ServeHTTP(w, r)
			return
		}

		tracestate := r.Header.Get("tracestate")
		state := tracing.ParseTraceState(tracestate)

		fmt.Printf("Traceparent: %+v\n", parent)
		fmt.Printf("Tracestate: %+v\n", state)

		spanUID := getSpanUID(*parent)
		log.Printf("Span UID: %s\n", spanUID)

		faultloadUids := parseFautload(*state)
		log.Printf("Fault injection: %s\n", faultloadUids)

		for _, faultUid := range faultloadUids {
			log.Printf("Checking fault UID: %s=%s?\n", faultUid, spanUID)

			if faultUid == spanUID {
				log.Printf("Injecting fault: HTTP error\n")
				http.Error(w, "Injected fault: HTTP error", http.StatusInternalServerError)
				return
			}
		}

		// Log request body (if it's a POST request)
		// if r.Method == "POST" || r.Method == "PUT" {
		// 	fmt.Printf("Request body: %s\n", r.Body)
		// }

		// Set the "tracestate" header before forwarding the request
		if state.HasKeys() {
			r.Header.Set("tracestate", state.String())
		}

		// Forward the request to the target server
		proxy.ServeHTTP(w, r)
	})
}

func main() {
	// Set up the proxy host and target
	proxyHost := os.Getenv("PROXY_HOST")     // Proxy server address
	proxyTarget := os.Getenv("PROXY_TARGET") // Target server address

	useHttp2 := os.Getenv("USE_HTTP2") == "true"

	// Start an HTTP/2 server with a custom reverse proxy handler
	var httpServer *http.Server

	if useHttp2 {
		httpServer = &http.Server{
			Addr:    proxyHost,
			Handler: h2c.NewHandler(proxyHandler(proxyTarget, useHttp2), &http2.Server{}),
		}
	} else {
		httpServer = &http.Server{
			Addr:    proxyHost,
			Handler: proxyHandler(proxyTarget, useHttp2),
		}
	}

	// Start the reverse proxy server
	log.Printf("Starting reverse proxy on %s\n", proxyHost)
	err := httpServer.ListenAndServe()
	// err := httpServer.ListenAndServeTLS("cert.pem", "key.pem") // Requires SSL certificates for HTTP/2

	if err != nil {
		log.Fatalf("Error starting proxy server: %v\n", err)
	}
}
