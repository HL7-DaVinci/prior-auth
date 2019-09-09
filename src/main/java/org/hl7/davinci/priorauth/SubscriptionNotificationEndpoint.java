package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;

import ca.uhn.fhir.parser.IParser;
import okhttp3.OkHttpClient;

/**
 * The SubscriptionNotification endpoint to send subscriptions notifications to
 * and process them
 */
@RequestScoped
@Path("SubscriptionNotification")
public class SubscriptionNotificationEndpoint {

  static final Logger logger = PALogger.getLogger();
  private static final String BASE_URL = "http://localhost:9000/fhir/";
  private static final String CLAIM_RESPONSE = "ClaimResponse";
  private static final String SUBSCRIPTION = "Subscription";

  @Context
  private UriInfo uri;

  @GET
  @Path("/")
  public Response subscriptionNotification(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient, @QueryParam("status") String status) {
    logger.info(
        "ProviderClient::SubscriptionNotificationEndpoint::Notification(" + id + ", " + patient + ", " + status + ")");
    Status returnStatus = Status.OK;

    try {
      // Send REST request for the new ClaimResponse...
      String url = BASE_URL + CLAIM_RESPONSE + "?identifier=" + id + "&patient.identifier=" + patient + "&status="
          + status;
      logger.fine("ProviderClient::SubscriptionNotificationEndpoint::url(" + url + ")");
      OkHttpClient client = new OkHttpClient();
      okhttp3.Response response = client
          .newCall(new okhttp3.Request.Builder().header("Accept", "application/fhir+json").url(url).build()).execute();
      IParser parser = App.FHIR_CTX.newJsonParser();
      Bundle claimResponseBundle = (Bundle) parser.parseResource(response.body().string());
      logger.info("ProviderClient::SubscriptionNotificationEndpoint::Received Bundle " + claimResponseBundle.getId());
      logger.fine(App.getDB().json(claimResponseBundle));

      // Check the ClaimResponse outcome...
      ClaimResponse claimResponse = (ClaimResponse) claimResponseBundle.getEntry().get(0).getResource();
      RemittanceOutcome outcome = claimResponse.getOutcome();
      if (outcome == RemittanceOutcome.COMPLETE || outcome == RemittanceOutcome.ERROR) {
        // Delete the subscription...
        logger.info(
            "ProviderClient::SubscriptionNotificationEndpoint::Delete Subscription (" + id + ", " + patient + ")");
        url = BASE_URL + SUBSCRIPTION + "?identifier=" + id + "&patient.identifier=" + patient;
        logger.fine("ProviderClient::SubscriptionNotificationEndpoint::url(" + url + ")");
        response = client
            .newCall(new okhttp3.Request.Builder().header("Accept", "application/fhir+json").url(url).delete().build())
            .execute();
      }
    } catch (IOException e) {
      returnStatus = Status.BAD_REQUEST;
      logger.log(Level.SEVERE, "ProviderClient::SubscriptionNotificationEndpoint::IOException in polling request", e);
    }

    return Response.status(returnStatus).type("application/fhir+json").build();
  }

}
