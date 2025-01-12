import asyncio
import base64
from dataclasses import dataclass, field

# import trace
from flask import Flask, request
from opentelemetry.proto.collector.trace.v1.trace_service_pb2 import ExportTraceServiceRequest
from google.protobuf.json_format import MessageToDict
import time

app = Flask(__name__)


@dataclass
class Span:
    span_uid: str
    span_id: str
    trace_id: str
    parent_span_id: str
    name: str
    start_time: int
    end_time: int
    service_name: str
    trace_state: str


collected_spans: list[Span] = []
collected_raw: list = []


@dataclass
class TraceNode:
    span: Span
    children: list['TraceNode'] = field(default_factory=list)


def find_span_by_id(span_id):
    for span in collected_spans:
        if span.span_id == span_id:
            return span
    return None


def get_attribute(attributes, key):
    for attr in attributes:
        if attr['key'] == key:
            value = attr['value']
            if isinstance(value, dict) and 'stringValue' in value:
                return value['stringValue']
            return value
    return None


def to_id(base_id, byte_length=16):
    if base_id is None:
        return None

    as_int = int.from_bytes(base64.b64decode(base_id), 'big')
    return hex(as_int)[2:].zfill(byte_length * 2)


def to_int(value):
    if value is None:
        return None
    return int(value)


span_counter = {}


def handleScopeSpan(span: dict, service_name: str):
    span_id = to_id(span.get('spanId', None), 8)
    trace_id = to_id(span.get('traceId', None), 16)
    parent_span_id = to_id(span.get('parentSpanId', None), 8)

    trace_state = span.get('traceState', None)

    name = span.get('name', None)
    start_time = to_int(span.get('startTimeUnixNano', None))
    end_time = to_int(span.get('endTimeUnixNano', None))

    # update existing span if it exists
    existing_span_index = next((i for i, s in enumerate(
        collected_spans) if s.span_id == span_id), None)

    if existing_span_index is not None:
        # update existing span and return
        collected_spans[existing_span_index].end_time = end_time
        return

    # define unique and deterministic span id
    span_base_id = f"{service_name}>{name}"
    trace_lookup = f"{trace_id}-{span_base_id}"
    span_count = span_counter.get(trace_lookup, 0)
    span_counter[trace_lookup] = span_count + 1
    span_uid = f"{span_base_id}|{span_count}"

    # create NEW span
    span = Span(
        span_id=span_id,
        span_uid=span_uid,
        trace_id=trace_id,
        parent_span_id=parent_span_id,
        name=name,
        start_time=start_time,
        end_time=end_time,
        service_name=service_name,
        trace_state=trace_state
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
    collected_raw.append(data_dict)

    for span in data_dict['resourceSpans']:
        handleSpan(span)

    return "Data collected", 200


def get_trace_tree(spans: list[Span]):
    tree_nodes = [TraceNode(span) for span in spans]
    span_lookup = {node.span.span_id: node for node in tree_nodes}

    # build tree
    for node in tree_nodes:
        parent = span_lookup.get(node.span.parent_span_id, None)
        if parent is None:
            continue
        parent.children.append(node)

    return [
        node for node in tree_nodes if span_lookup.get(node.span.parent_span_id, None) is None
    ]


@app.route('/v1/all', methods=['GET'])
def get_spans():
    trees = get_trace_tree(collected_spans)
    return {
        "spans": collected_spans,
        "trees": trees
    }, 200


@app.route('/v1/raw', methods=['GET'])
def get_raw_spans():
    return {
        "data": collected_raw,
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


@app.route('/v1/spanid/<trace_data>', methods=['GET'])
async def get_span_uid(trace_data):
    v, trace_id, parent_span_id, flags = trace_data.split('-')
    if parent_span_id == "0000000000000001":
        return "<root>", 200

    parent_span: Span = None
    delay_per_attempt_s = 0.05
    max_delay = 1.0
    max_attempts = max_delay / delay_per_attempt_s
    for _ in range(int(max_attempts)):
        parent_span = find_span_by_id(parent_span_id)
        if parent_span is not None:
            break
        else:
            await asyncio.sleep(delay_per_attempt_s)
    if parent_span is None:
        return "<none>", 404

    return parent_span.span_uid, 200


if __name__ == '__main__':
    loop = asyncio.get_event_loop()
    loop.run_until_complete(app.run(host='0.0.0.0', port=5000))
