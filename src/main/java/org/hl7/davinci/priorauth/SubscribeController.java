package org.hl7.davinci.priorauth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.logging.Logger;

@Controller
public class SubscribeController {
    public static final String ENDPOINT_SUBSCRIBE = "/subscribe";

    static final Logger logger = PALogger.getLogger();

    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    public SubscribeController(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    @MessageMapping(ENDPOINT_SUBSCRIBE)
    public void register(Message<Object> message, @Payload String payload, Principal principal) throws Exception {
        String username = principal.getName();
        logger.info("SubscribeController::Receive new subscription (" + username + ")");
        logger.fine("SubscribeController::Payload: " + payload);

        // Send to user /private/notification
        messagingTemplate.convertAndSendToUser(username, WebSocketConfig.SUBSCRIBE_USER_NOTIFICATION,
                "Thanks for your registration!");
        logger.info("SubscribeController::Response sent to " + username + " at " + WebSocketConfig.SUBSCRIBE_USER_PREFIX
                + WebSocketConfig.SUBSCRIBE_USER_NOTIFICATION);

        // Send to all subscribed to /queue
        messagingTemplate.convertAndSend(WebSocketConfig.SUBSCRIBE_QUEUE, "Someone just registered saying: " + payload);
        logger.info("SubscribeController::Response sent to " + WebSocketConfig.SUBSCRIBE_QUEUE);
    }

}