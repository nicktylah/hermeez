package redis

import (
	"fmt"
	"log"
	"strconv"
	"strings"
	"time"

	"hermeez.co/fcm-app-server/models"

	"github.com/go-redis/redis"
)

const (
	messagesPerRead = 1000
	blockTimeout    = time.Duration(3 * time.Minute)
)

type MessagesPayload struct {
	Messages   []models.Message
	Checkpoint string
}

// AddMessage adds a given message to the messages stream.
func AddMessage(msg *models.Message) (string, error) {
	// TODO(nick): uid should come from the request
	uid := "redacted"
	id, err := client.XAdd(
		&redis.XAddArgs{
			Stream: messagesStreamName,
			Values: map[string]interface{}{
				"uid":         uid,
				"addresses":   strings.Trim(strings.Join(strings.Fields(fmt.Sprint(msg.Addresses)), ","), "[]"),
				"attachment":  msg.Attachment,
				"body":        msg.Body,
				"contentType": msg.ContentType,
				"date":        time.Unix(msg.Date/1e3, 0).Format(time.RFC3339),
				"sender":      msg.Sender,
				"threadId":    msg.ThreadID,
				"type":        msg.Type,
			},
		}).Result()
	if err != nil {
		return "0-0", err
	}

	return id, nil
}

func ReadMessages(uid string, startID string) (*MessagesPayload, error) {
	cmd := client.XRead(&redis.XReadArgs{
		Streams: []string{messagesStreamName, startID},
		Count:   messagesPerRead,
		Block:   blockTimeout,
	})
	err := cmd.Err()
	if err != nil {
		return nil, err
	}

	// TODO(nick): Figure out structure and serialize to structs/json
	res, err := cmd.Result()
	if err != nil {
		log.Println("Error getting result", err)
		return nil, err
	}

	messages := make([]models.Message, 0)
	var lastID string
	for _, stream := range res {
		if stream.Stream != messagesStreamName {
			continue
		}

		for _, message := range stream.Messages {
			rawUID := message.Values["uid"]
			if rawUID == nil {
				continue
			}

			uidString, ok := rawUID.(string)
			if !ok || uidString != uid {
				continue
			}

			lastID = message.ID

			if message.Values["date"] == nil || message.Values["sender"] == nil || message.Values["threadId"] == nil || message.Values["type"] == nil {
				continue
			}

			var addresses []int64
			addressesString, ok := message.Values["addresses"].(string)
			if !ok {
				log.Println("Error parsing addresses from", message.Values["addresses"])
				continue
			}

			for _, addr := range strings.Split(addressesString, ",") {
				address, err := strconv.ParseInt(addr, 0, 64)
				if err != nil {
					log.Println("Error parsing address from", addr)
				}
				addresses = append(addresses, address)
			}

			date, err := time.Parse(time.RFC3339, message.Values["date"].(string))
			timestamp := date.UnixNano() / 1e6
			if err != nil {
				log.Println("Error parsing date from", message.Values["date"])
				continue
			}

			sender, err := strconv.Atoi(message.Values["sender"].(string))
			if err != nil {
				log.Println("Error parsing sender from", message.Values["sender"])
				continue
			}

			threadID, err := strconv.Atoi(message.Values["threadId"].(string))
			if err != nil {
				log.Println("Error parsing threadId from", message.Values["threadId"])
				continue
			}

			messageType, err := strconv.Atoi(message.Values["type"].(string))
			if err != nil {
				log.Println("Error parsing type from", message.Values["type"])
				continue
			}

			messages = append(messages, models.Message{
				Addresses:   addresses,
				Attachment:  message.Values["attachment"].(string),
				Body:        message.Values["body"].(string),
				ContentType: message.Values["contentType"].(string),
				Date:        timestamp,
				Sender:      int64(sender),
				ThreadID:    int64(threadID),
				Type:        messageType,
			})
		}
	}

	return &MessagesPayload{
		messages,
		lastID,
	}, nil
}
