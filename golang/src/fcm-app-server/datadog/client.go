package datadog

import (
	"github.com/DataDog/datadog-go/statsd"
	"time"
)

// Client is a wrapper for a Datadog StatsD client
type Client struct {
	*statsd.Client
}

func NewClient(namespace string) (*Client, error) {
	c, err := statsd.New("127.0.0.1:8125")
	if err != nil {
		return nil, err
	}

	c.Namespace = namespace
	return &Client{c}, nil
}

func (c *Client) HistogramMS(
	name string,
	start time.Time,
	tags []string,
	rate float64,
) error {
	return c.Histogram(
		name,
		float64(time.Since(start).Nanoseconds())/float64(1000000),
		tags,
		rate)
}
