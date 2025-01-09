package main

import (
	"context"
	"flag"
	"fmt"
	"log"
	"os"

	frontendsrv "github.com/harlow/go-micro-services/internal/services/frontend"
	geosrv "github.com/harlow/go-micro-services/internal/services/geo"
	profilesrv "github.com/harlow/go-micro-services/internal/services/profile"
	ratesrv "github.com/harlow/go-micro-services/internal/services/rate"
	searchsrv "github.com/harlow/go-micro-services/internal/services/search"
	"github.com/harlow/go-micro-services/internal/trace"
	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	"google.golang.org/grpc"
)

type server interface {
	Run(int) error
}

func main() {
	var (
		port        = flag.Int("port", 8080, "The service port")
		oteladdr    = flag.String("otel", "otel-collector:4318", "OTel collector address")
		profileaddr = flag.String("profileaddr", "profile:8080", "Profile service addr")
		geoaddr     = flag.String("geoaddr", "geo:8080", "Geo server addr")
		rateaddr    = flag.String("rateaddr", "rate:8080", "Rate server addr")
		searchaddr  = flag.String("searchaddr", "search:8080", "Search service addr")
	)
	flag.Parse()

	var cmd = os.Args[1]

	tp, err := trace.New(cmd, *oteladdr)
	if err != nil {
		log.Fatalf("trace new error: %v", err)
	}

	// Handle shutdown properly so nothing leaks.
	defer func() {
		if err := tp.Shutdown(context.Background()); err != nil {
			log.Printf("Error shutting down tracer provider: %v", err)
		}
	}()

	var srv server

	log.Printf("cmd: %s", cmd)

	switch cmd {
	case "geo":
		srv = geosrv.New(tp)
	case "rate":
		srv = ratesrv.New(tp)
	case "profile":
		srv = profilesrv.New(tp)
	case "search":
		srv = searchsrv.New(
			tp,
			dial(*geoaddr),
			dial(*rateaddr),
		)
	case "frontend":
		srv = frontendsrv.New(
			tp,
			dial(*searchaddr),
			dial(*profileaddr),
		)
	default:
		log.Fatalf("unknown cmd: %s", cmd)
	}

	if err := srv.Run(*port); err != nil {
		log.Fatalf("run %s error: %v", cmd, err)
	}
}

func dial(addr string) *grpc.ClientConn {
	conn, err := grpc.NewClient(addr,
		grpc.WithStatsHandler(otelgrpc.NewClientHandler()),
		grpc.WithInsecure(),
	)

	if err != nil {
		panic(fmt.Sprintf("ERROR: dial error: %v", err))
	}

	return conn
}
