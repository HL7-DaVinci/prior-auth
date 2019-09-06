package org.hl7.davinci.priorauth;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.hl7.davinci.priorauth.Endpoint.RequestType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Subscription;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;

import ca.uhn.fhir.parser.IParser;

/**
 * The Subscription endpoint to create new subscriptions or delete outdated
 * ones.
 */
@RequestScoped
@Path("Subscription")
public class SubscriptionEndpoint {

    static final Logger logger = PALogger.getLogger();

    String REQUIRES_SUBSCRIPTION = "Prior Authorization Subscription Operation requires a Subscription resource";
    String SUBSCRIPTION_ADDED_SUCCESS = "Subscription successful";
    String PROCESS_FAILED = "Unable to process the request properly. Check the log for more details.";

    @Context
    private UriInfo uri;

    @POST
    @Path("/")
    @Consumes({ MediaType.APPLICATION_JSON, "application/fhir+json" })
    public Response addSubscriptionJSON(String body) {
        return addSubscription(body, RequestType.JSON);
    }

    @POST
    @Path("/")
    @Consumes({ MediaType.APPLICATION_XML, "application/fhir+xml" })
    public Response addSubscriptionXML(String body) {
        return addSubscription(body, RequestType.XML);
    }

    @DELETE
    @Path("/")
    @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
    public Response deleteSubscriptionJSON(@QueryParam("identifier") String id,
            @QueryParam("patient.identifier") String patient) {
        return Endpoint.delete(id, patient, Database.SUBSCRIPTION, RequestType.JSON);
    }

    @DELETE
    @Path("/")
    @Produces({ MediaType.APPLICATION_JSON, "application/fhir+xml" })
    public Response deleteSubscriptionXML(@QueryParam("identifier") String id,
            @QueryParam("patient.identifier") String patient) {
        return Endpoint.delete(id, patient, Database.SUBSCRIPTION, RequestType.XML);
    }

    private Response addSubscription(String body, RequestType requestType) {
        logger.info("POST /Subscription fhir+" + requestType.name());

        Status status = Status.OK;
        String formattedData = null;
        try {
            IParser parser = requestType == RequestType.JSON ? App.FHIR_CTX.newJsonParser()
                    : App.FHIR_CTX.newXmlParser();
            IBaseResource resource = parser.parseResource(body);
            if (resource instanceof Subscription) {
                Subscription subscription = (Subscription) resource;
                if (processSubscription(subscription))
                    formattedData = SUBSCRIPTION_ADDED_SUCCESS;
                else {
                    status = Status.BAD_REQUEST;
                    OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID,
                            PROCESS_FAILED);
                    formattedData = requestType == RequestType.JSON ? App.getDB().json(error) : App.getDB().xml(error);
                }
            } else {
                // Subscription is required...
                status = Status.BAD_REQUEST;
                OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID,
                        REQUIRES_SUBSCRIPTION);
                formattedData = requestType == RequestType.JSON ? App.getDB().json(error) : App.getDB().xml(error);
            }
        } catch (Exception e) {
            // The subscription failed so spectacularly that we need to
            // catch an exception and send back an error message...
            status = Status.BAD_REQUEST;
            OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
            formattedData = requestType == RequestType.JSON ? App.getDB().json(error) : App.getDB().xml(error);
        }
        ResponseBuilder builder = requestType == RequestType.JSON
                ? Response.status(status).type("application/fhir+json").entity(formattedData)
                : Response.status(status).type("application/fhir+xml").entity(formattedData);
        return builder.build();
    }

    private boolean processSubscription(Subscription subscription) {
        // Get the criteria details parsed
        String claimResponseId = "";
        String patient = "";
        String status = "";
        String criteria = subscription.getCriteria();
        String regex = "identifier=(.*)&patient.identifier=(.*)&status=(.*)";

        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(criteria);

        if (matcher.find() && matcher.groupCount() == 3) {
            claimResponseId = matcher.group(1);
            patient = matcher.group(2);
            status = matcher.group(3);
        } else {
            logger.fine("Subscription.criteria: " + criteria);
            logger.severe("Subcription.criteria is not in the form " + regex);
            return false;
        }

        // Add to db
        Map<String, Object> dataMap = new HashMap<String, Object>();
        dataMap.put("claimResponseId", claimResponseId);
        dataMap.put("patient", patient);
        dataMap.put("status", status);
        dataMap.put("resource", subscription);
        return App.getDB().write(Database.SUBSCRIPTION, dataMap);
    }
}
