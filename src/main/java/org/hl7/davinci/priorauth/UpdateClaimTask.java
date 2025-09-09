package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.parser.IParser;
import okhttp3.MediaType;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.Subscription.SubscriptionChannelType;
import org.hl7.fhir.r4.model.Subscription.SubscriptionStatus;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * A TimerTask for updating claims.
 */
public class UpdateClaimTask extends TimerTask {
    private Bundle bundle;
    private String claimId;
    private String patient;

    static final Logger logger = PALogger.getLogger();

    public UpdateClaimTask(Bundle bundle, String claimId, String patient) {
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
                    ClaimResponseStatus.ACTIVE, patient, true);
        else
            return null;
    }

    @Override
    public void run() {
        Bundle updatedClaimBundle = updatePendedClaim(bundle, claimId, patient);
        if (updatedClaimBundle != null) {

            // Check for subscription
            Map<String, Object> constraintMap = new HashMap<>();
            // constraintMap.put("claimResponseId", claimId);
            // constraintMap.put("patient", patient);
            constraintMap.put("orgId", claimId);
            String orgId;
            ClaimResponse cr = (ClaimResponse)updatedClaimBundle.getEntryFirstRep().getResource();
            String[] requestorRef = cr.getRequestor().getReference().split("/");
            BundleEntryComponent entry = FhirUtils.getEntryComponentFromBundle(bundle, ResourceType.fromCode(requestorRef[0]), requestorRef[1]);
            Identifier identifier = null;
            if (entry.getResource() instanceof Organization) {
                identifier = ((Organization)entry.getResource()).getIdentifierFirstRep();
            }
            else if (entry.getResource() instanceof PractitionerRole) {
                identifier = ((PractitionerRole)entry.getResource()).getIdentifierFirstRep();
            }
            orgId = identifier.getSystem() + "|" + identifier.getValue();
            constraintMap.put("orgId", orgId);

            List<IBaseResource> subscriptions = App.getDB().readAll(Table.SUBSCRIPTION, constraintMap);
            logger.info("SubscriptionEndpoint::Found " + subscriptions.size() + " subscriptions for orgId: " + orgId);

            AtomicInteger subscriptionEventNumber = new AtomicInteger();
            // Send notification to each subscriber
            subscriptions.stream().forEach(resource -> {
                subscriptionEventNumber.addAndGet(1);

                Subscription subscription = (Subscription) resource;
                String subscriptionId = FhirUtils.getIdFromResource(subscription);
                SubscriptionChannelType subscriptionType = subscription.getChannel().getType();

                //Build Subscriptions R5 Backport Notification Bundle:
                //http://hl7.org/fhir/uv/subscriptions-backport/components.html#subscription-notifications
                Bundle bundle = new Bundle();
                bundle.setType(Bundle.BundleType.HISTORY);
                
                Parameters parameters = new Parameters();
                Meta meta = new Meta();
                meta.addProfile("http://hl7.org/fhir/uv/subscriptions-backport/StructureDefinition/backport-subscription-status-r4");
                parameters.setMeta(meta);
                parameters.addParameter().setName("subscription").setResource(subscription);
                parameters.addParameter().setName("topic").setValue(new CanonicalType("http://hl7.org/fhir/us/davinci-pas/SubscriptionTopic/PASSubscriptionTopic"));
                parameters.addParameter().setName("status").setValue(new CodeType(subscription.getStatus().toCode()));
                parameters.addParameter().setName("type").setValue(new CodeType("event-notification"));

                Parameters.ParametersParameterComponent notificationEventPart = new Parameters.ParametersParameterComponent();
                notificationEventPart.setName("notification-event");
                notificationEventPart.addPart().setName("event-number").setValue(new StringType(subscriptionEventNumber.toString()));
                notificationEventPart.addPart().setName("timestamp").setValue(new DateTimeType(new Date()));
                notificationEventPart.addPart().setName("focus").setValue(new Reference(updatedClaimBundle.getEntryFirstRep().getFullUrl()));
                parameters.addParameter(notificationEventPart);

                //TODO: Add error
                //parameters.addParameter().setName("error").setValue(new CodeableConcept());

                bundle.addEntry().setResource(parameters);
                bundle.addEntry().setResource(updatedClaimBundle);

                if (subscriptionType == SubscriptionChannelType.RESTHOOK) {
                    // Send rest-hook notification...
                    String endpoint = subscription.getChannel().getEndpoint();
                    logger.info("SubscriptionHandler::Sending rest-hook notification to " + endpoint);
                    try {

                        IParser parser = FhirContext.forR4().newJsonParser();
                        RequestBody body = RequestBody.create(parser.encodeResourceToString(bundle), MediaType.parse("application/json; charset=utf-8"));

                        OkHttpClient client = new OkHttpClient();
                        okhttp3.Response response = client
                                .newCall(new Request.Builder().url(endpoint).post(body).build())
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
                        IParser parser = FhirContext.forR4().newJsonParser();
                        logger.info("SubscriptionHandler::Sending web-socket notification to " + websocketId);
                        SubscribeController.sendMessageToUser(websocketId, WebSocketConfig.SUBSCRIBE_USER_NOTIFICATION,
                                "ping: " + subscriptionId + " Subscription Notification Bundle: " + parser.encodeResourceToString(bundle));
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