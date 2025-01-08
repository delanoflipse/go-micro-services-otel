package profile

import (
	"encoding/json"
	"fmt"
	"log"
	"net"

	"github.com/harlow/go-micro-services/data"
	profile "github.com/harlow/go-micro-services/internal/services/profile/proto"
	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"golang.org/x/net/context"
	"google.golang.org/grpc"
)

// New returns a new server
func New(tp *sdktrace.TracerProvider) *Profile {
	return &Profile{
		tracer:   tp,
		profiles: loadProfiles("data/hotels.json"),
	}
}

// Profile implements the profile service
type Profile struct {
	profiles map[string]*profile.Hotel
	tracer   *sdktrace.TracerProvider
}

// Run starts the server
func (s *Profile) Run(port int) error {
	srv := grpc.NewServer(
		grpc.StatsHandler(otelgrpc.NewServerHandler()),
	)
	profile.RegisterProfileServer(srv, s)

	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		log.Fatalf("failed to listen: %v", err)
	}

	return srv.Serve(lis)
}

// GetProfiles returns hotel profiles for requested IDs
func (s *Profile) GetProfiles(ctx context.Context, req *profile.Request) (*profile.Result, error) {
	res := new(profile.Result)
	for _, id := range req.HotelIds {
		res.Hotels = append(res.Hotels, s.getProfile(id))
	}
	return res, nil
}

func (s *Profile) getProfile(id string) *profile.Hotel {
	return s.profiles[id]
}

// loadProfiles loads hotel profiles from a JSON file.
func loadProfiles(path string) map[string]*profile.Hotel {
	var (
		file   = data.MustAsset(path)
		hotels []*profile.Hotel
	)

	if err := json.Unmarshal(file, &hotels); err != nil {
		log.Fatalf("Failed to load json: %v", err)
	}

	profiles := make(map[string]*profile.Hotel)
	for _, hotel := range hotels {
		profiles[hotel.Id] = hotel
	}
	return profiles
}
