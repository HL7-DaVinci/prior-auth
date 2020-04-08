package org.hl7.davinci.priorauth;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.UUID;
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
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.Endpoint.RequestType;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Type;
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

  static final HashMap<String, Timer> pendedTimers = new HashMap<String, Timer>();

  @GetMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> readClaimJson(HttpServletRequest request,
      @RequestParam(name = "identifier", required = false) String id,
      @RequestParam(name = "patient.identifier") String patient,
      @RequestParam(name = "status", required = false) String status) {
    Map<String, Object> constraintMap = new HashMap<String, Object>();
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
    Map<String, Object> constraintMap = new HashMap<String, Object>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Table.CLAIM, constraintMap, request, RequestType.XML);
  }

  @DeleteMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> deleteClaimJson(@RequestParam(name = "identifier") String id,
      @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Table.CLAIM, RequestType.JSON);
  }

  @DeleteMapping(value = "", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> deleteClaimXml(@RequestParam(name = "identifier") String id,
      @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Table.CLAIM, RequestType.XML);
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

    String id = null;
    String patient = null;
    HttpStatus status = HttpStatus.BAD_REQUEST;
    String formattedData = null;
    try {
      IParser parser = requestType == RequestType.JSON ? App.FHIR_CTX.newJsonParser() : App.FHIR_CTX.newXmlParser();
      IBaseResource resource = parser.parseResource(body);
      if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        // TODO: I think this should use .getEntry().get(0) instead of
        // getEntryFirstRep()
        if (bundle.hasEntry() && (bundle.getEntry().size() >= 1) && bundle.getEntryFirstRep().hasResource()
            && bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.Claim) {
          Bundle responseBundle = processBundle(bundle);
          if (responseBundle == null) {
            // Failed processing bundle...
            OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, PROCESS_FAILED);
            formattedData = FhirUtils.getFormattedData(error, requestType);
            logger.severe("ClaimEndpoint::SubmitOperation:Failed to process Bundle:" + bundle.getId());
          } else {
            ClaimResponse response = FhirUtils.getClaimResponseFromResponseBundle(responseBundle);
            id = FhirUtils.getIdFromResource(response);
            patient = FhirUtils.getPatientIdentifierFromBundle(responseBundle);
            formattedData = FhirUtils.getFormattedData(responseBundle, requestType);
            status = HttpStatus.CREATED;
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
    }
    MediaType contentType = requestType == RequestType.JSON ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_XML;
    return ResponseEntity.status(status).contentType(contentType)
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
    Disposition responseDisposition = Disposition.UNKNOWN;
    ClaimResponseStatus responseStatus = ClaimResponseStatus.ACTIVE;

    if (status == ClaimStatus.CANCELLED) {
      // Cancel the claim...
      if (cancelClaim(FhirUtils.getIdFromResource(claim), patient)) {
        responseStatus = ClaimResponseStatus.CANCELLED;
        responseDisposition = Disposition.CANCELLED;
      } else
        return null;
    } else {
      // Store the claim...
      claim.setId(id);
      Map<String, Object> claimMap = new HashMap<String, Object>();
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
          Timer timer = pendedTimers.get(relatedId);
          if (timer != null) {
            timer.cancel();
            pendedTimers.remove(relatedId);
          }
        }
      }

      if (!App.getDB().write(Table.CLAIM, claimMap))
        return null;

      // Store the bundle...
      bundle.setId(id);
      Map<String, Object> bundleMap = new HashMap<String, Object>();
      bundleMap.put("id", id);
      bundleMap.put("patient", patient);
      bundleMap.put("resource", bundle);
      App.getDB().write(Table.BUNDLE, bundleMap);

      // Store the claim items...
      if (claim.hasItem()) {
        if (!processClaimItems(claim, id, relatedId)) {
          logger.severe("ClaimEndpoint::processBundle:unable to process claim items successfully");
          return null;
        }
      }

      responseDisposition = ClaimResponseFactory.determineDisposition(claim);
    }

    // Generate the claim response...
    Bundle responseBundle = ClaimResponseFactory.generateAndStoreClaimResponse(bundle, claim, id, responseDisposition,
        responseStatus, patient);

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
  private boolean processClaimItems(Claim claim, String id, String relatedId) {
    boolean ret = true;
    String claimStatusStr = FhirUtils.getStatusFromResource(claim);
    if (relatedId != null) {
      // Update the items...
      for (ItemComponent item : claim.getItem()) {
        boolean itemIsCancelled = false;
        if (item.hasModifierExtension()) {
          List<Extension> exts = item.getModifierExtension();
          for (Extension ext : exts) {
            if (ext.getUrl().equals(FhirUtils.ITEM_CANCELLED_EXTENSION_URL) && ext.hasValue()) {
              Type type = ext.getValue();
              itemIsCancelled = type.castToBoolean(type).booleanValue();
            }
          }
        }

        Map<String, Object> dataMap = new HashMap<String, Object>();
        Map<String, Object> constraintMap = new HashMap<String, Object>();
        constraintMap.put("id", relatedId);
        constraintMap.put("sequence", item.getSequence());
        dataMap.put("id", id);
        dataMap.put("sequence", item.getSequence());
        dataMap.put("status", itemIsCancelled ? ClaimStatus.CANCELLED.getDisplay().toLowerCase() : claimStatusStr);

        // Update if item exists otherwise add to database
        if (App.getDB().readStatus(Table.CLAIM_ITEM, constraintMap) == null) {
          App.getDB().write(Table.CLAIM_ITEM, dataMap);
        } else {
          if (!App.getDB().update(Table.CLAIM_ITEM, constraintMap, dataMap))
            ret = false;
        }
      }
    } else {
      // Add the claim items...
      for (ItemComponent item : claim.getItem()) {
        boolean itemIsCancelled = false;
        if (item.hasModifierExtension()) {
          List<Extension> exts = item.getModifierExtension();
          for (Extension ext : exts) {
            if (ext.getUrl().equals(FhirUtils.ITEM_CANCELLED_EXTENSION_URL) && ext.hasValue()) {
              Type type = ext.getValue();
              itemIsCancelled = type.castToBoolean(type).booleanValue();
            }
          }
        }

        Map<String, Object> itemMap = new HashMap<String, Object>();
        itemMap.put("id", id);
        itemMap.put("sequence", item.getSequence());
        itemMap.put("status", itemIsCancelled ? ClaimStatus.CANCELLED.getDisplay().toLowerCase() : claimStatusStr);
        if (!App.getDB().write(Table.CLAIM_ITEM, itemMap))
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
    Claim initialClaim = (Claim) App.getDB().read(Table.CLAIM, claimConstraintMap);
    if (initialClaim != null) {
      if (initialClaim.getStatus() != ClaimStatus.CANCELLED) {
        // Cancel the claim...
        initialClaim.setStatus(ClaimStatus.CANCELLED);
        Map<String, Object> dataMap = new HashMap<String, Object>();
        Map<String, Object> constraintMap = new HashMap<String, Object>();
        constraintMap.put("id", claimId);
        dataMap.put("status", ClaimStatus.CANCELLED.getDisplay().toLowerCase());
        dataMap.put("resource", initialClaim);
        App.getDB().update(Table.CLAIM, constraintMap, dataMap);

        cascadeCancel(claimId);

        // Cancel items...
        dataMap = new HashMap<String, Object>();
        constraintMap = new HashMap<String, Object>();
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
    Map<String, Object> dataMap = new HashMap<String, Object>();
    dataMap.put("status", ClaimStatus.CANCELLED.getDisplay().toLowerCase());
    dataMap.put("resource", null);
    Map<String, Object> constraintMap = new HashMap<String, Object>();
    constraintMap.put("id", null);

    // Cascade up to all the Claims submitted after this which reference this Claim
    Map<String, Object> readConstraintMap = new HashMap<String, Object>();
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
  private void schedulePendedClaimUpdate(Bundle bundle, String id, String patient) {
    Timer timer = new Timer();
    pendedTimers.put(id, timer);
    timer.schedule(new UpdateClaimTask(bundle, id, patient), 30000); // 30s
  }

}
