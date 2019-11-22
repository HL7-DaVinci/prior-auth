package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TimerTask;
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
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.Endpoint.RequestType;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.davinci.priorauth.FhirUtils.ReviewAction;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Claim.RelatedClaimComponent;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.ClaimResponse.AdjudicationComponent;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;
import org.hl7.fhir.r4.model.ClaimResponse.Use;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.Subscription.SubscriptionChannelType;
import org.hl7.fhir.r4.model.Subscription.SubscriptionStatus;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Subscription;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.hl7.fhir.r4.model.Claim.ItemComponent;

import ca.uhn.fhir.parser.IParser;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;

/**
 * The Claim endpoint to READ, SEARCH for, and DELETE submitted claims.
 */
@CrossOrigin
@RestController
@RequestMapping("/Claim")
public class ClaimEndpoint {

  static final Logger logger = PALogger.getLogger();

  String REQUIRES_BUNDLE = "Prior Authorization Claim/$submit Operation requires a Bundle with a single Claim as the first entry and supporting resources.";
  String PROCESS_FAILED = "Unable to process the request properly. Check the log for more details.";
  String ITEM_REFERENCE_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemReference";
  String REVIEW_ACTION_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction";
  String REVIEW_ACTION_REASON_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewActionReason";

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
    HttpStatus status = HttpStatus.OK;
    String formattedData = null;
    try {
      IParser parser = requestType == RequestType.JSON ? App.FHIR_CTX.newJsonParser() : App.FHIR_CTX.newXmlParser();
      IBaseResource resource = parser.parseResource(body);
      if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        if (bundle.hasEntry() && (bundle.getEntry().size() > 1) && bundle.getEntryFirstRep().hasResource()
            && bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.Claim) {
          Bundle responseBundle = processBundle(bundle);
          if (responseBundle == null) {
            // Failed processing bundle...
            status = HttpStatus.BAD_REQUEST;
            OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, PROCESS_FAILED);
            formattedData = FhirUtils.getFormattedData(error, requestType);
          } else {
            ClaimResponse response = FhirUtils.getClaimResponseFromResponseBundle(responseBundle);
            id = FhirUtils.getIdFromResource(response);
            patient = FhirUtils.getPatientIdentifierFromBundle(responseBundle);
            formattedData = FhirUtils.getFormattedData(responseBundle, requestType);
          }
        } else {
          // Claim is required...
          status = HttpStatus.BAD_REQUEST;
          OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
          formattedData = FhirUtils.getFormattedData(error, requestType);
        }
      } else {
        // Bundle is required...
        status = HttpStatus.BAD_REQUEST;
        OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
        formattedData = FhirUtils.getFormattedData(error, requestType);
      }
    } catch (Exception e) {
      // The submission failed so spectacularly that we need
      // catch an exception and send back an error message...
      status = HttpStatus.BAD_REQUEST;
      OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
      formattedData = FhirUtils.getFormattedData(error, requestType);
    }
    MediaType contentType = requestType == RequestType.JSON ? MediaType.APPLICATION_JSON : MediaType.APPLICATION_XML;
    return ResponseEntity.status(status).contentType(contentType).header(HttpHeaders.LOCATION,
        App.getBaseUrl() + "ClaimResponse?identifier=" + id + "&patient.identifier=" + patient).body(formattedData);

  }

  /**
   * Process the $submit operation Bundle. Theoretically, this is where business
   * logic should be implemented or overridden.
   * 
   * @param bundle Bundle with a Claim followed by other required resources.
   * @return ClaimResponse with the result.
   */
  private Bundle processBundle(Bundle bundle) {
    logger.fine("ClaimEndpoint::processBundle");
    // Store the submission...
    // Generate a shared id...
    String id = UUID.randomUUID().toString();

    // get the patient
    Claim claim = FhirUtils.getClaimFromRequestBundle(bundle);
    String patient = FhirUtils.getPatientIdentifierFromBundle(bundle);
    if (patient == null) {
      logger.severe("ClaimEndpoint::processBundle:Patient was null");
      return null;
    }

    String claimId = id;
    Disposition responseDisposition = Disposition.UNKNOWN;
    ClaimResponseStatus responseStatus = ClaimResponseStatus.ACTIVE;
    ClaimStatus status = claim.getStatus();
    boolean delayedUpdate = false;
    Disposition delayedDisposition = Disposition.UNKNOWN;

    if (status == ClaimStatus.CANCELLED) {
      // Cancel the claim...
      claimId = FhirUtils.getIdFromResource(claim);
      if (cancelClaim(claimId, patient)) {
        responseStatus = ClaimResponseStatus.CANCELLED;
        responseDisposition = Disposition.CANCELLED;
      } else
        return null;
    } else {
      // Store the claim...
      claim.setId(id);
      String relatedId = null;
      String claimStatusStr = FhirUtils.getStatusFromResource(claim);
      Map<String, Object> claimMap = new HashMap<String, Object>();
      claimMap.put("id", id);
      claimMap.put("patient", patient);
      claimMap.put("status", claimStatusStr);
      claimMap.put("resource", claim);
      RelatedClaimComponent related = getRelatedComponent(claim);
      if (related != null) {
        // This is an update...
        relatedId = related.getIdElement().asStringValue();
        relatedId = App.getDB().getMostRecentId(relatedId);
        logger.info("ClaimEndpoint::Udpated id to most recent: " + relatedId);
        claimMap.put("related", relatedId);

        // Check if related is cancelled in the DB
        Map<String, Object> constraintMap = new HashMap<String, Object>();
        constraintMap.put("id", relatedId);
        String relatedStatusStr = App.getDB().readStatus(Table.CLAIM, constraintMap);
        ClaimStatus relatedStatus = ClaimStatus.fromCode(relatedStatusStr);
        if (relatedStatus == Claim.ClaimStatus.CANCELLED) {
          logger.warning(
              "ClaimEndpoint::Unable to submit update to claim " + relatedId + " because it has been cancelled");
          return null;
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
        processClaimItems(claim, id, relatedId);
      }

      // Generate random responses since not cancelling
      // with a 4 in 6 chance of being pending
      switch (getRand(6)) {
      case 1:
      case 2:
      case 3:
        responseDisposition = Disposition.PENDING;

        switch (getRand(2)) {
        case 1:
          delayedDisposition = Disposition.GRANTED;
          break;
        case 2:
          delayedDisposition = Disposition.DENIED;
          break;
        }

        delayedUpdate = true;
        break;
      case 4:
        // We can only have partial disposition when there are
        // more than 2 items in the Claim
        if (claim.hasItem() && claim.getItem().size() >= 2)
          responseDisposition = Disposition.PARTIAL;
        else
          responseDisposition = Disposition.GRANTED;
        break;
      case 5:
        responseDisposition = Disposition.GRANTED;
        break;
      case 6:
      default:
        responseDisposition = Disposition.DENIED;
        break;
      }
    }
    // Process the claim...
    // TODO

    // Generate the claim response...
    Bundle responseBundle = generateAndStoreClaimResponse(bundle, claim, id, responseDisposition, responseStatus,
        patient);

    if (delayedUpdate) {
      // schedule the update
      scheduleClaimUpdate(bundle, id, patient, delayedDisposition);
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
        String extUrl = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemCancelled";
        if (item.hasModifierExtension()) {
          List<Extension> exts = item.getModifierExtension();
          for (Extension ext : exts) {
            if (ext.getUrl().equals(extUrl) && ext.hasValue()) {
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
        Map<String, Object> itemMap = new HashMap<String, Object>();
        itemMap.put("id", id);
        itemMap.put("sequence", item.getSequence());
        itemMap.put("status", claimStatusStr);
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
   * Update the claim and generate a new ClaimResponse.
   * 
   * @param bundle      - the Bundle the Claim is a part of.
   * @param claimId     - the Claim ID.
   * @param patient     - the Patient ID.
   * @param disposition - the new disposition of the updated Claim.
   */
  private Bundle updateClaim(Bundle bundle, String claimId, String patient, Disposition disposition) {
    logger
        .info("ClaimEndpoint::updateClaim(" + claimId + "/" + patient + ", disposition: " + disposition.value() + ")");

    // Generate a new id...
    String id = UUID.randomUUID().toString();

    // Get the claim from the database
    Map<String, Object> claimConstraintMap = new HashMap<String, Object>();
    claimConstraintMap.put("id", claimId);
    claimConstraintMap.put("patient", patient);
    Claim claim = (Claim) App.getDB().read(Table.CLAIM, claimConstraintMap);
    if (claim != null)
      return generateAndStoreClaimResponse(bundle, claim, id, Disposition.GRANTED, ClaimResponseStatus.ACTIVE, patient);
    else
      return null;
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
   * @param bundle              The original bundle submitted to the server
   *                            requesting priorauthorization.
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
  private Bundle generateAndStoreClaimResponse(Bundle bundle, Claim claim, String id, Disposition responseDisposition,
      ClaimResponseStatus responseStatus, String patient) {
    logger.info("ClaimEndpoint::generateAndStoreClaimResponse(" + id + "/" + patient + ", disposition: "
        + responseDisposition + ", status: " + responseStatus + ")");

    // Generate the claim response...
    ClaimResponse response = new ClaimResponse();
    String claimId = App.getDB().getMostRecentId(FhirUtils.getIdFromResource(claim));
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
    response.setRequest(new Reference(App.getBaseUrl() + "Claim?identifier=" + FhirUtils.getIdFromResource(claim)
        + "&patient.identifier=" + patient));
    if (responseDisposition == Disposition.PENDING) {
      response.setOutcome(RemittanceOutcome.QUEUED);
    } else if (responseDisposition == Disposition.PARTIAL) {
      response.setOutcome(RemittanceOutcome.PARTIAL);
    } else {
      response.setOutcome(RemittanceOutcome.COMPLETE);
    }
    response.setItem(setClaimResponseItems(claim, responseDisposition));
    response.setDisposition(responseDisposition.value());
    response.setPreAuthRef(id);
    // TODO response.setPreAuthPeriod(period)?
    response.setId(id);

    response.addExtension(REVIEW_ACTION_EXTENSION_URL,
        FhirUtils.dispositionToReviewAction(responseDisposition).value());

    if (responseDisposition == Disposition.GRANTED || responseDisposition == Disposition.PARTIAL) {
      Identifier identifier = new Identifier();
      identifier.setSystem(App.getBaseUrl());
      identifier.setValue(id);
      response.addIdentifier(identifier);
    } else if (responseDisposition == Disposition.DENIED || responseDisposition == Disposition.PENDING) {
      response.addExtension(REVIEW_ACTION_REASON_EXTENSION_URL, new StringType("X"));
    }

    // Create the responseBundle
    Bundle responseBundle = new Bundle();
    responseBundle.setId(id);
    responseBundle.setType(Bundle.BundleType.COLLECTION);
    BundleEntryComponent responseEntry = responseBundle.addEntry();
    responseEntry.setResource(response);
    responseEntry.setFullUrl(App.getBaseUrl() + "/ClaimResponse/" + id);
    for (BundleEntryComponent entry : bundle.getEntry()) {
      responseBundle.addEntry(entry);
    }

    // Store the claim respnose...
    Map<String, Object> responseMap = new HashMap<String, Object>();
    responseMap.put("id", id);
    responseMap.put("claimId", claimId);
    responseMap.put("patient", patient);
    responseMap.put("status", FhirUtils.getStatusFromResource(response));
    responseMap.put("outcome", FhirUtils.dispositionToReviewAction(responseDisposition).value());
    responseMap.put("resource", responseBundle);
    App.getDB().write(Table.CLAIM_RESPONSE, responseMap);

    return responseBundle;
  }

  /**
   * Set the item for a ClaimResponse ItemComponent based on the submitted item
   * and the outcome
   * 
   * @param item   - the ItemComponent from the Claim
   * @param action - ReviewAction representing the outcome of the claim item
   * @param id     - the ClaimResponse ID (used to set the identifier system)
   * @return ClaimResponse ItemComponent with appropriate elements set
   */
  private ClaimResponse.ItemComponent createItemComponent(ItemComponent item, ReviewAction action, String id) {
    ClaimResponse.ItemComponent itemComponent = new ClaimResponse.ItemComponent();
    itemComponent.setItemSequence(item.getSequence());

    // Add the adjudication
    Coding adjudicationCoding = new Coding();
    adjudicationCoding.setCode("eligpercent");
    adjudicationCoding.setDisplay("Eligible %");
    adjudicationCoding.setSystem("http://terminology.hl7.org/CodeSystem/adjudication");
    CodeableConcept category = new CodeableConcept(adjudicationCoding);
    AdjudicationComponent adjudication = new AdjudicationComponent(category);
    Double adjudicationValue = action == ReviewAction.APPROVED ? 1.0 : 0.0;
    adjudication.setValue(adjudicationValue);
    itemComponent.addAdjudication(adjudication);

    if (action == ReviewAction.APPROVED) {
      // Add identifier for X12 mapping
      Identifier itemIdentifier = new Identifier();
      itemIdentifier.setSystem(App.getBaseUrl() + "/ClaimResponse/" + id);
      itemIdentifier.setValue(Integer.toString(item.getSequence()));
      itemComponent.addExtension(ITEM_REFERENCE_EXTENSION_URL, itemIdentifier);
    } else if (action == ReviewAction.DENIED || action == ReviewAction.PENDED)
      itemComponent.addExtension(REVIEW_ACTION_REASON_EXTENSION_URL, new StringType("X"));

    Extension reviewActionExtension = new Extension(REVIEW_ACTION_EXTENSION_URL);
    reviewActionExtension.setValue(action.value());
    itemComponent.addExtension(reviewActionExtension);

    return itemComponent;
  }

  /**
   * Set the Items on the ClaimResponse indicating the adjudication of each one
   * 
   * @param claim               - the initial Claim which contains the items
   * @param responseDisposition - the overall disposition
   * @return a list of ItemComponents to be added to the ClaimResponse.items field
   */
  private List<ClaimResponse.ItemComponent> setClaimResponseItems(Claim claim, Disposition responseDisposition) {
    List<ClaimResponse.ItemComponent> items = new ArrayList<ClaimResponse.ItemComponent>();
    // ReviewAction reviewAction = ReviewAction.DENIED;
    ReviewAction reviewAction = null;
    boolean hasDeniedAtLeastOne = false;
    boolean hasApprovedAtLeastOne = false;

    // Set the Items on the ClaimResponse based on the initial Claim and the
    // Response disposition
    for (ItemComponent item : claim.getItem()) {
      if (responseDisposition == Disposition.GRANTED) {
        reviewAction = ReviewAction.APPROVED;
      } else if (responseDisposition == Disposition.DENIED) {
        reviewAction = ReviewAction.DENIED;
      } else if (responseDisposition == Disposition.PENDING) {
        reviewAction = ReviewAction.PENDED;
      } else if (responseDisposition == Disposition.PARTIAL) {
        // Deny some and approve some
        if (!hasDeniedAtLeastOne) {
          reviewAction = ReviewAction.DENIED;
          hasDeniedAtLeastOne = true;
        } else if (!hasApprovedAtLeastOne) {
          reviewAction = ReviewAction.APPROVED;
          hasApprovedAtLeastOne = true;
        } else {
          switch (getRand(2)) {
          case 1:
            reviewAction = ReviewAction.DENIED;
            break;
          case 2:
          default:
            reviewAction = ReviewAction.APPROVED;
            break;
          }
        }
      }

      ClaimResponse.ItemComponent itemComponent = createItemComponent(item, reviewAction,
          FhirUtils.getIdFromResource(claim));
      items.add(itemComponent);

      // Update the ClaimItem database
      Map<String, Object> constraintMap = new HashMap<String, Object>();
      constraintMap.put("id", FhirUtils.getIdFromResource(claim));
      constraintMap.put("sequence", item.getSequence());
      App.getDB().update(Table.CLAIM_ITEM, constraintMap, Collections.singletonMap("outcome", reviewAction.value()));
    }
    return items;
  }

  /**
   * A TimerTask for updating claims.
   */
  class UpdateClaimTask extends TimerTask {
    public Bundle bundle;
    public String claimId;
    public String patient;
    public Disposition disposition;

    UpdateClaimTask(Bundle bundle, String claimId, String patient, Disposition disposition) {
      this.bundle = bundle;
      this.claimId = claimId;
      this.patient = patient;
      this.disposition = disposition;
    }

    @Override
    public void run() {
      if (updateClaim(bundle, claimId, patient, disposition) != null) {
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
                  .newCall(new Request.Builder().post(RequestBody.create(null, "")).url(endpoint).build()).execute();
              logger.fine("SubscriptionHandler::Response " + response.code());
              App.getDB().update(Table.SUBSCRIPTION, Collections.singletonMap("id", subscriptionId),
                  Collections.singletonMap("status", SubscriptionStatus.ACTIVE.getDisplay().toLowerCase()));
            } catch (IOException e) {
              logger.log(Level.SEVERE, "SubscriptionHandler::IOException in request", e);
              App.getDB().update(Table.SUBSCRIPTION, Collections.singletonMap("id", subscriptionId),
                  Collections.singletonMap("status", SubscriptionStatus.ERROR.getDisplay().toLowerCase()));
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
                  Collections.singletonMap("status", SubscriptionStatus.ACTIVE.getDisplay().toLowerCase()));
            } else {
              logger.severe("SubscriptionHandler::Unable to send web-socket notification for subscription "
                  + subscriptionId + " because web-socket id is null. Client did not bind a websocket to id");
              App.getDB().update(Table.SUBSCRIPTION, Collections.singletonMap("id", subscriptionId),
                  Collections.singletonMap("status", SubscriptionStatus.ERROR.getDisplay().toLowerCase()));
            }
          }
        });
      }
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
  private void scheduleClaimUpdate(Bundle bundle, String id, String patient, Disposition disposition) {
    App.timer.schedule(new UpdateClaimTask(bundle, id, patient, disposition), 30000); // 30s
  }

  /**
   * Get a random number between 1 and max.
   *
   * @param max The largest the random number could be.
   * @return int representing the random number.
   */
  private int getRand(int max) {
    Date date = new Date();
    return (int) ((date.getTime() % max) + 1);
  }

}
