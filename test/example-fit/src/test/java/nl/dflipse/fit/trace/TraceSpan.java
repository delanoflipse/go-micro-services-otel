package nl.dflipse.fit.trace;

import com.fasterxml.jackson.annotation.JsonGetter;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;

@JsonSerialize
@JsonDeserialize
public class TraceSpan {
    @JsonProperty("name")
    public String name;

    @JsonProperty("service_name")
    public String serviceName;

    @JsonProperty("parent_span_id")
    public String parentSpanId;

    @JsonProperty("span_id")
    public String spanId;

    @JsonProperty("trace_id")
    public String traceId;

    @JsonProperty("start_time")
    public long startTime;

    @JsonProperty("end_time")
    public long endTime;

    @JsonProperty("trace_state")
    private TraceState traceState;

    @JsonSetter("trace_state")
    private void setTraceState(String data) {
        traceState = new TraceState(data);
    }

    @JsonGetter("trace_state")
    private String getTraceState() {
        return traceState.toString();
    }
}
