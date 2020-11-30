package api

import (
	"encoding/json"
	"fmt"
	"log"
	"net/http"
	"strconv"
)

type metricRequest struct {
	MetricName  string  `json:"metricName"`
	MetricType  string  `json:"metricType"`
	MetricValue string `json:"metricValue"`
}

var (
	validMetricTypes = map[string]bool{
		"histogram": true,
		"count":     true,
	}
)

// MetricHandler takes in a request to submit a statsd metric and writes
// it to the local datadog daemon
func (a Client) MetricHandler(w http.ResponseWriter, r *http.Request) {
	defer r.Body.Close()
	if r.Method != "POST" {
		jsonResponse(w, &BasicResponse{Success: false, Error: "Method not allowed"}, http.StatusBadRequest)
		return
	}

	req := &metricRequest{}
	err := json.NewDecoder(r.Body).Decode(req)
	if err != nil {
		log.Println("Error deserializing request", err)
		jsonResponse(w, &BasicResponse{Success: false, Error: "Bad Request"}, http.StatusBadRequest)
		return
	}

	switch req.MetricType {
	case "histogram":
		metricValueFloat64, err := strconv.ParseFloat(req.MetricValue, 64)
		if err != nil {
			log.Println(fmt.Sprintf("Invalid metric value: %s, could not convert to float64", req.MetricType), err)
			jsonResponse(w, &BasicResponse{Success: false, Error: "Invalid metric value"}, http.StatusBadRequest)
		}
		a.ddClient.Histogram(req.MetricName, metricValueFloat64, nil, 1.0)
	case "count":
		metricValueInt64, err := strconv.ParseInt(req.MetricValue, 10, 64)
		if err != nil {
			log.Println(fmt.Sprintf("Invalid metric value: %s, could not convert to int64", req.MetricType), err)
			jsonResponse(w, &BasicResponse{Success: false, Error: "Invalid metric value"}, http.StatusBadRequest)
		}
		a.ddClient.Count(req.MetricName, metricValueInt64, nil, 1.0)
	default:
		log.Println(fmt.Sprintf("Invalid metric type: %s", req.MetricType), err)
		jsonResponse(w, &BasicResponse{Success: false, Error: "Invalid metric type. Valid types are: histogram, count"}, http.StatusBadRequest)
	}
}
