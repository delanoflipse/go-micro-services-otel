FROM golang:1.23.4
COPY . /go/src/github.com/harlow/go-micro-services
WORKDIR /go/src/github.com/harlow/go-micro-services
RUN go install -ldflags="-s -w" ./cmd/...
