package api

import (
	"database/sql"
	"errors"

	"github.com/go-redis/redis"
	"hermeez.co/fcm-app-server/datadog"
)

// Client represents a client that is capable of handling
// all requests to V1 endpoints. It contains necessary dependencies
// for handling those requests.
type Client struct {
	broker       *Broker
	dbClient     *sql.DB
	ddClient     *datadog.Client
	ddSampleRate float64
	redisClient  *redis.Client
}

// NewClient validates the dependencies it was provided and returns
// a new API client for handling requests.
func NewClient(dbClient *sql.DB, ddClient *datadog.Client, ddSampleRate float64, redisClient *redis.Client) (*Client, error) {
	if dbClient == nil {
		return nil, errors.New("invalid database client")
	}

	if ddClient == nil {
		return nil, errors.New("invalid datadog client")
	}

	if ddSampleRate == 0.0 {
		return nil, errors.New("invalid datadog sample rate")
	}

	if redisClient == nil {
		return nil, errors.New("invalid redis client")
	}

	// Create SSE Broker
	broker := NewBroker(ddClient, ddSampleRate)

	return &Client{
		broker:       broker,
		dbClient:     dbClient,
		ddClient:     ddClient,
		ddSampleRate: ddSampleRate,
		redisClient:  redisClient,
	}, nil
}
