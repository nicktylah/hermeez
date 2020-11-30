package api

import (
	"fmt"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"encoding/json"

	"hermeez.co/fcm-app-server/datadog"
	"hermeez.co/fcm-app-server/redis"

	redis2 "github.com/go-redis/redis"
)

const (
	keepAliveMessage      = "ping"
	keepAliveFrequency    = 30 * time.Second
	maxConnectionDuration = 10 * time.Minute
)

// Broker represents a struct for keeping a list of clients (browsers)
// which are currently attached, and for broadcasting events to them.
// A single broker will be created in this program.
type Broker struct {
	ddClient     *datadog.Client
	ddSampleRate float64

	// Create a map of clients, the keys of the map are the channels
	// over which we can push messages to attached clients.
	clients map[chan *redis.MessagesPayload]bool

	// Channel into which new clients can be pushed
	newClients chan chan *redis.MessagesPayload

	// Channel into which disconnected clients should be pushed
	defunctClients chan chan *redis.MessagesPayload

	// Channel into which messages are pushed to be broadcast out
	// to attached clients.
	messages chan string
}

// NewBroker initiates a broker, starts channel listening, and starts a
// keep-alive goroutine to continually ping clients to ensure the connection
// stays open through ELB
func NewBroker(ddClient *datadog.Client, ddSampleRate float64) *Broker {
	b := &Broker{
		ddClient:       ddClient,
		ddSampleRate:   ddSampleRate,
		clients:        make(map[chan *redis.MessagesPayload]bool),
		newClients:     make(chan (chan *redis.MessagesPayload)),
		defunctClients: make(chan (chan *redis.MessagesPayload)),
		messages:       make(chan string),
	}
	go b.Start()
	go b.StartKeepAlive()
	return b
}

// Start begins a new goroutine. It handles the addition & removal of
// clients, as well as broadcasting messages out to clients that are
// currently attached.
func (b *Broker) Start() {
	go func() {
		for {
			// Block until we receive from one of the
			// three following channels.
			select {

			case clientChan := <-b.newClients:

				// There is a new client attached and we
				// want to start sending them messages.
				b.clients[clientChan] = true
				log.Println("Added new client")

			case clientChan := <-b.defunctClients:

				// A client has detached, stop sending messages
				delete(b.clients, clientChan)
				close(clientChan)
				log.Println("Removed client")

			case <-b.messages:

				b.ddClient.Histogram("connections", float64(len(b.clients)), nil, b.ddSampleRate)
				// Only used for keepAlive pings. For each
				// attached client, push the pings
				// into the client's message channel.
				for clientChan := range b.clients {
					clientChan <- nil
				}
			}
		}
	}()
}

// StartKeepAlive keeps a long running process to repeatedly send messages to connected
// clients to ensure the connection remains established
func (b *Broker) StartKeepAlive() {
	for {
		b.messages <- keepAliveMessage
		time.Sleep(keepAliveFrequency)
	}
}

// SendMessage can be called to send a message to all connected clients
func (b *Broker) SendMessage(msg string) {
	b.messages <- msg
}

// ServeHTTP listens for incoming requests and starts a process for each
// connected client
func (b *Broker) ServeHTTP(w http.ResponseWriter, r *http.Request) {

	// Make sure that the writer supports flushing.
	f, ok := w.(http.Flusher)
	if !ok {
		jsonResponse(w, "HTTP SSE unsupported", http.StatusNotAcceptable)
		return
	}

	// Create a new channel, over which the broker can
	// send this client messages.
	messageChan := make(chan *redis.MessagesPayload)
	shutdownChan := make(chan bool)

	// Add this client to the map of those that should
	// receive updates
	b.newClients <- messageChan

	// Set the headers related to event streaming
	w.Header().Set("Content-Type", "text/event-stream")
	w.Header().Set("Connection", "keep-alive")
	w.Header().Set("Cache-Control", "no-cache")
	w.Header().Set("Access-Control-Allow-Origin", "*")

	if methods, ok := r.Header["Access-Control-Request-Methods"]; ok {
		w.Header().Set("Access-Control-Allow-Methods", methods[0])
	} else {
		w.Header().Set("Access-Control-Allow-Methods", "POST, PUT, GET, OPTIONS, DELETE")
	}

	if headers, ok := r.Header["Access-Control-Request-Headers"]; ok {
		w.Header().Set("Access-Control-Allow-Headers", headers[0])
	}

	uid := r.URL.Query().Get("uid")
	checkpointString := r.URL.Query().Get("checkpoint")
	if uid == "" || checkpointString == "" {
		log.Println("Could not retrieve values from request")
		jsonResponse(w, "Could not retrieve values from request", http.StatusBadRequest)
		return
	}

	splitCheckpointString := strings.Split(checkpointString, "-")
	id, err := strconv.ParseInt(splitCheckpointString[0], 10, 64)
	seq := int64(0)
	if err != nil {
		log.Println("Invalid checkpoint provided")
		jsonResponse(w, "Invalid checkpoint provided", http.StatusBadRequest)
	}

	if len(splitCheckpointString) > 1 {
		seq, err = strconv.ParseInt(splitCheckpointString[1], 10, 64)
	}
	startID := fmt.Sprintf("%d-%d", id, seq)

	// Listen to the closing of the http connection via the CloseNotifier.
	// Setup message fetching loop.
	go func() {
		for {
			select {
			case <-shutdownChan:
				// Remove this client from the map of attached clients
				// when `EventHandler` exits.
				b.defunctClients <- messageChan
				log.Println("HTTP connection just closed.")
				return
			default:
				// Blocks for up to 3 minutes
				log.Println("Fetching messages...")
				payload, err := redis.ReadMessages(uid, startID)
				if err != nil {
					if err == redis2.Nil {
						continue
					}

					log.Println("Error reading messages", err)
					jsonResponse(w, "Error reading messages", http.StatusInternalServerError)
					shutdownChan <- true
					return
				}

				log.Println("Got", len(payload.Messages), "messages. LastCheckpoint:", payload.Checkpoint)
				messageChan <- payload
				startID = payload.Checkpoint
			}
		}
	}()

	// Perform shutdown either due to close notification or max time.  ELB will
	// not notify that a client-side connection to ELB has closed.  For this
	// reason, we must close connections after some period of time to prevent
	// the server from running out of available connections.  The client effect is
	// negligible as a new connection is automatically established.
	//
	// TODO: Replace with just CloseNotify when Amazon fixes ELB HTTP SSE disconnects
	closeNotifyChan := w.(http.CloseNotifier).CloseNotify()
	timeoutChan := time.After(maxConnectionDuration)
	go func() {
		select {
		case <-closeNotifyChan:
			shutdownChan <- true
		case <-timeoutChan:
			shutdownChan <- true
		}
	}()

	for {
		msg, open := <-messageChan
		// If our messageChan was closed, this means that the client has
		// disconnected.
		if !open {
			log.Println("messageChan is closed")
			break
		}

		// Keep alive ping
		if msg == nil {
			fmt.Fprintf(w, "data: %s\n\n", "ping")
			f.Flush()
			continue
		}

		messageBytes, err := json.Marshal(msg.Messages)
		if err != nil {
			log.Println("Error serializing messages", err)
			jsonResponse(w, "Error serializing messages", http.StatusBadGateway)
			return
		}
		fmt.Fprintf(
			w,
			"id: %s\ndata: %s\n\n",
			msg.Checkpoint,
			messageBytes)
		f.Flush()
	}

	log.Println("Finished HTTP request", r.URL.Path)
}
