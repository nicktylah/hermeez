package api

// TODO: Once more than one user is on board, this handler must be restored
// type TokenRequest struct {
// 	UID   string `json:"uid"`
// 	Token string `json:"token"`
// }

// TokenHandler takes in a request to store a pushy token and writes
// a row to the database
// func (a Client) TokenHandler(w http.ResponseWriter, r *http.Request) {
// 	defer r.Body.Close()
// 	if r.Method != "POST" {
// 		jsonResponse(w, &BasicResponse{Success: false, Error: "Method not allowed"}, http.StatusBadRequest)
// 		return
// 	}

// 	req := &TokenRequest{}
// 	err := json.NewDecoder(r.Body).Decode(req)
// 	if err != nil {
// 		log.Println("Error deserializing request", err)
// 		jsonResponse(w, &BasicResponse{Success: false, Error: "Bad Request"}, http.StatusBadRequest)
// 		return
// 	}

// 	query, args, err := sq.
// 		Insert("users").
// 		Columns("uid", "fcm_token").
// 		Values(req.UID, req.Token).
// 		Suffix("ON DUPLICATE KEY UPDATE fcm_token = VALUES(fcm_token)").
// 		ToSql()
// 	if err != nil {
// 		log.Println("Error generating insert token SQL", err)
// 		jsonResponse(
// 			w,
// 			&BasicResponse{
// 				Success: false,
// 				Error:   "Error storing token",
// 			},
// 			http.StatusInternalServerError,
// 		)
// 		return
// 	}

// 	res, err := a.dbClient.Exec(query, args...)
// 	if err != nil {
// 		log.Println("Failed to upsert FCM token", err)
// 		jsonResponse(
// 			w,
// 			&BasicResponse{
// 				Success: false,
// 				Error:   "Error storing token",
// 			},
// 			http.StatusInternalServerError,
// 		)
// 		return
// 	}

// 	rowsAffected, err := res.RowsAffected()
// 	log.Println("rows affected:", rowsAffected, err)

// 	jsonResponse(w, &BasicResponse{Success: true}, http.StatusOK)
// }
