package org.hl7.davinci.priorauth;

import java.util.Collections;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.Endpoint.RequestType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.hl7.fhir.r4.model.Claim.RelatedClaimComponent;
import org.hl7.fhir.r4.model.Subscription.SubscriptionStatus;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class FhirUtils {

  static final Logger logger = PALogger.getLogger();

  // FHIR Extension URLS
  public static final String ITEM_REFERENCE_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemReference";
  public static final String ITEM_CANCELLED_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemCancelled";
  public static final String ITEM_CHANGED_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-infoChanged";
  public static final String REVIEW_ACTION_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction";
  public static final String REVIEW_ACTION_REASON_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewActionReason";
  public static final String WEBSOCKET_EXTENSION_URL = "http://hl7.org/fhir/StructureDefinition/capabilitystatement-websocket";
  public static final String SECURITY_SYSTEM_URL = "http://terminology.hl7.org/CodeSystem/v3-ObservationValue";
  public static final String SECURITY_SUBSETTED = "SUBSETTED";

  /**
   * Enum for the ClaimResponse Disposition field Values are Granted, Denied,
   * Partial, Pending, Cancelled, and Unknown
   */
  public enum Disposition {
    GRANTED("Granted"), DENIED("Denied"), PARTIAL("Partial"), PENDING("Pending"), CANCELLED("Cancelled"),
    UNKNOWN("Unknown");

    private final String value;

    Disposition(String value) {
      this.value = value;
    }

    public String value() {
      return this.value;
    }
  }

  /**
   * Enum for the ClaimResponse.item reviewAction extensions used for X12 HCR01
   * Responde Code. Codes taken from X12 and CMS
   * http://www.x12.org/x12org/subcommittees/X12N/N0210_4010MultProcedures.pdf
   * https://www.cms.gov/Research-Statistics-Data-and-Systems/Computer-Data-and-Systems/ESMD/Downloads/esMD_X12_278_09_2016Companion_Guide.pdf
   */
  public enum ReviewAction {
    APPROVED("A1"), PARTIAL("A2"), DENIED("A3"), PENDED("A4"), CANCELLED("A6");

    private final String value;

    ReviewAction(String value) {
      this.value = value;
    }

    public StringType value() {
      return new StringType(this.value);
    }

    public String asStringValue() {
      return this.value;
    }

    public static ReviewAction fromString(String value) {
      for (ReviewAction reviewAction : ReviewAction.values()) {
        if (reviewAction.asStringValue().equals(value))
          return reviewAction;
      }

      return null;
    }
  }

  /**
   * Internal function to get the correct status from a resource depending on the
   * type
   *
   * @param resource - the resource.
   * @return - the status of the resource.
   */
  public static String getStatusFromResource(IBaseResource resource) {
    String status = "unknown";
    String resourceString = FhirUtils.json(resource);
    try {
      JSONObject resourceJSON = (JSONObject) new JSONParser().parse(resourceString);
      if (resourceJSON.containsKey("status"))
        status = (String) resourceJSON.get("status");
    } catch (ParseException e) {
      logger.log(Level.SEVERE, "FhirUtils::getStatusFromResource:Unable to parse JSON", e);
    }

    return status.toLowerCase();
  }

  /**
   * Internal function to get the Patient identifier from the Patient Reference.
   *
   * @param resource - the resource.
   * @return String - the Patient identifier, the ID if it does not exist or null
   */
  public static String getPatientIdentifierFromBundle(Bundle bundle) {
    Reference patientReference = null;

    // If Response Bundle get the ClaimResponse
    ClaimResponse claimResponse = getClaimResponseFromResponseBundle(bundle);
    if (claimResponse != null)
      patientReference = claimResponse.getPatient();

    // If Request Bundle get the Claim
    Claim claim = getClaimFromRequestBundle(bundle);
    if (claim != null)
      patientReference = claim.getPatient();

    // Obtain the identifier based on how the reference is defined
    if (patientReference.hasReference()) {
      // Get the patient through the reference
      String reference = patientReference.getReference();
      logger.info("FhirUtils::getPatientIdentifier:patientReference:" + reference);
      String[] referenceParts = reference.split("/");
      String patientId = referenceParts[referenceParts.length - 1];
      logger.info("FhirUtils::getPatientIdentifier:patientId:" + patientId);

      // Get the patient resource with the matching id
      BundleEntryComponent bec = getEntryComponentFromBundle(bundle, ResourceType.Patient, patientId);
      if (bec != null) {
        Patient patient = (Patient) bec.getResource();
        logger.info("FhirUtils::getPatientIdentifier:foundPatient:" + FhirUtils.getIdFromResource(patient));
        if (patient.hasIdentifier())
          return patient.getIdentifierFirstRep().getValue();

        logger.info("FhirUtils::getPatientIdentifierFromBundle:Patient found but has no identifier. Using ID instead");
        // TODO: This is a temporary fix so the result is not null. The IG should be
        // updated to explain what to do here
        return patientId;
      } else if (isDifferential(bundle)) {
        // Differential update so the patient will be in original bundle
        logger.info(
            "FhirUtils::getPatientIdentifierFromBundle:Traversing update chain for patient:Bundle/" + bundle.getId());
        String relatedId = getRelatedComponentId(getClaimFromRequestBundle(bundle));
        logger.fine("FhirUtils::getPatientIdentifierFromBundle:Found related ID:" + relatedId);
        if (relatedId != null) {
          Bundle relatedBundle = (Bundle) App.getDB().read(Table.BUNDLE, Collections.singletonMap("id", relatedId));
          return getPatientIdentifierFromBundle(relatedBundle);
        }
      }
      logger.severe("FhirUtils::getPatientIdentifierFromBundle:Patient with given id not found in Bundle");

      // If could not find the resource locally or has no identifier set null
      return null;
    } else if (patientReference.hasIdentifier())
      return patientReference.getIdentifier().getValue();
    else
      return null;
  }

  /**
   * Convert the response disposition into a review action
   * 
   * @param disposition - the response disposition
   * @return corresponding ReviewAction for the Disposition
   */
  public static ReviewAction dispositionToReviewAction(Disposition disposition) {
    if (disposition == Disposition.DENIED)
      return ReviewAction.DENIED;
    else if (disposition == Disposition.GRANTED)
      return ReviewAction.APPROVED;
    else if (disposition == Disposition.PARTIAL)
      return ReviewAction.PARTIAL;
    else if (disposition == Disposition.PENDING)
      return ReviewAction.PENDED;
    else if (disposition == Disposition.CANCELLED)
      return ReviewAction.CANCELLED;
    else
      return null;
  }

  /**
   * Get the ClaimResponse from a PAS ClaimResponse Bundle. ClaimResponse is the
   * first entry
   * 
   * @param bundle - PAS Claim Response Bundle
   * @return ClaimResponse resource for the response
   */
  public static ClaimResponse getClaimResponseFromResponseBundle(Bundle bundle) {
    Resource resource = bundle.getEntry().get(0).getResource();
    if (resource.getResourceType() == ResourceType.ClaimResponse)
      return (ClaimResponse) resource;
    else
      return null;
  }

  /**
   * Get the Claim from a PAS Claim Bundle. Claim is the first entry
   * 
   * @param bundle - PAS Claim Request Bundle
   * @return Claim resource for the request
   */
  public static Claim getClaimFromRequestBundle(Bundle bundle) {
    Resource resource = bundle.getEntry().get(0).getResource();
    if (resource.getResourceType() == ResourceType.Claim)
      return (Claim) resource;
    else
      return null;
  }

  /**
   * Find the BundleEntryComponent in a Bundle where the resource has the desired
   * id
   * 
   * @param bundle       - the bundle to search through
   * @param resourceType - the resource type to look for (since ids are not
   *                     unique)
   * @param id           - the resource id to match
   * @return BundleEntryComponent in Bundle with resource matching id
   */
  public static BundleEntryComponent getEntryComponentFromBundle(Bundle bundle, ResourceType resourceType, String id) {
    for (BundleEntryComponent entry : bundle.getEntry()) {
      Resource resource = entry.getResource();
      if (resource.getResourceType() == resourceType && FhirUtils.getIdFromResource(resource).equals(id)) {
        return entry;
      }
    }
    return null;
  }

  /**
   * Get the id of the related claim for an update to a claim (replaces
   * relationship)
   * 
   * @param claim - the base Claim resource.
   * @return the id of the first related claim with relationship "replaces" or
   *         null if no matching related resource.
   */
  public static String getRelatedComponentId(Claim claim) {
    if (claim.hasRelated()) {
      for (RelatedClaimComponent relatedComponent : claim.getRelated()) {
        if (relatedComponent.hasRelationship()) {
          for (Coding code : relatedComponent.getRelationship().getCoding()) {
            if (code.getCode().equals("replaces")) {
              // This claim is an update to an old claim
              return relatedComponent.getId();
            }
          }
        }
      }
    }
    return null;
  }

  /**
   * Return the id of a resource
   * 
   * @param resource - the resource to get the id from
   * @return the id of the resource
   */
  public static String getIdFromResource(IBaseResource resource) {
    if (resource.getIdElement().hasIdPart())
      return resource.getIdElement().getIdPart();
    return null;
  }

  /**
   * Returns true if element with id in table has status cancelled
   * 
   * @param table - Table in DB to read status from
   * @param id    - the id of the row to read
   * @return true if the status column of row given by id is "cancelled"
   */
  public static Boolean isCancelled(Table table, String id) {
    String status = App.getDB().readStatus(table, Collections.singletonMap("id", id));
    logger.fine("FhirUtils::isCancelled:" + status);
    return status.equals("cancelled");
  }

  /**
   * Returns true if Claim with the given id is pended
   * 
   * @param id - the id of the claim to read.
   * @return true if the Claim is pended, false otherwise.
   */
  public static Boolean isPended(String id) {
    id = App.getDB().getMostRecentId(id);

    String outcome = App.getDB().readString(Table.CLAIM_RESPONSE, Collections.singletonMap("claimId", id), "outcome");
    logger.fine("FhirUtils::isPended:Outcome " + outcome);

    return outcome != null ? outcome.equals(ReviewAction.PENDED.asStringValue()) : false;
  }

  /**
   * Convert a FHIR resource into JSON.
   * 
   * @param resource - the resource to convert to JSON.
   * @return String - the JSON.
   */
  public static String json(IBaseResource resource) {
    String json = App.FHIR_CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
    return json;
  }

  /**
   * Convert a FHIR resource into XML.
   * 
   * @param resource - the resource to convert to XML.
   * @return String - the XML.
   */
  public static String xml(IBaseResource resource) {
    String xml = App.FHIR_CTX.newXmlParser().setPrettyPrint(true).encodeResourceToString(resource);
    return xml;
  }

  /**
   * Format the resource status in a standard way for the database
   * 
   * @param status - the status
   * @return standard string representation of the status
   */
  public static String formatResourceStatus(Object status) {
    if (status instanceof ClaimStatus)
      return ((ClaimStatus) status).getDisplay().toLowerCase();
    else if (status instanceof SubscriptionStatus)
      return ((SubscriptionStatus) status).getDisplay().toLowerCase();
    return "error";
  }

  /**
   * Format a resource into JSON or XML string
   * 
   * @param resource    - the resource to convert
   * @param requestType - the type to represent it as
   * @return JSON or XML string representation of the resource
   */
  public static String getFormattedData(IBaseResource resource, RequestType requestType) {
    return requestType == RequestType.JSON ? FhirUtils.json(resource) : FhirUtils.xml(resource);
  }

  /**
   * Create a FHIR OperationOutcome.
   *
   * @param severity The severity of the result.
   * @param type     The issue type.
   * @param message  The message to return.
   * @return OperationOutcome - the FHIR resource.
   */
  public static OperationOutcome buildOutcome(OperationOutcome.IssueSeverity severity, OperationOutcome.IssueType type,
      String message) {
    OperationOutcome error = new OperationOutcome();
    OperationOutcome.OperationOutcomeIssueComponent issue = error.addIssue();
    issue.setSeverity(severity);
    issue.setCode(type);
    issue.setDiagnostics(message);
    return error;
  }

  /**
   * Get a random number between 1 and max.
   *
   * @param max The largest the random number could be.
   * @return int representing the random number.
   */
  public static int getRand(int max) {
    Date date = new Date();
    return (int) ((date.getTime() % max) + 1);
  }

  /**
   * Determines whether or not the PAS request is differential or complete.
   * Wrapper around securityIsSubsetted for readability.
   * 
   * @param bundle - the bundle resource.
   * @return true if the bundle is a differential request and false otherwise.
   */
  public static boolean isDifferential(Bundle bundle) {
    return FhirUtils.securityIsSubsetted(bundle);
  }

  /**
   * Internal function to determine whether the security tag is SUBSETTED or not
   *
   * @param bundle - the resource.
   * @return true if the security tag is SUBSETTED and false otherwise
   */
  private static boolean securityIsSubsetted(Bundle bundle) {
    // Using a loop since bundle.getMeta().getSecurity(SYSTEM, CODE) returns null
    for (Coding coding : bundle.getMeta().getSecurity()) {
      logger.info("FhirUtils::Security:" + coding.getCode());
      if (coding.getSystem().equals(SECURITY_SYSTEM_URL) && coding.getCode().equals(SECURITY_SUBSETTED)) {
        logger.info("FhirUtils::securityIsSubsetted:true");
        return true;
      }
    }
    return false;
  }
}
