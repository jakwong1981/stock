package com.cyberstock.config;

import com.cyberstock.market.QuoteWebSocketHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final QuoteWebSocketHandler handler;
    private final AppProperties props;

    public WebSocketConfig(QuoteWebSocketHandler handler, AppProperties props) {
        this.handler = handler;
        this.props = props;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/quotes")
                .setAllowedOrigins(props.cors().originList().toArray(String[]::new));
    }
}
