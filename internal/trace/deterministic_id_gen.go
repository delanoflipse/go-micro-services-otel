package trace

import (
	"context"
	"crypto/sha256"
	"math/rand"

	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"go.opentelemetry.io/otel/trace"
)

type deterministicIDGenerator struct {
	randSource *rand.Rand
}

func (g deterministicIDGenerator) NewIDs(ctx context.Context) (trace.TraceID, trace.SpanID) {
	traceID := g.newTraceId()
	spanID := g.NewSpanID(ctx, traceID)
	return traceID, spanID
}

func (g deterministicIDGenerator) NewSpanID(ctx context.Context, traceID trace.TraceID) trace.SpanID {
	spanID := sha256.Sum256([]byte("deterministic-span-id"))

	return trace.SpanID(spanID[:8])
}

func (g deterministicIDGenerator) newTraceId() trace.TraceID {
	tid := trace.TraceID{}

	for {
		_, _ = g.randSource.Read(tid[:])
		if tid.IsValid() {
			break
		}
	}

	return tid
}

func NewDeterministicIDGenerator() sdktrace.IDGenerator {
	return deterministicIDGenerator{}
}
