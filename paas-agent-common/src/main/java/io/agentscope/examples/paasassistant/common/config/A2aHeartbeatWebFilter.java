package io.agentscope.examples.paasassistant.common.config;

import io.a2a.spec.JSONRPCResponse;
import io.a2a.spec.TransportProtocol;
import io.a2a.util.Utils;
import io.agentscope.core.a2a.server.AgentScopeA2aServer;
import io.agentscope.core.a2a.server.transport.jsonrpc.JsonRpcTransportWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class A2aHeartbeatWebFilter implements WebFilter {

    private static final Logger log = LoggerFactory.getLogger(A2aHeartbeatWebFilter.class);

    private final ObjectProvider<AgentScopeA2aServer> agentScopeA2aServerProvider;
    private JsonRpcTransportWrapper jsonRpcHandler;

    public A2aHeartbeatWebFilter(ObjectProvider<AgentScopeA2aServer> agentScopeA2aServerProvider) {
        this.agentScopeA2aServerProvider = agentScopeA2aServerProvider;
    }

    private JsonRpcTransportWrapper getJsonRpcHandler() {
        if (jsonRpcHandler == null) {
            AgentScopeA2aServer agentScopeA2aServer = agentScopeA2aServerProvider.getIfAvailable();
            if (agentScopeA2aServer != null) {
                jsonRpcHandler = agentScopeA2aServer.getTransportWrapper(
                        TransportProtocol.JSONRPC.asString(), JsonRpcTransportWrapper.class);
            }
        }
        return jsonRpcHandler;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        if (HttpMethod.POST.equals(exchange.getRequest().getMethod())
                && "/".equals(exchange.getRequest().getPath().value())) {

            AgentScopeA2aServer server = agentScopeA2aServerProvider.getIfAvailable();
            if (server == null) {
                return chain.filter(exchange);
            }

            log.info("A2aHeartbeatWebFilter intercepting JSON-RPC POST request to /");
            return DataBufferUtils.join(exchange.getRequest().getBody())
                    .map(dataBuffer -> {
                        byte[] bytes = new byte[dataBuffer.readableByteCount()];
                        dataBuffer.read(bytes);
                        DataBufferUtils.release(dataBuffer);
                        return new String(bytes, StandardCharsets.UTF_8);
                    })
                    .defaultIfEmpty("")
                    .flatMap(body -> {
                        if (body.isEmpty()) {
                            return chain.filter(exchange);
                        }

                        Map<String, String> headers = exchange.getRequest().getHeaders().toSingleValueMap();
                        JsonRpcTransportWrapper handler = getJsonRpcHandler();
                        if (handler == null) {
                            return chain.filter(exchange);
                        }

                        Object result = handler.handleRequest(body, headers, Map.of());

                        if (result instanceof Flux<?> fluxResult) {
                            log.info("A2A request is a stream, writing ServerSentEvents with heartbeats");
                            ServerHttpResponse response = exchange.getResponse();
                            response.getHeaders().setContentType(MediaType.TEXT_EVENT_STREAM);

                            Flux<ServerSentEvent<String>> actualFlux = fluxResult
                                    .filter(each -> each instanceof JSONRPCResponse)
                                    .map(each -> (JSONRPCResponse<?>) each)
                                    .map(this::convertToSse);

                            // Generate heartbeats every 15 seconds
                            Flux<ServerSentEvent<String>> heartbeats = Flux.interval(Duration.ofSeconds(15))
                                    .map(tick -> ServerSentEvent.<String>builder().comment("keep-alive").build());

                            // Merge heartbeats into actual stream, terminated when actual stream completes
                            // Use publish(Function) to share subscription to actualFlux
                            Flux<ServerSentEvent<String>> mergedFlux = actualFlux.publish(sharedFlux ->
                                    Flux.merge(
                                            sharedFlux,
                                            heartbeats.takeUntilOther(sharedFlux.ignoreElements())
                                    )
                            );

                            Flux<DataBuffer> bodyBuffers = mergedFlux.map(sse -> {
                                StringBuilder sb = new StringBuilder();
                                if (sse.comment() != null) {
                                    sb.append(":").append(sse.comment()).append("\n\n");
                                } else {
                                    if (sse.id() != null) {
                                        sb.append("id:").append(sse.id()).append("\n");
                                    }
                                    if (sse.event() != null) {
                                        sb.append("event:").append(sse.event()).append("\n");
                                    }
                                    if (sse.data() != null) {
                                        sb.append("data:").append(sse.data()).append("\n");
                                    }
                                    sb.append("\n");
                                }
                                byte[] sseBytes = sb.toString().getBytes(StandardCharsets.UTF_8);
                                return response.bufferFactory().wrap(sseBytes);
                            });

                            return response.writeWith(bodyBuffers);
                        } else if (result instanceof JSONRPCResponse<?> jsonResponse) {
                            log.info("A2A request is a single message response, writing JSON");
                            ServerHttpResponse response = exchange.getResponse();
                            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
                            try {
                                String json = Utils.OBJECT_MAPPER.writeValueAsString(jsonResponse);
                                byte[] jsonBytes = json.getBytes(StandardCharsets.UTF_8);
                                return response.writeWith(Mono.just(response.bufferFactory().wrap(jsonBytes)));
                            } catch (Exception e) {
                                log.error("Failed to write single JSON-RPC response", e);
                                response.setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
                                return response.setComplete();
                            }
                        } else {
                            log.warn("Unrecognized JSON-RPC response type: {}", result.getClass().getName());
                            return chain.filter(exchange);
                        }
                    });
        }
        return chain.filter(exchange);
    }

    private ServerSentEvent<String> convertToSse(JSONRPCResponse<?> response) {
        try {
            String data = Utils.OBJECT_MAPPER.writeValueAsString(response);
            ServerSentEvent.Builder<String> builder =
                    ServerSentEvent.<String>builder().data(data).event("jsonrpc");
            if (response.getId() != null) {
                builder.id(response.getId().toString());
            }
            return builder.build();
        } catch (Exception e) {
            log.error("Error converting response to SSE: {}", e.getMessage());
            return ServerSentEvent.<String>builder()
                    .data("{\"error\":\"Internal conversion error\"}")
                    .event("error")
                    .build();
        }
    }
}
