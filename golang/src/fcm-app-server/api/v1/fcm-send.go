package api

import (
	"bytes"
	"database/sql"
	"encoding/json"
	"fmt"
	"io/ioutil"
	"log"
	"net/http"
	"time"

	uuid "github.com/satori/go.uuid"
)

const (
	// fcmAddr              = "https://fcm.googleapis.com/fcm/send"
	pushyAddr      = "https://api.pushy.me/push"
	expirationTime = 2 * time.Minute
)

type SendMessageRequest struct {
	To                    string `json:"to"` // A Firebase UserID
	Content               string `json:"content"`
	Attachment            string `json:"attachment,omitempty"`
	AttachmentContentType string `json:"attachmentContentType,omitempty"`
	RecipientIDs          string `json:"recipientIds"`
	ConversationID        int    `json:"conversationId"`
}

type SendMessageResponse struct {
	Success      bool   `json:"success"`
	SentMessages int    `json:"sentMessages,omitEmpty"`
	Error        string `json:"error,omitempty"`
}

type PushyMessageData struct {
	Content                 string    `json:"content"`
	AttachmentPointer       string    `json:"attachmentPointer"`
	AttachmentContentType   string    `json:"attachmentContentType"`
	RecipientIDs            string    `json:"recipientIds"`
	ConversationID          int       `json:"conversationId"`
	SendRequestReceivedDate time.Time `json:"sendDate"`
}

type PushyMessage struct {
	To   string           `json:"to"` // A Pushy Token (NOT the same as UserID)
	Data PushyMessageData `json:"data"`
}

//type FCMResp struct {
//	MulticastID  int `json:"multicast_id"`
//	Success      int `json:"success"`
//	Failure      int `json:"failure"`
//	CanonicalIDs int `json:"canonical_ids"`
//	Results      []struct {
//		MessageID      string `json:"message_id"`
//		RegistrationID string `json:"registration_id,omitempty"`
//		Error          string `json:"error,omitempty"`
//	} `json:"results"`
//}

type PushyResponse struct {
	Success bool   `json:"success"`
	Error   string `json:"error"`
	ID      string `json:"id"`
	Info    struct {
		Devices int
	}
}

// Pushy send handler
func (a Client) PushySendHandler(w http.ResponseWriter, r *http.Request) {
	defer r.Body.Close()
	req := &SendMessageRequest{}
	reqBytes, err := ioutil.ReadAll(r.Body)
	if err != nil {
		log.Println("Error reading request body:", err)
		jsonResponse(w, &SendMessageResponse{Success: false, Error: "Bad Request"}, http.StatusBadRequest)
		return
	}

	err = json.Unmarshal(reqBytes, &req)
	if err != nil {
		log.Println("Error deserializing request:", err)
		jsonResponse(w, &SendMessageResponse{Success: false, Error: "Bad Request"}, http.StatusBadRequest)
		return
	}

	// Lookup Pushy Token in Hermes Database using req.To
	pushyToken, err := a.getPushyToken(req.To)
	if err != nil {
		log.Println("Unable to fetch Pushy token for user:", req.To, err)
		if err == sql.ErrNoRows {
			jsonResponse(w, &SendMessageResponse{Success: false, Error: "No user found"}, http.StatusNotFound)
		} else {
			jsonResponse(w, &SendMessageResponse{Success: false, Error: err.Error()}, http.StatusBadRequest)
		}
		return
	}

	var attachmentPointer string
	if req.Attachment != "" {
		log.Println("attachment", req.Attachment)
		// Set this attachment in memory KV store, for later retrieval
		attachmentId := uuid.NewV4().String()
		start := time.Now()
		err = a.redisClient.Set(attachmentId, req.Attachment, expirationTime).Err()
		if err != nil {
			a.ddClient.Incr("redis.set.error", nil, 1.0)
			log.Println("Unable to set attachment in Redis", err)
			jsonResponse(w, &SendMessageResponse{Success: false, Error: err.Error()}, http.StatusBadRequest)
			return
		}

		a.ddClient.HistogramMS("redis.set.latency_ms", start, nil, a.ddSampleRate)
		attachmentPointer = fmt.Sprintf("%s/fcm/attachment/%s", hostname, attachmentId)
		log.Println("attachmentPointer", attachmentPointer)
	} else {
		attachmentPointer = ""
	}

	// Compose MessageRequest
	sendMessage := &PushyMessage{
		To: pushyToken,
		Data: PushyMessageData{
			Content:                 req.Content,
			AttachmentPointer:       attachmentPointer,
			AttachmentContentType:   req.AttachmentContentType,
			RecipientIDs:            req.RecipientIDs,
			ConversationID:          req.ConversationID,
			SendRequestReceivedDate: time.Now(),
		},
	}

	// Compose a POST to the Pushy backend
	payloadBytes, err := json.Marshal(sendMessage)
	if err != nil {
		a.ddClient.Incr("pushy.request.error", nil, 1.0)
		log.Println("Error marshalling Pushy request body", err)
		jsonResponse(w, &SendMessageResponse{Success: false, Error: err.Error()}, http.StatusBadRequest)
		return
	}

	pushyReq, err := http.NewRequest("POST", pushyAddr, bytes.NewReader(payloadBytes))
	if err != nil {
		a.ddClient.Incr("pushy.request.error", nil, 1.0)
		log.Println("Error creating HTTP request", err)
		jsonResponse(w, &SendMessageResponse{Success: false, Error: err.Error()}, http.StatusBadRequest)
		return
	}

	pushyReq.Header.Set("Content-Type", "application/json")

	// Auth header is for FCM
	// pushyReq.Header.Set("Authorization", fmt.Sprintf("key=%s", pushyServerKey))
	// Query param is for Pushy
	q := pushyReq.URL.Query()
	q.Add("api_key", pushyServerKey)
	pushyReq.URL.RawQuery = q.Encode()

	res, err := http.DefaultClient.Do(pushyReq)
	if err != nil {
		a.ddClient.Incr("pushy.request.error", nil, 1.0)
		log.Println("Error creating Pushy request", err)
		jsonResponse(w, &SendMessageResponse{Success: false, Error: err.Error()}, http.StatusBadGateway)
		return
	}

	defer res.Body.Close()
	// Unmarshal the Pushy response
	pushyResponse := &PushyResponse{}
	err = json.NewDecoder(res.Body).Decode(pushyResponse)
	if err != nil {
		log.Println("Error deserializing Pushy response", err)
		resBytes, _ := ioutil.ReadAll(res.Body)
		log.Println("Response:", string(resBytes))
		jsonResponse(w, &SendMessageResponse{Success: false, Error: err.Error()}, http.StatusInternalServerError)
		return
	}

	if res.StatusCode > 200 {
		a.ddClient.Incr("pushy.request.error", nil, 1.0)
		log.Printf("Error with Pushy request. StatusCode: %d\nStatus: %s\nError: %s", res.StatusCode, res.Status, pushyResponse.Error)
		jsonResponse(
			w,
			&SendMessageResponse{Success: false, Error: fmt.Sprintf("Error requesting Pushy backend: %s", pushyResponse.Error)},
			http.StatusBadRequest)
		return
	}

	if !pushyResponse.Success || pushyResponse.Error != "" {
		// Some messages failed to send
		jsonResponse(
			w,
			&SendMessageResponse{Success: false, Error: pushyResponse.Error, SentMessages: 0},
			http.StatusBadRequest,
		)
		return
	}

	jsonResponse(
		w,
		&SendMessageResponse{Success: true, Error: "", SentMessages: 1},
		http.StatusOK,
	)

	// Code below is from V1 FCM implementation (replaced by Pushy)

	//// Unmarshal the FCM response
	//fcmRespBody := &FCMResp{}
	//err = json.NewDecoder(res.Body).Decode(fcmRespBody)
	//if err != nil {
	//	log.Println("Error deserializing FCM response", err)
	//	resBytes, _ := ioutil.ReadAll(res.Body)
	//	log.Println("Response:", string(resBytes))
	//	jsonResponse(w, &SendMessageResponse{Success: false, Error: err.Error()}, http.StatusInternalServerError)
	//	return
	//}
	//
	//if fcmRespBody.Failure > 0 || fcmRespBody.CanonicalIDs > 0 {
	//	// Some messages failed to send
	//	sentMessages := 0
	//	errors := ""
	//	for i, result := range fcmRespBody.Results {
	//		if result.RegistrationID != "" {
	//			errors += fmt.Sprintf("Message %d contained a registrationID; ", i)
	//		} else if result.Error != "" {
	//			errors += fmt.Sprintf("Message %d error: %s; ", i, result.Error)
	//		} else {
	//			sentMessages++
	//		}
	//	}
	//	jsonResponse(
	//		w,
	//		&SendMessageResponse{Success: false, Error: errors, SentMessages: sentMessages},
	//		http.StatusBadRequest,
	//	)
	//	return
	//}
	//
	//jsonResponse(
	//	w,
	//	&SendMessageResponse{Success: true, Error: "", SentMessages: len(fcmRespBody.Results)},
	//	http.StatusOK,
	//)
}

// Queries hermes database for this userID's FCM token
func (a Client) getPushyToken(userID string) (token string, err error) {
	// query, args, err := sq.
	// 	Select("fcm_token").
	// 	From("users").
	// 	Where(sq.Eq{"uid": userID}).
	// 	ToSql()
	// if err != nil {
	// 	return token, err
	// }

	// res := a.dbClient.QueryRow(query, args...)
	// if err != nil {
	// 	return token, err
	// }

	// This hardcoded token from Pushy corresponds to my Firebase UID: redacted
	// TODO: Once more than one user is on board, store these in a datastore
	// FIXME: If sending messages starts failing, a stale token here could be the culprit
	// This value can be obtained here: https://dashboard.pushy.me/apps/5c7488aa5ad49ff30dba3d11/devices
	token = "62ed8fe321756279f04ca7"

	// err = res.Scan(&token)
	// if err != nil {
	// 	return token, err
	// }

	return token, nil
}
