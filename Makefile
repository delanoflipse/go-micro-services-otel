.PHONY: proto data run

proto:
	for f in internal/services/*/proto/*.proto; do \
		protoc --go_out=plugins=grpc:. $$f; \
		echo compiled: $$f; \
	done

data:
	go-bindata -o data/bindata.go -pkg data data/*.json

run:
	docker-compose build
	docker-compose up --remove-orphans

build-collector:
	cd ./services/collector; docker build -t fit-otel-collector:latest .

build-proxy:
	cd ./services/proxy; docker build -t fit-proxy:latest .

build-services:
	docker build -t go-micro-service:latest .

build-all: build-collector build-proxy build-services

fit-test:
	cd ./test/example-fit; mvn test
