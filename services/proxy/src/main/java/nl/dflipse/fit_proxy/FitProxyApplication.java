package nl.dflipse.fit_proxy;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.server.RouterFunction;
import org.springframework.web.reactive.function.server.RouterFunctions;
import org.springframework.web.reactive.function.server.ServerRequest;
import org.springframework.web.reactive.function.server.ServerResponse;
import reactor.core.publisher.Mono;
import reactor.netty.http.HttpProtocol;
import reactor.netty.http.client.HttpClient;
import org.springframework.web.reactive.function.server.RequestPredicates;

import nl.dflipse.fit_proxy.trace.TraceParent;

@SpringBootApplication
public class FitProxyApplication {
	private static String proxyHost = System.getenv("PROXY_HOST");
	private static String proxyTarget = System.getenv("PROXY_TARGET");
	private static boolean useHttp2 = System.getenv("USE_HTTP2").equals("true");

	private final WebClient webClient;

	public static void main(String[] args) {
		SpringApplication.run(FitProxyApplication.class, args);
	}

	public FitProxyApplication() {
		var protocol = useHttp2 ? HttpProtocol.H2C : HttpProtocol.HTTP11;

		this.webClient = WebClient.builder()
				.baseUrl(proxyTarget)
				.clientConnector(new ReactorClientHttpConnector(HttpClient.create().protocol(protocol)))
				.build();
	}

	@Bean
	public RouterFunction<ServerResponse> proxyRoutes() {
		return RouterFunctions.route(RequestPredicates.all(), this::handleRequest);
	}

	private Mono<ServerResponse> handleRequest(ServerRequest serverRequest) {
		// Extract request information
		HttpHeaders headers = serverRequest.headers().asHttpHeaders();
		String traceparent = headers.getFirst("traceparent");
		String tracestate = headers.getFirst("tracestate");

		TraceParent traceParent = TraceParent.fromString(traceparent);
		if (traceParent == null) {
			return forwardRequest(serverRequest);
		}

		HttpMethod method = serverRequest.method();
		String path = serverRequest.path();
		System.out.printf("Received traced request: %s %s\n", method, path);
		System.out.printf("Traceparent: %s\n", traceparent);
		System.out.printf("Tracestate: %s\n", tracestate);

		// Parse tracestate and handle inspection logic
		return forwardRequest(serverRequest);
	};

	private Mono<ServerResponse> forwardRequest(ServerRequest serverRequest) {

		HttpMethod method = serverRequest.method();
		String path = serverRequest.path();
		HttpHeaders headers = serverRequest.headers().asHttpHeaders();

		// Forward the request to the target server
		return webClient.method(method)
				.uri(path)
				.headers(httpHeaders -> httpHeaders.addAll(headers))
				.body(serverRequest.bodyToMono(String.class), String.class)
				.exchangeToMono(clientResponse -> ServerResponse.status(clientResponse.statusCode())
						.headers(responseHeaders -> responseHeaders.addAll(clientResponse.headers().asHttpHeaders()))
						.body(BodyInserters.fromPublisher(clientResponse.bodyToMono(String.class), String.class)));
	}

	// private Mono<ServerResponse> injectFault(String traceParent) {
	// System.out.printf("Injecting fault for traceparent: %s%n", traceParent);
	// return ServerResponse.status(500)
	// .contentType(MediaType.APPLICATION_JSON)
	// .bodyValue("{\"error\": \"Injected fault: HTTP error\"}");
	// }

	// private Mono<ServerResponse> forwardRequest(ServerRequest request, WebClient
	// webClient, String targetUrl) {
	// // Rebuild the request to forward
	// ServerHttpRequest originalRequest = request.exchange().getRequest();
	// return webClient.method(originalRequest.getMethod())
	// .uri(targetUrl + originalRequest.getPath())
	// .headers(headers -> headers.addAll(originalRequest.getHeaders()))
	// .body(request.bodyToMono(String.class), String.class)
	// .retrieve()
	// .toEntity(String.class)
	// .flatMap(response -> ServerResponse.status(response.getStatusCode())
	// .headers(httpHeaders -> httpHeaders.addAll(response.getHeaders()))
	// .bodyValue(response.getBody()));
	// }

}
