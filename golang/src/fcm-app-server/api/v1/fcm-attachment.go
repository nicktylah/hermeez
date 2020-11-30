package api

import (
	"log"
	"net/http"
	"strings"
	"time"
)

// Attachment request handler
func (a Client) FCMAttachmentHandler(w http.ResponseWriter, r *http.Request) {

	splitPath := strings.Split(r.URL.Path, "/")
	key := splitPath[len(splitPath)-1]
	if key == "" {
		log.Printf("No key found in %s", r.URL.String())
	}

	log.Println("Looking up key", key)

	// Lookup uuid in redis
	start := time.Now()
	val, err := a.redisClient.Get(key).Result()
	if err != nil {
		a.ddClient.Incr("redis.get.error", nil, 1.0)
		log.Println("Error retrieving attachment", err)
		w.Write([]byte(""))
		return
	}

	a.ddClient.HistogramMS("redis.get.latency_ms", start, nil, a.ddSampleRate)
	log.Println("Found attachment", val)
	w.Write([]byte(val))
}
