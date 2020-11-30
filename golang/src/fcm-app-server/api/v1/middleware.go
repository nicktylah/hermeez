package api

import (
	"fmt"
	"net/http"
	"time"
)

const allHTTPMethods = "POST, PUT, GET, OPTIONS, DELETE"

// RequestMiddleware provides metrics about request rate and latency by endpoint.
// TODO(nick): Add authentication
func (c Client) RequestMiddleware(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		startTime := time.Now()

		tags := []string{fmt.Sprintf("endpoint:%s", r.URL.Path)}
		c.ddClient.Incr("request", tags, c.ddSampleRate)

		next(w, r)

		c.ddClient.HistogramMS("request.latency_ms", startTime, tags, c.ddSampleRate)
		return
	}
}

// AddCORS middleware adds CORS Headers
func (c Client) AddCORS(next http.HandlerFunc) http.HandlerFunc {
	return func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Access-Control-Allow-Credentials", "true")

		if origin, ok := r.Header["Origin"]; ok {
			w.Header().Set("Access-Control-Allow-Origin", origin[0])
		} else {
			w.Header().Set("Access-Control-Allow-Origin", "*")
		}

		if methods, ok := r.Header["Access-Control-Request-Methods"]; ok {
			w.Header().Set("Access-Control-Allow-Methods", methods[0])
		} else {
			w.Header().Set("Access-Control-Allow-Methods", allHTTPMethods)
		}

		if headers, ok := r.Header["Access-Control-Request-Headers"]; ok {
			w.Header().Set("Access-Control-Allow-Headers", headers[0])
		}

		if r.Method == "OPTIONS" {
			return
		}

		next(w, r)
	}
}
