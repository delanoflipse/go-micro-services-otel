// Copyright (c) 2017 Uber Technologies, Inc.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package trace

import (
	"net/http"

	"go.opentelemetry.io/contrib/instrumentation/net/http/otelhttp"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
)

// NewServeMux creates a new TracedServeMux.
func NewServeMux(tracerProvider *sdktrace.TracerProvider) *TracedServeMux {
	return &TracedServeMux{
		mux:            http.NewServeMux(),
		tracerProvider: tracerProvider,
	}
}

// TracedServeMux is a wrapper around http.ServeMux that instruments handlers for tracing.
type TracedServeMux struct {
	mux            *http.ServeMux
	tracerProvider *sdktrace.TracerProvider
}

// Handle implements http.ServeMux#Handle
func (tm *TracedServeMux) Handle(pattern string, handler http.Handler) {
	// Configure the "http.route" for the HTTP instrumentation.
	tracedHandler := otelhttp.NewHandler(handler, pattern, otelhttp.WithTracerProvider(tm.tracerProvider))
	tm.mux.Handle(pattern, tracedHandler)
}

// ServeHTTP implements http.ServeMux#ServeHTTP
func (tm *TracedServeMux) ServeHTTP(w http.ResponseWriter, r *http.Request) {
	tm.mux.ServeHTTP(w, r)
}
