package org.hl7.davinci.priorauth;

import java.util.logging.Logger;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

/**
 * Spring websocket configuration with STOMP.
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    public static final String ENDPOINT_CONNECT = "/connect";
    public static final String SUBSCRIBE_USER_PREFIX = "/private";
    public static final String SUBSCRIBE_USER_NOTIFICATION = "/notification";

    static final Logger logger = PALogger.getLogger();

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint(ENDPOINT_CONNECT, SubscribeController.ENDPOINT_SUBSCRIBE)
                // assign a random username as principal for each websocket client
                // this is needed to be able to communicate with a specific client
                .setHandshakeHandler(new AssignPrincipalHandshakeHandler()).setAllowedOrigins("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker(SUBSCRIBE_USER_NOTIFICATION);
        registry.setUserDestinationPrefix(SUBSCRIBE_USER_PREFIX);
    }

    @EventListener
    public void handleSubscribeEvent(SessionSubscribeEvent event) {
        logger.info("WebSocketConfig::handleSubscribeEvent:New subscription from " + event.getUser().getName());
        logger.fine("WebSocketConfig::handleSubscribeEvent:Event: " + event);
    }

    @EventListener
    public void handleConnectEvent(SessionConnectEvent event) {
        logger.info("WebSocketConfig::handleConnectEvent:New connection form " + event.getUser().getName());
        logger.fine("WebSocketConfig::handleConnectEvent:Event: " + event);
    }

    @EventListener
    public void handleDisconnectEvent(SessionDisconnectEvent event) {
        logger.info("WebSocketConfig::handleDisconnectEvent:Disconnect from " + event.getUser().getName());
        logger.fine("WebSocketConfig::handleDisconnectEvent:Event: " + event);
    }
}