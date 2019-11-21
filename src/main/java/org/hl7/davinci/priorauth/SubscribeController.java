package org.hl7.davinci.priorauth;

import org.hl7.davinci.priorauth.Database.Table;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;
import java.util.Collections;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Controller
public class SubscribeController {
    public static final String ENDPOINT_SUBSCRIBE = "/subscribe";

    static final Logger logger = PALogger.getLogger();

    private static SimpMessagingTemplate messagingTemplate;

    @Autowired
    public SubscribeController(SimpMessagingTemplate mt) {
        messagingTemplate = mt;
    }

    @MessageMapping(ENDPOINT_SUBSCRIBE)
    public void register(Message<Object> message, @Payload String payload, Principal principal) throws Exception {
        String username = principal.getName();
        logger.info("SubscribeController::Receive new subscription (" + username + ")");
        logger.fine("SubscribeController::Payload: " + payload);

        // Get the id
        String regex = "bind: (.*)";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(payload);

        if (matcher.find() && matcher.groupCount() == 1) {
            // Bind the id to the subscription in db
            String subscriptionId = matcher.group(1);
            if (App.getDB().update(Table.SUBSCRIPTION, Collections.singletonMap("id", subscriptionId),
                    Collections.singletonMap("websocketId", username)))
                sendMessageToUser(username, WebSocketConfig.SUBSCRIBE_USER_NOTIFICATION, "bound: " + subscriptionId);
            else
                sendMessageToUser(username, WebSocketConfig.SUBSCRIBE_USER_NOTIFICATION,
                        "Unable to bind " + subscriptionId + " because it does not exist");

        } else {
            logger.info("SubscribeController::Bind message does not match regex " + regex);
            sendMessageToUser(username, WebSocketConfig.SUBSCRIBE_USER_NOTIFICATION,
                    "Unable to bind id. Request was not in the form \"" + regex + "\"");
        }
    }

    public static void sendMessageToUser(String username, String channel, String msg) {
        messagingTemplate.convertAndSendToUser(username, channel, msg);
        logger.info("SubscribeController::Message sent to " + username + " on " + channel);
    }

}