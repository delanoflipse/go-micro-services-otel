package trace

import (
	"context"
	"fmt"
	"log"
	"os"

	"go.opentelemetry.io/otel"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace"
	"go.opentelemetry.io/otel/exporters/otlp/otlptrace/otlptracehttp"
	"go.opentelemetry.io/otel/propagation"
	"go.opentelemetry.io/otel/sdk/resource"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	semconv "go.opentelemetry.io/otel/semconv/v1.26.0"
)

// New creates a new tracer
func New(serviceName, host string) (*sdktrace.TracerProvider, error) {
	ctx := context.Background()

	exporter, err := otlptrace.New(ctx, otlptracehttp.NewClient(
		otlptracehttp.WithEndpoint(host),
		otlptracehttp.WithInsecure(), // Use this if your endpoint doesn't use TLS
	))

	if err != nil {
		return nil, fmt.Errorf("failed to create OpenTelemetry exporter: %v", err)
	}

	resource, err := resource.Merge(
		resource.Default(),
		resource.NewWithAttributes(
			semconv.SchemaURL,
			semconv.ServiceName(serviceName),
		),
	)

	if err != nil {
		panic(err)
	}

	useExporter := os.Getenv("NO_INSTRUMENTATION") == ""

	var provider *sdktrace.TracerProvider
	if useExporter {
		log.Printf("Using exporter")
		provider = sdktrace.NewTracerProvider(
			sdktrace.WithSampler(sdktrace.AlwaysSample()),
			sdktrace.WithSpanProcessor(NewDirectSpanProcessor(exporter)),
			sdktrace.WithResource(resource),
		)
	} else {
		provider = sdktrace.NewTracerProvider(
			sdktrace.WithSampler(sdktrace.AlwaysSample()),
			sdktrace.WithResource(resource),
			sdktrace.WithBatcher(exporter),
		)
	}

	otel.SetTracerProvider(provider)
	otel.SetTextMapPropagator(propagation.NewCompositeTextMapPropagator(propagation.TraceContext{}, propagation.Baggage{}))

	return provider, nil
}
