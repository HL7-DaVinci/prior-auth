package org.hl7.davinci.priorauth;

import java.util.logging.Logger;

import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Controller;

@Controller
public class SocketEndpoint {

    static final Logger logger = PALogger.getLogger();

    @MessageMapping("/hello")
    @SendTo("/topic/greetings")
    public String greeting(String message) throws Exception {
        logger.info("SockerEndpoint::Controller received a message");
        Thread.sleep(1000); // simulated delay
        return "Hello " + message;
    }

}