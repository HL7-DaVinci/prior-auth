package org.hl7.davinci.priorauth;

import java.util.logging.Level;
import java.util.logging.Logger;

import org.hl7.davinci.priorauth.Endpoint.RequestType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.hl7.fhir.r4.model.Subscription.SubscriptionStatus;
import org.hl7.fhir.r4.model.Extension;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

public class FhirUtils {

  static final Logger logger = PALogger.getLogger();

  /**
   * Enum for the ClaimResponse Disposition field Values are Granted, Denied,
   * Partial, Pending, and Cancelled
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
   * Get the review action extension value from a ClaimResponse
   * 
   * @param claimResponse - the ClaimResponse resource
   * @return the X12 review action value if it exsits, otherwise null
   */
  public static String getReviewActionFromClaimResponse(ClaimResponse claimResponse) {
    String reviewActionUrl = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction";
    for (Extension ext : claimResponse.getExtension()) {
      if (ext.getUrl().equals(reviewActionUrl)) {
        return ext.getValue().primitiveValue();
      }
    }
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
    if (bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.ClaimResponse)
      return (ClaimResponse) bundle.getEntryFirstRep().getResource();
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
    if (bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.Claim)
      return (Claim) bundle.getEntryFirstRep().getResource();
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
}
