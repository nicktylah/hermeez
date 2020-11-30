GIT_TAG := $(shell git rev-parse --short HEAD 2> /dev/null)
BUILD_TAG := $(if $(BUILD_TAG),$(BUILD_TAG),nicktylah/fcm-app-server:$(GIT_TAG))
STG_SERVER_PUBLIC_IP := "redacted"

build: clean
	go fmt ./...
	go build

build-linux: clean
	env GOOS=linux GOARCH=amd64 go build

docker-build: build
	docker build -t $(BUILD_TAG) .

clean:
	rm -f fcm-app-server

upload: build-linux
	ssh -i ~/.ssh/fcm-app-server.pem ubuntu@$(STG_SERVER_PUBLIC_IP) sudo service fcm-app-server stop
	scp -i ~/.ssh/fcm-app-server.pem fcm-app-server ubuntu@$(STG_SERVER_PUBLIC_IP):/home/ubuntu/fcm-app-server
	ssh -i ~/.ssh/fcm-app-server.pem ubuntu@$(STG_SERVER_PUBLIC_IP) sudo service fcm-app-server start

upload-release-apk:
	scp -i ~/.ssh/fcm-app-server.pem /Users/nick/AndroidStudioProjects/AndroidMessages/app/app-release.apk ubuntu@$(STG_SERVER_PUBLIC_IP):/home/ubuntu/fcm-app-server/apk

upload-debug-apk:
	scp -i ~/.ssh/fcm-app-server.pem /Users/nick/AndroidStudioProjects/TextTop/app/build/outputs/apk/app-debug.apk ubuntu@$(STG_SERVER_PUBLIC_IP):/home/ubuntu/fcm-app-server/apk
