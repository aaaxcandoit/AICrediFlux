package aicrediflux.token.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.support.DefaultHandshakeHandler;
import aicrediflux.token.middleware.RealtimeHandshakeInterceptor;
import aicrediflux.token.relay.handler.WebSocketHandler;

/**
 * Realtime WebSocket 服务端入口配置。
 */
@Configuration
@EnableWebSocket
@RequiredArgsConstructor
public class RealtimeWebSocketConfig implements WebSocketConfigurer {

    private final WebSocketHandler webSocketHandler;
    private final RealtimeHandshakeInterceptor realtimeHandshakeInterceptor;

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        DefaultHandshakeHandler handshakeHandler = new DefaultHandshakeHandler();
        handshakeHandler.setSupportedProtocols("realtime");

        registry.addHandler(webSocketHandler, "/v1/realtime")
                .addInterceptors(realtimeHandshakeInterceptor)
                .setHandshakeHandler(handshakeHandler)
                .setAllowedOriginPatterns("*");
    }
}
