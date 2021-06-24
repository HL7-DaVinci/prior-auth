package org.hl7.davinci.priorauth.endpoint;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.hl7.davinci.priorauth.authorization.AuthUtils;
import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.Audit;
import org.hl7.davinci.priorauth.ClaimResponseFactory;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.ProcessClaimItemTask;
import org.hl7.davinci.priorauth.UpdateClaimTask;
import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.endpoint.Endpoint.RequestType;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.hl7.fhir.r4.model.Claim.ItemComponent;

import ca.uhn.fhir.parser.IParser;

/**
 * The Claim endpoint to READ, SEARCH for, and DELETE submitted claims.
 */
@CrossOrigin
@RestController
@RequestMapping("/Claim")
public class ClaimEndpoint {

  static final Logger logger = PALogger.getLogger();

  static final String REQUIRES_BUNDLE = "Prior Authorization Claim/$submit Operation requires a Bundle with a single Claim as the first entry and supporting resources.";
  static final String PROCESS_FAILED = "Unable to process the request properly. Check the log for more details.";

  static final HashMap<String, Timer> pendedTimers = new HashMap<>();

  @GetMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> readClaimJson(HttpServletRequest request,
      @RequestParam(name = "identifier", required = false) String id,
      @RequestParam(name = "patient.identifier") String patient,
      @RequestParam(name = "status", required = false) String status) {
    Map<String, Object> constraintMap = new HashMap<>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Table.CLAIM, constraintMap, request, RequestType.JSON);
  }

  @GetMapping(value = "", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> readClaimXml(HttpServletRequest request,
      @RequestParam(name = "identifier", required = false) String id,
      @RequestParam(name = "patient.identifier") String patient,
      @RequestParam(name = "status", required = false) String status) {
    Map<String, Object> constraintMap = new HashMap<>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Table.CLAIM, constraintMap, request, RequestType.XML);
  }

  @CrossOrigin
  @DeleteMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> deleteClaimJson(HttpServletRequest request,
      @RequestParam(name = "identifier") String id, @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Table.CLAIM, request, RequestType.JSON);
  }

  @CrossOrigin
  @DeleteMapping(value = "", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> deleteClaimXml(HttpServletRequest request, @RequestParam(name = "identifier") String id,
      @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Table.CLAIM, request, RequestType.XML);
  }

  @PostMapping(value = "/$submit", consumes = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> submitOperationJson(HttpServletRequest request, HttpEntity<String> entity) {
    return submitOperation(entity.getBody(), RequestType.JSON, request);
  }

  @PostMapping(value = "/$submit", consumes = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> submitOperationXml(HttpServletRequest request, HttpEntity<String> entity) {
    return submitOperation(entity.getBody(), RequestType.XML, request);
  }

  /**
   * The submitOperation function for both json and xml
   * 
   * @param body        - the body of the post request.
   * @param requestType - the RequestType of the request.
   * @return - claimResponse response
   */
  private ResponseEntity<String> submitOperation(String body, RequestType requestType, HttpServletRequest request) {
    logger.info("POST /Claim/$submit fhir+" + requestType.name());
    App.setBaseUrl(Endpoint.getServiceBaseUrl(request));

    if (!AuthUtils.validateAccessToken(request))
      return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
          .body("{ error: \"Invalid access token. Make sure to use Authorization: Bearer (token)\" }");

    String id = null;
    String patient = null;
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String formattedData = null;
    AuditEventOutcome auditOutcome = AuditEventOutcome.MINOR_FAILURE;
    try {
      IParser parser = requestType == RequestType.JSON ? App.getFhirContext().newJsonParser()
          : App.getFhirContext().newXmlParser();
      IBaseResource resource = parser.parseResource(body);
      if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        if (bundle.hasEntry() && (!bundle.getEntry().isEmpty()) && bundle.getEntry().get(0).hasResource()
            && bundle.getEntry().get(0).getResource().getResourceType() == ResourceType.Claim) {
          Bundle responseBundle = processBundle(bundle);
          if (responseBundle == null) {
            // Failed processing bundle...
            OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, PROCESS_FAILED);
            formattedData = FhirUtils.getFormattedData(error, requestType);
            logger.severe("ClaimEndpoint::SubmitOperation:Failed to process Bundle:" + bundle.getId());
            auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
          } else {
            ClaimResponse response = FhirUtils.getClaimResponseFromResponseBundle(responseBundle);
            id = FhirUtils.getIdFromResource(response);
            patient = FhirUtils.getPatientIdentifierFromBundle(responseBundle);
            formattedData = FhirUtils.getFormattedData(responseBundle, requestType);
            status = HttpStatus.CREATED;
            auditOutcome = AuditEventOutcome.SUCCESS;
          }
        } else {
          // Claim is required...
          OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
          formattedData = FhirUtils.getFormattedData(error, requestType);
          logger.severe("ClaimEndpoint::SubmitOperation:First bundle entry is not a PASClaim");
        }
      } else {
        // Bundle is required...
        OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
        formattedData = FhirUtils.getFormattedData(error, requestType);
        logger.severe("ClaimEndpoint::SubmitOperation:Body is not a Bundle");
      }
    } catch (Exception e) {
      // The submission failed so spectacularly that we need to
      // catch an exception and send back an error message...
      OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
      formattedData = FhirUtils.getFormattedData(error, requestType);
      auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
    }
    Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, auditOutcome, null, request, "POST /Claim/$submit");
    MediaType contentType = requestType == RequestType.JSON ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_XML;
    String fhirContentType = requestType == RequestType.JSON ? "application/fhir+json" : "application/xml+json";
    return ResponseEntity.status(status).contentType(contentType)
        .header(HttpHeaders.CONTENT_TYPE, fhirContentType + "; charset=utf-8")
        .header(HttpHeaders.LOCATION,
            App.getBaseUrl() + "/ClaimResponse?identifier=" + id + "&patient.identifier=" + patient)
        .body(formattedData);
  }

  /**
   * Process the $submit operation Bundle. Theoretically, this is where business
   * logic should be implemented or overridden.
   * 
   * @param bundle Bundle with a Claim followed by other required resources.
   * @return ClaimResponse with the result.
   */
  private Bundle processBundle(Bundle bundle) {
    logger.fine("ClaimEndpoint::processBundle:" + bundle.getId());

    // Generate a shared id...
    String id = UUID.randomUUID().toString();

    // Get the patient
    Claim claim = FhirUtils.getClaimFromRequestBundle(bundle);
    String patient = FhirUtils.getPatientIdentifierFromBundle(bundle);
    if (patient == null) {
      logger.severe("ClaimEndpoint::processBundle:Patient was null");
      return null;
    }

    ClaimStatus status = claim.getStatus();
    Disposition responseDisposition = null;
    ClaimResponseStatus responseStatus = ClaimResponseStatus.ACTIVE;

    if (status == ClaimStatus.CANCELLED) {
      // Cancel the claim...
      if (cancelClaim(FhirUtils.getIdFromResource(claim), patient)) {
        responseStatus = ClaimResponseStatus.CANCELLED;
        responseDisposition = Disposition.CANCELLED;
        cancelTimer(FhirUtils.getIdFromResource(claim));
      } else {
        logger.severe("ClaimEndpoint::Unable to cancel Claim/" + FhirUtils.getIdFromResource(claim));
        return null;
      }
    } else {
      // Store the claim...
      claim.setId(id);
      Map<String, Object> claimMap = new HashMap<>();
      claimMap.put("isDifferential", FhirUtils.isDifferential(bundle));
      claimMap.put("id", id);
      claimMap.put("patient", patient);
      claimMap.put("status", FhirUtils.getStatusFromResource(claim));
      claimMap.put("resource", claim);
      String relatedId = FhirUtils.getRelatedComponentId(claim);
      if (relatedId != null) {
        // This is an update...

        // Check the related id exists
        Claim relatedClaim = (Claim) App.getDB().read(Table.CLAIM, Collections.singletonMap("id", relatedId));
        if (relatedClaim == null) {
          logger.warning("ClaimEndpoint::Unable to submit update to claim " + relatedId + " because it does not exist");
          return null;
        }

        relatedId = App.getDB().getMostRecentId(relatedId);
        logger.info("ClaimEndpoint::Updated related id to most recent: " + relatedId);
        claimMap.put("related", relatedId);

        // Check if related is cancelled in the DB
        if (FhirUtils.isCancelled(Table.CLAIM, relatedId)) {
          logger.warning(
              "ClaimEndpoint::Unable to submit update to claim " + relatedId + " because it has been cancelled");
          return null;
        }

        // Check if the related is pended in the DB
        if (FhirUtils.isPended(relatedId)) {
          logger.warning("ClaimEndpoint::Related claim " + relatedId + " is pending. Cancelling the scheduled update");
          cancelTimer(relatedId);
        }
      }

      if (!App.getDB().write(Table.CLAIM, claimMap))
        return null;

      // Store the bundle...
      bundle.setId(id);
      Map<String, Object> bundleMap = new HashMap<>();
      bundleMap.put("id", id);
      bundleMap.put("patient", patient);
      bundleMap.put("resource", bundle);
      App.getDB().write(Table.BUNDLE, bundleMap);

      // Store the claim items...
      if (claim.hasItem() && !processClaimItems(bundle, id, relatedId)) {
        logger.severe("ClaimEndpoint::processBundle:unable to process claim items successfully");
        return null;
      }

      responseDisposition = ClaimResponseFactory.determineDisposition(bundle);
    }

    // Generate the claim response...
    Bundle responseBundle = ClaimResponseFactory.generateAndStoreClaimResponse(bundle, claim, id, responseDisposition,
        responseStatus, patient, false);

    // Schedule update to Pended Claim
    if (responseDisposition == Disposition.PENDING) {
      schedulePendedClaimUpdate(bundle, id, patient);
    }

    // Respond...
    return responseBundle;
  }

  /**
   * Process the claim items in the database. For a new claim add the items, for
   * an updated claim update the items.
   * 
   * @param claim     - the claim the items belong to.
   * @param id        - the id of the claim.
   * @param relatedId - the related id to this claim.
   * @return true if all updates successful, false otherwise.
   */
  private boolean processClaimItems(Bundle bundle, String id, String relatedId) {
    boolean ret = true;
    Claim claim = FhirUtils.getClaimFromRequestBundle(bundle);
    String claimStatusStr = FhirUtils.getStatusFromResource(claim);

    // Start all of the threads
    Map<Integer, ProcessClaimItemTask> threads = new HashMap<>();
    for (ItemComponent item : claim.getItem()) {
      ProcessClaimItemTask itemTask = new ProcessClaimItemTask(bundle, item, id, relatedId, claimStatusStr);
      threads.put(item.getSequence(), itemTask);
      itemTask.start();
    }

    // Block until all of the threads are done
    for (ProcessClaimItemTask itemTask : threads.values()) {
      try {
        itemTask.getThread().join();
        if (itemTask.getStatus() != 0)
          ret = false;
        logger.fine("ClaimEndpoint::processClaimItems:finsihed processing " + itemTask.getItemName() + ":"
            + itemTask.getStatus());
      } catch (InterruptedException e) {
        ret = false;
        logger.log(Level.SEVERE, "ClaimEndpoint::processClaimItems:Thread Interrutped:" + itemTask.getItemName(), e);
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
    Map<String, Object> claimConstraintMap = new HashMap<>();
    claimConstraintMap.put("id", claimId);
    claimConstraintMap.put("patient", patient);
    Claim initialClaim = (Claim) App.getDB().read(Table.CLAIM, claimConstraintMap);
    if (initialClaim != null) {
      if (initialClaim.getStatus() != ClaimStatus.CANCELLED) {
        // Cancel the claim...
        initialClaim.setStatus(ClaimStatus.CANCELLED);
        Map<String, Object> dataMap = new HashMap<>();
        Map<String, Object> constraintMap = new HashMap<>();
        constraintMap.put("id", claimId);
        dataMap.put("status", ClaimStatus.CANCELLED.getDisplay().toLowerCase());
        dataMap.put("resource", initialClaim);
        App.getDB().update(Table.CLAIM, constraintMap, dataMap);

        cascadeCancel(claimId);

        // Cancel items...
        dataMap = new HashMap<>();
        constraintMap = new HashMap<>();
        constraintMap.put("id", App.getDB().getMostRecentId(claimId));
        dataMap.put("status", ClaimStatus.CANCELLED.getDisplay().toLowerCase());
        App.getDB().update(Table.CLAIM_ITEM, constraintMap, dataMap);

        result = true;
      } else {
        logger.warning("ClaimEndpoint::Claim " + claimId + " is already cancelled");
        result = false;
      }
    } else {
      logger.warning("ClaimEndpoint::Claim " + claimId + " does not exist. Unable to cancel");
      result = false;
    }
    return result;
  }

  /**
   * Helper function to complete the cascade aspect of a Claim cancel. When a
   * Claim is cancelled this will cascade the cancel to all related Claims (both
   * up and downstream). A cancel updates the database status as well as update
   * the resource status
   * 
   * @param claimId - the id of the original Claim to be cancelled
   */
  private void cascadeCancel(String claimId) {
    // Cascade delete shared maps
    Map<String, Object> dataMap = new HashMap<>();
    dataMap.put("status", ClaimStatus.CANCELLED.getDisplay().toLowerCase());
    dataMap.put("resource", null);
    Map<String, Object> constraintMap = new HashMap<>();
    constraintMap.put("id", null);

    // Cascade up to all the Claims submitted after this which reference this Claim
    Map<String, Object> readConstraintMap = new HashMap<>();
    readConstraintMap.put("related", claimId);
    Claim referencingClaim = (Claim) App.getDB().read(Table.CLAIM, readConstraintMap);
    String referencingId;

    while (referencingClaim != null) {
      // Update referencing claim to cancelled
      referencingClaim.setStatus(ClaimStatus.CANCELLED);
      referencingId = FhirUtils.getIdFromResource(referencingClaim);
      constraintMap.replace("id", referencingId);
      dataMap.replace("resource", referencingClaim);
      App.getDB().update(Table.CLAIM, constraintMap, dataMap);

      // Get the new referencing claim
      readConstraintMap.replace("related", referencingId);
      referencingClaim = (Claim) App.getDB().read(Table.CLAIM, readConstraintMap);
    }

    // Cascade the cancel to all related Claims...
    // Follow each related until it is NULL
    constraintMap.replace("id", claimId);
    String relatedId = App.getDB().readRelated(Table.CLAIM, constraintMap);
    Claim relatedClaim;

    while (relatedId != null) {
      // Update related claim to cancelled
      constraintMap.replace("id", relatedId);
      relatedClaim = (Claim) App.getDB().read(Table.CLAIM, constraintMap);
      relatedClaim.setStatus(ClaimStatus.CANCELLED);
      dataMap.replace("resource", relatedClaim);
      App.getDB().update(Table.CLAIM, constraintMap, dataMap);

      // Get the new related id from the db
      relatedId = App.getDB().readRelated(Table.CLAIM, constraintMap);
    }
  }

  /**
   * Schedule an update to the Claim to support pending actions.
   *
   * @param bundle      - the bundle containing the claim.
   * @param id          - the Claim ID.
   * @param patient     - the Patient ID.
   * @param disposition - the new disposition of the updated Claim.
   */
  protected void schedulePendedClaimUpdate(Bundle bundle, String id, String patient) {
    Timer timer = new Timer();
    pendedTimers.put(id, timer);
    timer.schedule(new UpdateClaimTask(bundle, id, patient), 30000); // 30s
  }

  /**
   * Cancels a timer for a specific id (key).
   * 
   * @param id - the id of the claim to cancel the timer for.
   * @return true if the timer was cancelled successfully, false otherwise.
   */
  private boolean cancelTimer(String id) {
    Timer timer = pendedTimers.get(id);
    if (timer != null) {
      timer.cancel();
      pendedTimers.remove(id);
      return true;
    }
    return false;
  }

}
