package org.hl7.davinci.priorauth;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.invoke.MethodHandles;

public class FhirUtils {

  static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /**
   * Internal function to get the correct status from a resource depending on the
   * type
   *
   * @param resource - the resource.
   * @return - the status of the resource.
   */
  public static String getStatusFromResource(IBaseResource resource) {
    String status;
    if (resource instanceof Claim) {
      Claim claim = (Claim) resource;
      status = claim.getStatus().getDisplay();
    } else if (resource instanceof ClaimResponse) {
      ClaimResponse claimResponse = (ClaimResponse) resource;
      status = claimResponse.getStatus().getDisplay();
    } else if (resource instanceof Bundle) {
      status = "valid";
    } else {
      status = "unkown";
    }
    status = status.toLowerCase();
    return status;
  }

  /**
   * Internal function to get the Patient ID from the Patient Reference.
   *
   * @param resource - the resource.
   * @return String - the Patient ID.
   */
  public static String getPatientIdFromResource(IBaseResource resource) {
    String patient = "";
    try {
      String patientReference = null;
      if (resource instanceof Claim) {
        Claim claim = (Claim) resource;
        patientReference = claim.getPatient().getReference();
      } else if (resource instanceof ClaimResponse) {
        ClaimResponse claimResponse = (ClaimResponse) resource;
        patientReference = claimResponse.getPatient().getReference();
      } else if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        Claim claim = (Claim) bundle.getEntryFirstRep().getResource();
        patient = FhirUtils.getPatientIdFromResource(claim);
      } else {
        return patient;
      }
      String[] patientParts = patientReference.split("/");
      patient = patientParts[patientParts.length - 1];
      logger.info("getPatientIdFromResource: patient: " + patientParts[patientParts.length - 1]);
    } catch (Exception e) {
      logger.error("getPatientIdFromResource: error processing patient: " + e.toString());
    }
    return patient;
  }

  /**
   * Find the first instance of a ClaimResponse in a bundle
   * 
   * @param bundle - the bundle search through for the ClaimResponse
   * @return ClaimResponse in the bundle or null if not found
   */
  public static ClaimResponse getClaimResponseFromBundle(Bundle bundle) {
    ClaimResponse claimResponse = null;
    for (BundleEntryComponent bec : bundle.getEntry()) {
      if (bec.getResource().getResourceType() == ResourceType.ClaimResponse)
        return (ClaimResponse) bec.getResource();
    }

    return claimResponse;
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
