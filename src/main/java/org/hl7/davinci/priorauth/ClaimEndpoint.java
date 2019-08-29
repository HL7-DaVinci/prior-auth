package org.hl7.davinci.priorauth;

import java.lang.invoke.MethodHandles;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.TimerTask;
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
import org.hl7.fhir.r4.model.Claim.RelatedClaimComponent;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;
import org.hl7.fhir.r4.model.ClaimResponse.Use;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.hl7.fhir.r4.model.Claim.ItemComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.uhn.fhir.parser.IParser;

/**
 * The Claim endpoint to READ, SEARCH for, and DELETE submitted claims.
 */
@RequestScoped
@Path("Claim")
public class ClaimEndpoint {

  static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  String REQUIRES_BUNDLE = "Prior Authorization Claim/$submit Operation requires a Bundle with a single Claim as the first entry and supporting resources.";
  String PROCESS_FAILED = "Unable to process the request properly. Check the log for more details.";

  public static String baseUri = null;

  @Context
  private UriInfo uri;

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response readClaimJson(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    Map<String, Object> constraintMap = new HashMap<String, Object>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Database.CLAIM, constraintMap, uri, RequestType.JSON);
  }

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response readClaimXml(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    Map<String, Object> constraintMap = new HashMap<String, Object>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Database.CLAIM, constraintMap, uri, RequestType.XML);
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
    if (baseUri == null) {
      baseUri = uri.getBaseUri().toString();
    }

    String id = null;
    String patient = null;
    Status status = Status.OK;
    String formattedData = null;
    try {
      IParser parser = requestType == RequestType.JSON ? App.FHIR_CTX.newJsonParser() : App.FHIR_CTX.newXmlParser();
      IBaseResource resource = parser.parseResource(body);
      if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        if (bundle.hasEntry() && (bundle.getEntry().size() > 1) && bundle.getEntryFirstRep().hasResource()
            && bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.Claim) {
          ClaimResponse response = processBundle(bundle);
          if (response == null) {
            // Failed processing bundle...
            status = Status.BAD_REQUEST;
            OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, PROCESS_FAILED);
            formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
          } else {
            if (response.getIdElement().hasIdPart()) {
              id = response.getIdElement().getIdPart();
            }
            if (response.hasPatient()) {
              patient = FhirUtils.getPatientIdFromResource(response);
            }
            formattedData = requestType == RequestType.JSON ? App.DB.json(response) : App.DB.xml(response);
          }
        } else {
          // Claim is required...
          status = Status.BAD_REQUEST;
          OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
          formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
        }
      } else {
        // Bundle is required...
        status = Status.BAD_REQUEST;
        OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
        formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
      }
    } catch (Exception e) {
      // The submission failed so spectacularly that we need
      // catch an exception and send back an error message...
      status = Status.BAD_REQUEST;
      OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
      formattedData = requestType == RequestType.JSON ? App.DB.json(error) : App.DB.xml(error);
    }
    ResponseBuilder builder = requestType == RequestType.JSON
        ? Response.status(status).type("application/fhir+json").entity(formattedData)
        : Response.status(status).type("application/fhir+xml").entity(formattedData);
    if (id != null) {
      builder = builder.header("Location",
          uri.getBaseUri() + "ClaimResponse?identifier=" + id + "&patient.identifier=" + patient);
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
  private ClaimResponse processBundle(Bundle bundle) {
    logger.info("processBundle");
    // Store the submission...
    // Generate a shared id...
    String id = UUID.randomUUID().toString();

    // get the patient
    Claim claim = (Claim) bundle.getEntryFirstRep().getResource();
    String patient = FhirUtils.getPatientIdFromResource(claim);

    String claimId = id;
    String responseDisposition = "Unknown";
    ClaimResponseStatus responseStatus = ClaimResponseStatus.ACTIVE;
    ClaimStatus status = claim.getStatus();
    boolean delayedUpdate = false;
    String delayedDisposition = "";

    if (status == Claim.ClaimStatus.CANCELLED) {
      // Cancel the claim...
      claimId = claim.getIdElement().getIdPart();
      if (cancelClaim(claimId, patient)) {
        responseStatus = ClaimResponseStatus.CANCELLED;
        responseDisposition = "Cancelled";
      } else
        return null;
    } else {
      // Store the claim...
      claim.setId(id);
      String claimStatusStr = FhirUtils.getStatusFromResource(claim);
      Map<String, Object> claimMap = new HashMap<String, Object>();
      claimMap.put("id", id);
      claimMap.put("patient", patient);
      claimMap.put("status", claimStatusStr);
      claimMap.put("resource", claim);
      RelatedClaimComponent related = getRelatedComponent(claim);
      if (related != null)
        claimMap.put("related", related.getIdElement().asStringValue());
      if (!App.DB.write(Database.CLAIM, claimMap))
        return null;

      // Store the bundle...
      bundle.setId(id);
      Map<String, Object> bundleMap = new HashMap<String, Object>();
      bundleMap.put("id", id);
      bundleMap.put("patient", patient);
      bundleMap.put("status", FhirUtils.getStatusFromResource(bundle));
      bundleMap.put("resource", bundle);
      App.DB.write(Database.BUNDLE, bundleMap);

      // Store the claim items...
      if (claim.hasItem()) {
        processClaimItems(claim, related, id);
      }

      // generate random responses since not cancelling
      switch (getRand(3)) {
      case 1:
        responseDisposition = "Granted";
        break;
      case 2:
        responseDisposition = "Pending";

        switch (getRand(2)) {
        case 1:
          delayedDisposition = "Granted";
          break;
        case 2:
          delayedDisposition = "Denied";
          break;
        }

        delayedUpdate = true;
        break;
      case 3:
      default:
        responseDisposition = "Denied";
        break;
      }
    }
    // Process the claim...
    // TODO

    // Generate the claim response...
    ClaimResponse response = generateAndStoreClaimResponse(claim, id, responseDisposition, responseStatus, patient);

    if (delayedUpdate) {
      // schedule the update
      scheduleClaimUpdate(id, patient, delayedDisposition);
    }

    // Respond...
    return response;
  }

  /**
   * Process the claim items in the database. For a new claim add the items, for
   * an updated claim update the items.
   * 
   * @param claim   - the claim the items belong to.
   * @param related - the related claim (old claim this is replacing).
   * @param id      - the id of the claim.
   * @return true if all updates successful, false otherwise.
   */
  private boolean processClaimItems(Claim claim, RelatedClaimComponent related, String id) {
    boolean ret = true;
    String claimStatusStr = FhirUtils.getStatusFromResource(claim);
    if (related != null) {
      // Update the items...
      for (ItemComponent item : claim.getItem()) {
        boolean itemIsCancelled = false;
        String extUrl = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemCancelled";
        if (item.hasExtension(extUrl)) {
          Extension ext = item.getExtensionsByUrl(extUrl).get(0);
          if (ext.hasValue()) {
            Type type = ext.getValue();
            itemIsCancelled = type.castToBoolean(type).booleanValue();
          }
        }
        Map<String, Object> dataMap = new HashMap<String, Object>();
        Map<String, Object> constraintMap = new HashMap<String, Object>();
        constraintMap.put("id", related.getIdElement().asStringValue());
        constraintMap.put("sequence", item.getSequence());
        dataMap.put("id", id);
        dataMap.put("status", itemIsCancelled ? ClaimStatus.CANCELLED.getDisplay().toLowerCase() : claimStatusStr);
        if (!App.DB.update(Database.CLAIM_ITEM, constraintMap, dataMap))
          ret = false;
      }
    } else {
      // Add the claim items...
      for (ItemComponent item : claim.getItem()) {
        Map<String, Object> itemMap = new HashMap<String, Object>();
        itemMap.put("id", id);
        itemMap.put("sequence", item.getSequence());
        itemMap.put("status", claimStatusStr);
        if (!App.DB.write(Database.CLAIM_ITEM, itemMap))
          ret = false;
      }
    }
    return ret;
  }

  /**
   * Determine if a cancel can be performed and then update the DB to reflect the
   * cancel
   * 
   * @param claimId - the claim id to cancel.
   * @param patient - the patient for the claim to cancel.
   * @return true if the claim was cancelled successfully and false otherwise
   */
  private boolean cancelClaim(String claimId, String patient) {
    boolean result;
    Map<String, Object> claimConstraintMap = new HashMap<String, Object>();
    claimConstraintMap.put("id", claimId);
    claimConstraintMap.put("patient", patient);
    Claim initialClaim = (Claim) App.DB.read(Database.CLAIM, claimConstraintMap);
    if (initialClaim != null) {
      if (initialClaim.getStatus() != Claim.ClaimStatus.CANCELLED) {
        // Cancel the claim...
        Map<String, Object> dataMap = new HashMap<String, Object>();
        Map<String, Object> constraintMap = new HashMap<String, Object>();
        constraintMap.put("id", claimId);
        dataMap.put("status", Claim.ClaimStatus.CANCELLED.getDisplay().toLowerCase());
        App.DB.update(Database.CLAIM, constraintMap, dataMap);

        // Cancel the claim items
        for (ItemComponent item : initialClaim.getItem()) {
          dataMap = new HashMap<String, Object>();
          constraintMap = new HashMap<String, Object>();
          constraintMap.put("id", claimId);
          constraintMap.put("sequence", item.getSequence());
          dataMap.put("id", claimId);
          dataMap.put("status", ClaimStatus.CANCELLED.getDisplay().toLowerCase());
          App.DB.update(Database.CLAIM_ITEM, constraintMap, dataMap);
        }
        result = true;
      } else {
        logger.info("Claim " + claimId + " is already cancelled");
        result = false;
      }
    } else {
      logger.info("Claim " + claimId + " does not exist. Unable to cancel");
      result = false;
    }
    return result;
  }

  /**
   * Get the related claim for an update to a claim (replaces relationship)
   * 
   * @param claim - the base Claim resource.
   * @return the first related claim with relationship "replaces" or null if no
   *         matching related resource.
   */
  private RelatedClaimComponent getRelatedComponent(Claim claim) {
    if (claim.hasRelated()) {
      for (RelatedClaimComponent relatedComponent : claim.getRelated()) {
        if (relatedComponent.hasRelationship()) {
          for (Coding code : relatedComponent.getRelationship().getCoding()) {
            if (code.getCode().equals("replaces")) {
              // This claim is an update to an old claim
              return relatedComponent;
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Generate a new ClaimResponse and store it in the database.
   *
   * @param claim               The claim which this ClaimResponse is in reference
   *                            to.
   * @param id                  The new identifier for this ClaimResponse.
   * @param responseDisposition The new disposition for this ClaimResponse
   *                            (Granted, Pending, Cancelled, Declined ...).
   * @param responseStatus      The new status for this ClaimResponse (Active,
   *                            Cancelled, ...).
   * @param patient             The identifier for the patient this ClaimResponse
   *                            is referring to.
   * @return ClaimResponse that has been generated and stored in the Database.
   */
  private ClaimResponse generateAndStoreClaimResponse(Claim claim, String id, String responseDisposition,
      ClaimResponseStatus responseStatus, String patient) {
    logger.info("generateAndStoreClaimResponse: " + id + "/" + patient + ", disposition: " + responseDisposition
        + ", status: " + responseStatus);

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
    response.setRequest(new Reference(
        baseUri + "Claim?identifier=" + claim.getIdElement().getIdPart() + "&patient.identifier=" + patient));
    if (responseDisposition == "Pending") {
      response.setOutcome(RemittanceOutcome.QUEUED);
    } else {
      response.setOutcome(RemittanceOutcome.COMPLETE);
    }
    response.setDisposition(responseDisposition);
    response.setPreAuthRef(id);
    // TODO response.setPreAuthPeriod(period)?
    response.setId(id);

    // Store the claim respnose...
    Map<String, Object> responseMap = new HashMap<String, Object>();
    responseMap.put("id", id);
    responseMap.put("claimId", claim.getIdElement().getIdPart());
    responseMap.put("patient", patient);
    responseMap.put("status", FhirUtils.getStatusFromResource(response));
    responseMap.put("resource", response);
    App.DB.write(Database.CLAIM_RESPONSE, responseMap);

    return response;
  }

  /**
   * A TimerTask for updating claims.
   */
  class UpdateClaimTask extends TimerTask {
    public String claimId;
    public String patient;
    public String disposition;

    UpdateClaimTask(String claimId, String patient, String disposition) {
      this.claimId = claimId;
      this.patient = patient;
      this.disposition = disposition;
    }

    @Override
    public void run() {
      updateClaim(claimId, patient, disposition);
    }
  }

  /**
   * Schedule an update to the Claim to support pending actions.
   *
   * @param id          - the Claim ID.
   * @param patient     - the Patient ID.
   * @param disposition - the new disposition of the updated Claim.
   */
  private void scheduleClaimUpdate(String id, String patient, String disposition) {
    App.timer.schedule(new UpdateClaimTask(id, patient, disposition), 5000); // 5s
  }

  /**
   * Update the claim and generate a new ClaimResponse.
   * 
   * @param claimId     - the Claim ID.
   * @param patient     - the Patient ID.
   * @param disposition - the new disposition of the updated Claim.
   */
  private void updateClaim(String claimId, String patient, String disposition) {
    logger.info("updateClaim: " + claimId + "/" + patient + ", disposition: " + disposition);

    // Generate a new id...
    String id = UUID.randomUUID().toString();

    // Get the claim from the database
    Map<String, Object> claimConstraintMap = new HashMap<String, Object>();
    claimConstraintMap.put("id", claimId);
    claimConstraintMap.put("patient", patient);
    Claim claim = (Claim) App.DB.read(Database.CLAIM, claimConstraintMap);
    if (claim != null) {
      generateAndStoreClaimResponse(claim, id, "Granted", ClaimResponseStatus.ACTIVE, patient);
    }
  }

  /**
   * Get a random number between 1 and max.
   *
   * @param max The largest the random number could be.
   * @return int representing the random number.
   */
  private int getRand(int max) {
    Date date = new Date();
    // System.out.printf(String.valueOf(date.getTime()) + ": ");
    return (int) ((date.getTime() % max) + 1);
  }

}
