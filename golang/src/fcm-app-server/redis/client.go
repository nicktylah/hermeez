package redis

import (
	"github.com/go-redis/redis"
)

const redisPoolSize = 20

var (
	messagesStreamName = "messages-stg"
	client             *redis.Client
)

func NewClient() *redis.Client {
	client = redis.NewClient(&redis.Options{
		Addr:        "redacted",
		PoolSize:    redisPoolSize,
		Password:    "", // no password set
		DB:          0,  // use default DB
		ReadTimeout: -1,
	})

	return client
}
