package geo

import (
	"encoding/json"
	"fmt"
	"log"
	"net"

	"github.com/hailocab/go-geoindex"
	"github.com/harlow/go-micro-services/data"
	geo "github.com/harlow/go-micro-services/internal/services/geo/proto"
	"go.opentelemetry.io/contrib/instrumentation/google.golang.org/grpc/otelgrpc"
	sdktrace "go.opentelemetry.io/otel/sdk/trace"
	"golang.org/x/net/context"
	"google.golang.org/grpc"
)

const (
	maxSearchRadius  = 10
	maxSearchResults = 5
)

// point represents a hotels's geo location on map
type point struct {
	Pid  string  `json:"hotelId"`
	Plat float64 `json:"lat"`
	Plon float64 `json:"lon"`
}

// Implement Point interface
func (p *point) Lat() float64 { return p.Plat }
func (p *point) Lon() float64 { return p.Plon }
func (p *point) Id() string   { return p.Pid }

// New returns a new server
func New(tp *sdktrace.TracerProvider) *Geo {
	return &Geo{
		tracer: tp,
		geoidx: newGeoIndex("data/geo.json"),
	}
}

// Server implements the geo service
type Geo struct {
	geoidx *geoindex.ClusteringIndex
	tracer *sdktrace.TracerProvider
}

// Run starts the server
func (s *Geo) Run(port int) error {
	srv := grpc.NewServer(
		grpc.StatsHandler(otelgrpc.NewServerHandler()),
	)
	geo.RegisterGeoServer(srv, s)

	lis, err := net.Listen("tcp", fmt.Sprintf(":%d", port))
	if err != nil {
		return fmt.Errorf("failed to listen: %v", err)
	}

	return srv.Serve(lis)
}

// Nearby returns all hotels within a given distance.
func (s *Geo) Nearby(ctx context.Context, req *geo.Request) (*geo.Result, error) {
	var (
		points = s.getNearbyPoints(ctx, float64(req.Lat), float64(req.Lon))
		res    = &geo.Result{}
	)

	for _, p := range points {
		res.HotelIds = append(res.HotelIds, p.Id())
	}

	return res, nil
}

func (s *Geo) getNearbyPoints(ctx context.Context, lat, lon float64) []geoindex.Point {
	center := &geoindex.GeoPoint{
		Pid:  "",
		Plat: lat,
		Plon: lon,
	}

	return s.geoidx.KNearest(
		center,
		maxSearchResults,
		geoindex.Km(maxSearchRadius), func(p geoindex.Point) bool {
			return true
		},
	)
}

// newGeoIndex returns a geo index with points loaded
func newGeoIndex(path string) *geoindex.ClusteringIndex {
	var (
		file   = data.MustAsset(path)
		points []*point
	)

	// load geo points from json file
	if err := json.Unmarshal(file, &points); err != nil {
		log.Fatalf("Failed to load hotels: %v", err)
	}

	// add points to index
	index := geoindex.NewClusteringIndex()
	for _, point := range points {
		index.Add(point)
	}

	return index
}
