.PHONY: proto data run stop build

proto:
	for f in internal/services/*/proto/*.proto; do \
		protoc --go_out=plugins=grpc:. $$f; \
		echo compiled: $$f; \
	done

data:
	go-bindata -o data/bindata.go -pkg data data/*.json

run:
	docker compose build
	docker compose up --remove-orphans

stop:
	docker compose down

build:
	docker build -t go-micro-service:latest .

