package main

import (
	"net/http"
)

// Health check status response
func statusHandler(w http.ResponseWriter, r *http.Request) {
	w.Header().Set("content-type", "application/json")
	w.Write([]byte("ok"))
}
