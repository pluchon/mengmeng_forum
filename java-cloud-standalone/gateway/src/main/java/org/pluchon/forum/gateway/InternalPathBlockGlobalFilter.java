package org.pluchon.forum.gateway;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

// 外部请求禁止直达各域 /internal/**（服务间调用走发现地址，不经网关）
@Component
public class InternalPathBlockGlobalFilter implements GlobalFilter, Ordered {

    private static final String INTERNAL_SEGMENT = "/internal/";

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath();
        if (path != null && path.contains(INTERNAL_SEGMENT)) {
            ServerHttpResponse response = exchange.getResponse();
            response.setStatusCode(HttpStatus.FORBIDDEN);
            response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
            byte[] body = "{\"code\":1003,\"message\":\"禁止访问\",\"data\":null}".getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(body);
            return response.writeWith(Mono.just(buffer));
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        // 紧随 TraceIdGlobalFilter，保证拒绝响应仍可带上链路头
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
