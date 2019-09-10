package org.hl7.davinci.priorauth;

import com.google.gson.Gson;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

@Component
public class SocketHandler extends TextWebSocketHandler {

    static final Logger logger = PALogger.getLogger();

    List<WebSocketSession> sessions = new CopyOnWriteArrayList<>();

    @Override
    public void handleTextMessage(WebSocketSession session, TextMessage message)
            throws InterruptedException, IOException {
        Map<String, String> value = new Gson().fromJson(message.getPayload(), Map.class);
        session.sendMessage(new TextMessage("Hello " + value.get("name") + " !"));
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        InetSocketAddress clientAddress = session.getRemoteAddress();

        // the messages will be broadcasted to all users.
        logger.info("Accepted connection from: " + clientAddress.getHostString() + ":" + clientAddress.getPort());
        // logger.info("Client hostname: {}", clientAddress.getHostName());
        // logger.info("Client ip: {}", clientAddress.getAddress().getHostAddress());
        // logger.info("Client port: {}", clientAddress.getPort());
        //
        // logger.info("Session accepted protocols: {}", session.getAcceptedProtocol());
        // logger.info("Session binary message size limit: {}",
        // session.getBinaryMessageSizeLimit());
        // logger.info("Session id: {}", session.getId());
        // logger.info("Session text message size limit: {}",
        // session.getTextMessageSizeLimit());
        // logger.info("Session uri: {}", session.getUri().toString());
        //
        // logger.info("Handshake header: Accept {}", handshakeHeaders.toString());
        // logger.info("Handshake header: User-Agent {}",
        // handshakeHeaders.get("User-Agent").toString());
        // logger.info("Handshake header: Sec-WebSocket-Extensions {}",
        // handshakeHeaders.get("Sec-WebSocket-Extensions").toString());
        // logger.info("Handshake header: Sec-WebSocket-Key {}",
        // handshakeHeaders.get("Sec-WebSocket-Key").toString());
        // logger.info("Handshake header: Sec-WebSocket-Version {}",
        // handshakeHeaders.get("Sec-WebSocket-Version").toString());

        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        logger.info("Connection closed by " + session.getRemoteAddress().getHostString() + ":"
                + session.getRemoteAddress().getPort());
        super.afterConnectionClosed(session, status);
    }
}