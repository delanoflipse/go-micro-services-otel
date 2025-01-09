import base64
from dataclasses import dataclass, field

# import trace
from flask import Flask, request
from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import ExportTraceServiceRequest
from google.protobuf.json_format import MessageToDict

app = Flask(__name__)


@dataclass
class Span:
    span_id: str
    trace_id: str
    parent_span_id: str
    name: str
    start_time: int
    end_time: int
    service_name: str


collected_spans: list[Span] = []


@dataclass
class TraceNode:
    span: Span
    is_root: bool = False
    children: list['TraceNode'] = field(default_factory=list)


def get_attribute(attributes, key):
    for attr in attributes:
        if attr['key'] == key:
            value = attr['value']
            if isinstance(value, dict) and 'stringValue' in value:
                return value['stringValue']
            return value
    return None


def to_id(base_id):
    if base_id is None:
        return None

    as_int = int.from_bytes(base64.b64decode(base_id), 'big')
    return hex(as_int)


def handleScopeSpan(span: dict, service_name: str):
    span_id = to_id(span.get('spanId', None))
    trace_id = to_id(span.get('traceId', None))
    parent_span_id = to_id(span.get('parentSpanId', None))

    name = span.get('name', None)
    start_time = span.get('startTimeUnixNano', None)
    end_time = span.get('endTimeUnixNano', None)

    span = Span(
        span_id=span_id,
        trace_id=trace_id,
        parent_span_id=parent_span_id,
        name=name,
        start_time=start_time,
        end_time=end_time,
        service_name=service_name
    )

    collected_spans.append(span)


def handleSpan(span):
    span_resource_attributes = span['resource']['attributes']
    service_name = get_attribute(span_resource_attributes, 'service.name')

    scope_spans = span['scopeSpans']
    for span in scope_spans:
        for scopedspan in span['spans']:
            handleScopeSpan(scopedspan, service_name)


def parse_protobuf(data):
    request_proto = ExportTraceServiceRequest()
    request_proto.ParseFromString(data)
    return MessageToDict(request_proto)


@app.route('/v1/traces', methods=['POST'])
def collect():
    raw_data = request.data
    data_dict = parse_protobuf(raw_data)

    for span in data_dict['resourceSpans']:
        handleSpan(span)

    return "Data collected", 200


def get_trace_tree(spans: list[Span]):
    tree_nodes = [TraceNode(span, is_root=span.parent_span_id is None)
                  for span in spans]
    span_lookup = {node.span.span_id: node for node in tree_nodes}

    # build tree
    for node in tree_nodes:
        if not node.is_root:
            parent = span_lookup.get(node.span.parent_span_id, None)
            if parent is None:
                print(f"Parent not found for span {node.span.span_id}")
                continue
            parent.children.append(node)

    return [
        node for node in tree_nodes if node.is_root
    ]


@app.route('/v1/all', methods=['GET'])
def get_spans():
    trees = get_trace_tree(collected_spans)
    return {
        "spans": collected_spans,
        "trees": trees
    }, 200


@app.route('/v1/get/<trace_id>', methods=['GET'])
def get_spans_by_trace_id(trace_id):
    filtered_spans = [
        span for span in collected_spans if span.trace_id == trace_id]

    trees = get_trace_tree(filtered_spans)
    return {
        "spans": filtered_spans,
        "trees": trees
    }, 200


if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000)
