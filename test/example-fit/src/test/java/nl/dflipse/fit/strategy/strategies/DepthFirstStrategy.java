package nl.dflipse.fit.strategy.strategies;

import java.util.LinkedList;
import java.util.List;
import java.util.Queue;

import nl.dflipse.fit.collector.TraceData;
import nl.dflipse.fit.collector.TraceTreeSpan;
import nl.dflipse.fit.strategy.FIStrategy;
import nl.dflipse.fit.strategy.Faultload;

public class DepthFirstStrategy implements FIStrategy {
    private boolean failed = false;
    private final Queue<Faultload> queue = new LinkedList<>();

    public DepthFirstStrategy() {
        // start out with an empty queue
        queue.add(new Faultload());
    }

    @Override
    public Faultload next() {
        if (failed) {
            return null;
        }
        return queue.poll();
    }

    @Override
    public void handleResult(Faultload faultload, TraceData trace, boolean passed) {
        if (!passed) {
            failed = true;
            return;
        }

        boolean wasFirst = faultload.size() == 0;

        if (wasFirst) {
            var potentialFaults = getDepthFirstFaults(trace);
            for (String fault : potentialFaults) {
                var newFaultload = new Faultload(List.of(fault));
                queue.add(newFaultload);
            }
            return;
        }
    }

    private List<String> getDepthFirstFaults(TraceData trace) {
        if (trace == null || trace.trees.isEmpty()) {
            return new LinkedList<>();
        }

        TraceTreeSpan root = trace.trees.get(0);
        return getFaults(root, null);
    }

    private List<String> getFaults(TraceTreeSpan spanNode, TraceTreeSpan parent) {
        List<String> faults = new LinkedList<>();

        if (spanNode == null) {
            return faults;
        }

        if (spanNode.children != null) {
            for (TraceTreeSpan child : spanNode.children) {
                faults.addAll(getFaults(child, spanNode));
            }
        }

        boolean isRoot = parent == null;
        boolean isFaultPoint = !isRoot && !parent.span.serviceName.equals(spanNode.span.serviceName);

        if (isFaultPoint) {
            faults.add(parent.span.spanUid);
        }

        return faults;
    }
}
