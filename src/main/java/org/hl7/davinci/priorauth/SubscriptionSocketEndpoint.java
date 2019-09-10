package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.websocket.EncodeException;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

@ServerEndpoint(value = "/SocketSubscription/{priorauthid}")
public class SubscriptionSocketEndpoint {

    static final Logger logger = PALogger.getLogger();

    private static HashMap<String, Session> sessions = new HashMap<String, Session>();

    @OnOpen
    public void onOpen(Session session, @PathParam("priorauthid") String priorauthId) {
        logger.info("SubscriptionSocketEndpoint::Session " + session.getId() + ": Bound PriorAuthId " + priorauthId);

        try {
            session.getBasicRemote().sendObject("Bound " + priorauthId);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "SubscriptionSocketEndpoint::IOException sending bound message", e);
        } catch (EncodeException e) {
            logger.log(Level.SEVERE, "SubscriptionSocketEndpoint::EncodeException sending bound message", e);
        }
    }

    @OnClose
    public void onClose(Session session) {
        for (Entry<String, Session> entry : sessions.entrySet()) {
            if (entry.getValue().getId().equals(session.getId())) {
                sessions.remove(entry.getKey());
                logger.info("SubscriptionSocketEndpoint::Closed socket for PriorAuthId " + entry.getKey());
                return;
            }
        }

        logger.warning("SubscriptionSocketEndpoint::Unable to successfully close session " + session.getId());
    }

    @OnError
    public void OnError(Session session) {
        logger.severe("SubscriptionSocketEndpoint::Unkown error for session " + session.getId());
    }

    public static void sendPing(String priorauthId) throws IllegalArgumentException, IOException, NullPointerException {
        Session session = sessions.get(priorauthId);
        session.getBasicRemote().sendPing(ByteBuffer.wrap(("ping " + priorauthId).getBytes()));
    }

}