package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hl7.fhir.r4.model.Subscription.SubscriptionChannelType;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class SubscriptionHandler {

    static final Logger logger = PALogger.getLogger();

    /**
     * Send a Notification of the desired channel to the provided endpoint
     * 
     * @param subscriptionType - the channel type either RESTHOOK or WEBSOCKET
     * @param endpoint         - the endpoint to send the notification to
     */
    public static void sendNotifcation(SubscriptionChannelType subscriptionType, String endpoint) {
        logger.info("SubscriptionHandler::Sending " + subscriptionType.name() + " notification to " + endpoint);
        if (subscriptionType == SubscriptionChannelType.RESTHOOK) {
            // Implement rest hook
            try {
                OkHttpClient client = new OkHttpClient();
                Response response = client.newCall(new Request.Builder().url(endpoint).build()).execute();
                logger.fine("SubscriptionHandler::Resopnse " + response.code());
            } catch (IOException e) {
                logger.log(Level.SEVERE, "SubscriptionHandler::IOException in request", e);
            }
        } else {
            logger.warning("Websocket subscription not implemented for endpoint: " + endpoint);
        }
    }
}