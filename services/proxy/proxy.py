from mitmproxy import http

# This function modifies the request before it is sent to the origin server


def request(flow: http.HTTPFlow) -> None:
    # Log some metadata about the request
    print(f"Request URL: {flow.request.url}")
    print(f"Request Method: {flow.request.method}")
    print(f"Request Headers: {flow.request.headers}")
    # print(f"Request Content: {flow.request.content}")

    # Example: Inject fault based on a header
    if "X-Fault-Inject" in flow.request.headers:
        fault_type = flow.request.headers["X-Fault-Inject"]
        if fault_type == "timeout":
            # Simulate a network timeout by not responding at all
            flow.response = http.Response.make(
                504,  # HTTP status code for Gateway Timeout
                b"Request Timed Out",
                {"Content-Type": "text/plain"}
            )
            print(f"Injected timeout fault due to header: {fault_type}")

        elif fault_type == "error":
            # Simulate a server error
            flow.response = http.Response.make(
                500,  # HTTP status code for Internal Server Error
                b"Internal Server Error",
                {"Content-Type": "text/plain"}
            )
            print(f"Injected error fault due to header: {fault_type}")

        # Add more fault injection logic as needed
    else:
        # Forward the request without modification
        pass

# This function modifies the response after it is received from the origin server


def response(flow: http.HTTPFlow) -> None:
    # You can inject faults in the response as well, e.g., delay, modification
    pass
