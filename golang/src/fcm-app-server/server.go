package main

import (
	"context"
	"database/sql"
	"flag"
	"log"
	"net/http"

	"hermeez.co/fcm-app-server/api/v1"
	"hermeez.co/fcm-app-server/datadog"
	"hermeez.co/fcm-app-server/redis"

	_ "github.com/go-sql-driver/mysql"
	"github.com/zabawaba99/firego"
)

const (
	mysqlDSN             = ""
	hostname             = "https://hermeez.co"
	firebaseDatabaseAddr = "https://android-messages-6c275.firebaseio.com"
)

var (
	addr     = flag.String("addr", "0.0.0.0:9000", "TCP address to listen for requests.")
	addrTLS  = flag.String("addrTLS", "0.0.0.0:443", "TCP address to listen to TLS (aka SSL or HTTPS) requests. Leave empty for disabling TLS")
	certFile = flag.String("certFile", "", "Path to TLS certificate file")
	// serviceAccountKey = flag.String("serviceAccountKey", "./TextTop-b267a6743fcb.json", "Path to GCP service account key for fcm-app-server")
	dbClient     *sql.DB
	keyFile      = flag.String("keyFile", "", "Path to TLS key file")
	dir          = flag.String("dir", "/home/ubuntu/fcm-app-server/apk", "Directory to serve static files from")
	redisClient  = redis.NewClient()
	fb           *firego.Firebase
	ddClient     *datadog.Client
	ddSampleRate = 1.0
)

func main() {
	// Parse command-line flags.
	flag.Parse()

	// Grab project config
	// jsonKey, err := ioutil.ReadFile(*serviceAccountKey)
	// if err != nil {
	// 	log.Fatal(err)
	// }

	ddClient, err := datadog.NewClient("fcm_app_server.")
	if err != nil {
		log.Fatal(err)
	}

	// Create a JWT Token
	// conf, err := google.JWTConfigFromJSON(
	// 	jsonKey,
	// 	"https://www.googleapis.com/auth/userinfo.email",
	// 	"https://www.googleapis.com/auth/firebase.database")
	// if err != nil {
	// 	log.Fatal(err)
	// }

	// Create a Firebase Database client
	//client := conf.Client(oauth2.NoContext)
	// client := conf.Client(context.Background())
	// fb = firego.New(firebaseDatabaseAddr, client)

	// Create MySQL client
	// FIXME: This error is never handled
	dbClient, err = sql.Open("mysql", mysqlDSN)

	ctx := context.Background()
	ctx, cancel := context.WithCancel(ctx)
	defer cancel()

	apiClient, err := api.NewClient(dbClient, ddClient, ddSampleRate, redisClient)
	if err != nil {
		log.Fatal(err)
	}

	mux := http.NewServeMux()
	// Static file serving for .apk files
	// TODO: host the Android app in the play store and rm
	mux.Handle("/apk/", http.StripPrefix("/apk/", http.FileServer(http.Dir(*dir))))
	mux.HandleFunc("/status", statusHandler)
	mux.HandleFunc("/fcm/attachment/:key", apiClient.AddCORS(apiClient.RequestMiddleware(apiClient.FCMAttachmentHandler)))
	mux.HandleFunc("/v1/fcm/send", apiClient.AddCORS(apiClient.RequestMiddleware(apiClient.PushySendHandler)))
	mux.HandleFunc("/v1/messages", apiClient.AddCORS(apiClient.RequestMiddleware(apiClient.MessagesHandler)))
	// TODO: Once more than one user is on board, this route/handler must be restored
	// mux.HandleFunc("/v1/token", apiClient.AddCORS(apiClient.RequestMiddleware(apiClient.TokenHandler)))
	mux.HandleFunc("/v1/metric", apiClient.AddCORS(apiClient.RequestMiddleware(apiClient.MetricHandler)))

	// Serve
	log.Printf("Serving files from directory %q", *dir)
	// Dev (non-TLS)
	if *addr != "" {
		log.Println("Listening on:", *addr)
		log.Fatal(http.ListenAndServe(*addr, mux))

		return
	}

	// Prod (only TLS)
	log.Println("Listening on:", *addrTLS)
	log.Fatal(http.ListenAndServeTLS(*addrTLS, *certFile, *keyFile, mux))
}
