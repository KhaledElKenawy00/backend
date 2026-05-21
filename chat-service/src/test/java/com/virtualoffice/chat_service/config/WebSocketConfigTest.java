package com.virtualoffice.chat_service.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.StompWebSocketEndpointRegistration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WebSocketConfigTest {

    @Mock
    private WebSocketHandshakeInterceptor handshakeInterceptor;

    @Mock
    private WebSocketSubscriptionInterceptor subscriptionInterceptor;

    @InjectMocks
    private WebSocketConfig config;

    @Test
    void shouldRegisterBothTopicAndQueueBrokers() {
        // /queue is required for /user/queue/errors to work — A1 regression guard
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        ArgumentCaptor<String[]> captor = ArgumentCaptor.forClass(String[].class);
        verify(registry).enableSimpleBroker(captor.capture());
        String[] prefixes = captor.getValue();
        assertThat(prefixes).contains("/topic", "/queue");
    }

    @Test
    void shouldSetApplicationDestinationPrefix() {
        MessageBrokerRegistry registry = mock(MessageBrokerRegistry.class);

        config.configureMessageBroker(registry);

        verify(registry).setApplicationDestinationPrefixes("/app");
    }

    @Test
    void shouldRegisterConnectEndpointWithHandshakeInterceptor() {
        StompEndpointRegistry registry = mock(StompEndpointRegistry.class);
        StompWebSocketEndpointRegistration endpoint = mock(StompWebSocketEndpointRegistration.class);
        when(registry.addEndpoint("/api/chat/connect")).thenReturn(endpoint);
        when(endpoint.addInterceptors(handshakeInterceptor)).thenReturn(endpoint);
        when(endpoint.setAllowedOriginPatterns(any())).thenReturn(endpoint);

        config.registerStompEndpoints(registry);

        verify(registry).addEndpoint("/api/chat/connect");
        verify(endpoint).addInterceptors(handshakeInterceptor);
        verify(endpoint).setAllowedOriginPatterns("*");
    }

    @Test
    void shouldWireSubscriptionInterceptor() {
        ChannelRegistration registration = mock(ChannelRegistration.class);

        config.configureClientInboundChannel(registration);

        verify(registration).interceptors(subscriptionInterceptor);
    }
}
