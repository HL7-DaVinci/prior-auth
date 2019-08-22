package org.hl7.davinci.priorauth;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
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
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;
import org.hl7.fhir.r4.model.ClaimResponse.Use;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.parser.IParser;

/**
 * The Claim endpoint to READ, SEARCH for, and DELETE submitted claims.
 */
@RequestScoped
@Path("Claim")
public class ClaimEndpoint {

  static final Logger logger = LoggerFactory.getLogger(ClaimEndpoint.class);

  String REQUIRES_BUNDLE = "Prior Authorization Claim/$submit Operation requires a Bundle with a single Claim as the first entry and supporting resources.";
  String PROCESS_FAILED = "Unable to process the request properly. This may be from a request to a cancel a Claim which does not exist or is already cancelled. Check the log for more details";

  @Context
  private UriInfo uri;

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response readClaimJson(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    return Endpoint.read(id, patient, status, Database.CLAIM, uri, RequestType.JSON);
  }

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response readClaimXml(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    return Endpoint.read(id, patient, status, Database.CLAIM, uri, RequestType.XML);
  }

  @DELETE
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response deleteClaimJson(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Database.CLAIM, RequestType.JSON);
  }

  @DELETE
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+xml" })
  public Response deleteClaimXml(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Database.CLAIM, RequestType.XML);
  }

  @POST
  @Path("/$submit")
  @Consumes({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response submitOperationJson(String body) {
    return submitOperation(body, RequestType.JSON);
  }

  @POST
  @Path("/$submit")
  @Consumes({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response submitOperationXml(String body) {
    return submitOperation(body, RequestType.XML);
  }

  /**
   * The submitOperation function for both json and xml
   * 
   * @param body        - the body of the post request.
   * @param requestType - the RequestType of the request.
   * @return - claimResponse response
   */
  private Response submitOperation(String body, RequestType requestType) {
    logger.info("POST /Claim/$submit fhir+" + requestType.name());
    String id = null;
    Status status = Status.OK;
    String formattedData = null;
    try {
      IParser parser = requestType == RequestType.JSON ? App.FHIR_CTX.newJsonParser() : App.FHIR_CTX.newXmlParser();
      IBaseResource resource = parser.parseResource(body);
      if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        if (bundle.hasEntry() && (bundle.getEntry().size() > 1) && bundle.getEntryFirstRep().hasResource()
            && bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.Claim) {
          IBaseResource response = processBundle(bundle);
          if (response == null) {
            // Failed processing bundle...
            status = Status.BAD_REQUEST;
            OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, PROCESS_FAILED);
            formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
          } else {
            if (response.getIdElement().hasIdPart()) {
              id = response.getIdElement().getIdPart();
            }
            formattedData = requestType == RequestType.JSON ? App.DB.json(response) : App.DB.xml(response);
          }
        } else {
          // Claim is required...
          status = Status.BAD_REQUEST;
          OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
          formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
        }
      } else {
        // Bundle is required...
        status = Status.BAD_REQUEST;
        OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
        formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
      }
    } catch (Exception e) {
      // The submission failed so spectacularly that we need
      // catch an exception and send back an error message...
      status = Status.BAD_REQUEST;
      OperationOutcome error = App.DB.outcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
      formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
    }
    ResponseBuilder builder = requestType == RequestType.JSON
        ? Response.status(status).type("application/fhir+json").entity(formattedData)
        : Response.status(status).type("application/fhir+xml").entity(formattedData);
    if (id != null) {
      builder = builder.header("Location", uri.getBaseUri() + "ClaimResponse/" + id);
    }
    return builder.build();
  }

  /**
   * Process the $submit operation Bundle. Theoretically, this is where business
   * logic should be implemented or overridden.
   * 
   * @param bundle Bundle with a Claim followed by other required resources.
   * @return ClaimResponse with the result.
   */
  private IBaseResource processBundle(Bundle bundle) {
    logger.info("processBundle");
    // Store the submission...
    // Generate a shared id...
    String id = UUID.randomUUID().toString();

    // get the patient
    Claim claim = (Claim) bundle.getEntryFirstRep().getResource();
    String patient = "";
    try {
      String[] patientParts = claim.getPatient().getReference().split("/");
      patient = patientParts[patientParts.length - 1];
      logger.info("processBundle: patient: " + patientParts[patientParts.length - 1]);
    } catch (Exception e) {
      logger.error("processBundle: error procesing patient: " + e.toString());
    }

    String claimId = id;
    String responseDisposition = "Unknown";
    ClaimResponseStatus responseStatus = ClaimResponseStatus.ACTIVE;
    ClaimStatus status = claim.getStatus();
    if (status == Claim.ClaimStatus.CANCELLED) {
      // Cancel the claim
      claimId = claim.getIdElement().getIdPart();
      Claim initialClaim = (Claim) App.DB.read(Database.CLAIM, claimId, patient);
      if (initialClaim != null) {
        if (initialClaim.getStatus() != Claim.ClaimStatus.CANCELLED) {
          App.DB.update(Database.CLAIM, claimId, "status", status.getDisplay().toLowerCase());
          responseStatus = ClaimResponseStatus.CANCELLED;
          responseDisposition = "Cancelled";
        } else {
          logger.info("Claim " + claimId + " is already cancelled");
          return null;
        }
      } else {
        logger.info("Claim " + claimId + " does not exist. Unable to cancel");
        return null;
      }
    } else {
      // Store the bundle...
      bundle.setId(id);
      Map<String, Object> bundleMap = new HashMap<String, Object>();
      bundleMap.put("id", id);
      bundleMap.put("patient", patient);
      bundleMap.put("status", Database.getStatusFromResource(bundle));
      bundleMap.put("resource", bundle);
      App.DB.write(Database.BUNDLE, bundleMap);

      // Store the claim...
      claim.setId(id);
      Map<String, Object> claimMap = new HashMap<String, Object>();
      claimMap.put("id", id);
      claimMap.put("patient", patient);
      claimMap.put("status", Database.getStatusFromResource(claim));
      claimMap.put("resource", claim);
      App.DB.write(Database.CLAIM, claimMap);

      // Make up a disposition
      responseDisposition = "Granted";
    }
    // Process the claim...
    // TODO

    // Generate the claim response...
    ClaimResponse response = new ClaimResponse();
    response.setStatus(responseStatus);
    response.setType(claim.getType());
    response.setUse(Use.PREAUTHORIZATION);
    response.setPatient(claim.getPatient());
    response.setCreated(new Date());
    if (claim.hasInsurer()) {
      response.setInsurer(claim.getInsurer());
    } else {
      response.setInsurer(new Reference().setDisplay("Unknown"));
    }
    response.setRequest(new Reference(uri.getBaseUri() + "Claim/" + id));
    response.setOutcome(RemittanceOutcome.COMPLETE);
    response.setDisposition(responseDisposition);
    response.setPreAuthRef(id);
    // TODO response.setPreAuthPeriod(period)?
    response.setId(id);

    // Store the claim respnose...
    Map<String, Object> responseMap = new HashMap<String, Object>();
    responseMap.put("id", id);
    responseMap.put("claimId", claimId);
    responseMap.put("patient", patient);
    responseMap.put("status", Database.getStatusFromResource(response));
    responseMap.put("resource", response);
    App.DB.write(Database.CLAIM_RESPONSE, responseMap);

    // Respond...
    return response;
  }
}
