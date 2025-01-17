package nl.dflipse.fit_proxy.trace;

public class TraceParent {
    public String version;
    public String traceId;
    public String parentSpanId;
    public String traceFlags;

    public TraceParent(String version, String traceId, String parentSpanId, String traceFlags) {
        this.version = version;
        this.traceId = traceId;
        this.parentSpanId = parentSpanId;
        this.traceFlags = traceFlags;
    }

    public static TraceParent fromString(String header) {
        String[] parts = header.split("-");
        if (parts.length != 4) {
            return null;
        }

        return new TraceParent(parts[0], parts[1], parts[2], parts[3]);
    }

    public TraceParent() {
        this.version = "00";
        this.traceId = genTraceId();
        this.parentSpanId = initialSpanId();
        this.traceFlags = "01";
    }

    private static String genId(int numberOfBytes) {
        byte[] bytes = new byte[numberOfBytes];
        new java.security.SecureRandom().nextBytes(bytes);
        StringBuilder hexString = new StringBuilder(2 * bytes.length);
        for (byte b : bytes) {
            hexString.append(String.format("%02x", b));
        }
        return hexString.toString();
    }

    public static String genTraceId() {
        return genId(16);
    }

    public static String genSpanId() {
        return genId(8);
    }

    public static String initialSpanId() {
        return "0000000000000001";
    }

    public String toString() {
        return this.version + "-" + this.traceId + "-" + this.parentSpanId + "-" + this.traceFlags;
    }
}
