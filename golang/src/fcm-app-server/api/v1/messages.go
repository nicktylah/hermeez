package api

import (
	"encoding/json"
	"io/ioutil"
	"log"
	"net/http"

	"hermeez.co/fcm-app-server/models"
	"hermeez.co/fcm-app-server/redis"
)

func (a Client) MessagesHandler(w http.ResponseWriter, r *http.Request) {
	defer r.Body.Close()

	switch r.Method {
	case "GET":
		// Call the broker here to stream messages over SSE
		a.broker.ServeHTTP(w, r)
	case "POST":
		a.writeMessage(w, r)
	default:
		jsonResponse(w, "Method not supported", http.StatusBadRequest)
	}
}

func (a Client) writeMessage(w http.ResponseWriter, r *http.Request) {
	defer r.Body.Close()
	messageBytes, err := ioutil.ReadAll(r.Body)
	if err != nil {
		log.Println("Error reading message data", err)
		return
	}

	message := &models.Message{}
	err = json.Unmarshal(messageBytes, message)
	if err != nil {
		log.Printf("Error unmarshalling message data: %s", string(messageBytes))
		log.Println(err)
		return
	}

	_, err = redis.AddMessage(message)
	if err != nil {
		a.ddClient.Incr("redis.insert_message.error", nil, 1.0)
		log.Println("Error writing to Redis", err)
		return
	}
}
