package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Subscription;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.Subscription.SubscriptionChannelType;
import org.hl7.fhir.r4.model.Subscription.SubscriptionStatus;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * A TimerTask for updating claims.
 */
class UpdateClaimTask extends TimerTask {
    public Bundle bundle;
    public String claimId;
    public String patient;

    static final Logger logger = PALogger.getLogger();

    UpdateClaimTask(Bundle bundle, String claimId, String patient) {
        this.bundle = bundle;
        this.claimId = claimId;
        this.patient = patient;
    }

    /**
     * Update the pended claim and generate a new ClaimResponse.
     * 
     * @param bundle      - the Bundle the Claim is a part of.
     * @param claimId     - the Claim ID.
     * @param patient     - the Patient ID.
     * @param disposition - the new disposition of the updated Claim.
     */
    private Bundle updatePendedClaim(Bundle bundle, String claimId, String patient) {
        logger.info("ClaimEndpoint::updateClaim(" + claimId + "/" + patient + ")");

        // Generate a new id...
        String id = UUID.randomUUID().toString();

        // Get the claim from the database
        Claim claim = (Claim) App.getDB().read(Table.CLAIM, Collections.singletonMap("id", claimId));
        if (claim != null && !FhirUtils.isCancelled(Table.CLAIM, claimId))
            return ClaimResponseFactory.generateAndStoreClaimResponse(bundle, claim, id, Disposition.GRANTED,
                    ClaimResponseStatus.ACTIVE, patient);
        else
            return null;
    }

    @Override
    public void run() {
        if (updatePendedClaim(bundle, claimId, patient) != null) {
            // Check for subscription
            Map<String, Object> constraintMap = new HashMap<String, Object>();
            constraintMap.put("claimResponseId", claimId);
            constraintMap.put("patient", patient);
            List<IBaseResource> subscriptions = App.getDB().readAll(Table.SUBSCRIPTION, constraintMap);

            // Send notification to each subscriber
            subscriptions.stream().forEach(resource -> {
                Subscription subscription = (Subscription) resource;
                String subscriptionId = FhirUtils.getIdFromResource(subscription);
                SubscriptionChannelType subscriptionType = subscription.getChannel().getType();
                if (subscriptionType == SubscriptionChannelType.RESTHOOK) {
                    // Send rest-hook notification...
                    String endpoint = subscription.getChannel().getEndpoint();
                    logger.info("SubscriptionHandler::Sending rest-hook notification to " + endpoint);
                    try {
                        OkHttpClient client = new OkHttpClient();
                        okhttp3.Response response = client
                                .newCall(new Request.Builder().post(RequestBody.create(null, "")).url(endpoint).build())
                                .execute();
                        logger.fine("SubscriptionHandler::Response " + response.code());
                        App.getDB().update(Table.SUBSCRIPTION, Collections.singletonMap("id", subscriptionId),
                                Collections.singletonMap("status",
                                        SubscriptionStatus.ACTIVE.getDisplay().toLowerCase()));
                    } catch (IOException e) {
                        logger.log(Level.SEVERE, "SubscriptionHandler::IOException in request", e);
                        App.getDB().update(Table.SUBSCRIPTION, Collections.singletonMap("id", subscriptionId),
                                Collections.singletonMap("status",
                                        SubscriptionStatus.ERROR.getDisplay().toLowerCase()));
                    }
                } else if (subscriptionType == SubscriptionChannelType.WEBSOCKET) {
                    // Send websocket notification...
                    String websocketId = App.getDB().readString(Table.SUBSCRIPTION,
                            Collections.singletonMap("id", subscriptionId), "websocketId");
                    if (websocketId != null) {
                        logger.info("SubscriptionHandler::Sending web-socket notification to " + websocketId);
                        SubscribeController.sendMessageToUser(websocketId, WebSocketConfig.SUBSCRIBE_USER_NOTIFICATION,
                                "ping: " + subscriptionId);
                        App.getDB().update(Table.SUBSCRIPTION, Collections.singletonMap("id", subscriptionId),
                                Collections.singletonMap("status",
                                        SubscriptionStatus.ACTIVE.getDisplay().toLowerCase()));
                    } else {
                        logger.severe("SubscriptionHandler::Unable to send web-socket notification for subscription "
                                + subscriptionId
                                + " because web-socket id is null. Client did not bind a websocket to id");
                        App.getDB().update(Table.SUBSCRIPTION, Collections.singletonMap("id", subscriptionId),
                                Collections.singletonMap("status",
                                        SubscriptionStatus.ERROR.getDisplay().toLowerCase()));
                    }
                }
            });
        }
    }

}