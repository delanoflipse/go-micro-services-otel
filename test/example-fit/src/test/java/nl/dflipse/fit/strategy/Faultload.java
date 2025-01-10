package nl.dflipse.fit.strategy;

import java.util.ArrayList;
import java.util.List;

import nl.dflipse.fit.trace.TraceParent;
import nl.dflipse.fit.trace.TraceState;

public class Faultload {
    private final List<String> faultload;

    private TraceParent traceParent = new TraceParent();
    private TraceState traceState = new TraceState();

    public Faultload(List<String> faultload) {
        this.faultload = faultload;
        initializeTraceState();
    }

    public Faultload() {
        this.faultload = new ArrayList<>();
        initializeTraceState();
    }

    private void initializeTraceState() {
        traceState.set("faultload", "");
        traceState.set("spanuid", 0);
    }

    public String getTraceId() {
        return traceParent.traceId;
    }

    public TraceParent getTraceParent() {
        return traceParent;
    }

    public TraceState getTraceState() {
        return traceState;
    }

    public List<String> getFaultload() {
        return faultload;
    }

    public int size() {
        return faultload.size();
    }
}
