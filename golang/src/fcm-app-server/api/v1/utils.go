package api

import (
	"encoding/json"

	"log"
	"net/http"
)

const (
	hostname       = "https://hermeez.co"
	pushyServerKey = ""
)

// BasicResponse is used by several API handler functions to indicate
// a success or error
type BasicResponse struct {
	Success bool   `json:"success"`
	Error   string `json:"error,omitempty"`
}

func jsonResponse(w http.ResponseWriter,
	content interface{},
	status uint32) {
	body, err := json.Marshal(content)
	if err != nil {
		log.Printf("Error serializing response to JSON. %v", content)
		w.Write([]byte("Internal server error"))
		w.WriteHeader(http.StatusInternalServerError)
		return
	}

	// Set the Content-Type
	w.Header().Set("Content-Type", "application/json")

	// Keep-Alive
	w.Header().Set("Connection", "keep-alive")

	// Write the HTTP Status
	w.WriteHeader(int(status))

	// Write the content
	w.Write(body)
}
