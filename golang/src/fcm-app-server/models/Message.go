package models

// TODO(nick): Shared protobuf would be nice
type Message struct {
	Addresses   []int64 `json:"addresses,omitempty"`
	Attachment  string  `json:"attachment,omitempty"`
	Body        string  `json:"body,omitempty"`
	ContentType string  `json:"contentType,omitempty"`
	Date        int64   `json:"date"`
	Sender      int64   `json:"sender"`
	ThreadID    int64   `json:"threadId"`
	Type        int     `json:"type"`
}
