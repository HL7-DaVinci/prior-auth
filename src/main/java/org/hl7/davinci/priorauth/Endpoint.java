package org.hl7.davinci.priorauth;

import java.util.Map;
import java.util.logging.Logger;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public class Endpoint {

    static final Logger logger = PALogger.getLogger();

    public enum RequestType {
        XML, JSON
    }

    static String REQUIRES_ID = "Instance ID is required: DELETE {resourceType}?identifier=";
    static String REQUIRES_PATIENT = "Patient Identifier is required: DELETE {resourceType}?patient.identifier=";
    static String DELETED_MSG = "Deleted resource and all related and referenced resources.";

    /**
     * Read a resource from an endpoint in either JSON or XML
     * 
     * @param resourceType  - the FHIR resourceType to read.
     * @param constraintMap - map of the column names and values for the SQL query.
     * @param uri           - the base URI for the microservice.
     * @param requestType   - the RequestType of the request.
     * @return the desired resource if successful and an error message otherwise
     */
    public static ResponseEntity<String> read(String resourceType, Map<String, Object> constraintMap, String uri,
            RequestType requestType) {
        logger.info("GET /" + resourceType + ":" + constraintMap.toString() + " fhir+" + requestType.name());
        if (!constraintMap.containsKey("patient") || constraintMap.get("patient") == null) {
            logger.warning("Endpoint::read:patient null");
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        String formattedData = null;
        if ((!constraintMap.containsKey("id") || constraintMap.get("id") == null)
                && (!constraintMap.containsKey("claimId") || constraintMap.get("claimId") == null)) {
            // Search
            App.getDB().setBaseUrl(uri);
            constraintMap.remove("id");
            Bundle searchBundle;
            searchBundle = App.getDB().search(resourceType, constraintMap);
            formattedData = requestType == RequestType.JSON ? App.getDB().json(searchBundle)
                    : App.getDB().xml(searchBundle);
        } else {
            // Read
            IBaseResource baseResource;
            baseResource = App.getDB().read(resourceType, constraintMap);

            if (baseResource == null)
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

            // Convert to correct resourceType
            if (resourceType == Database.BUNDLE) {
                Bundle bundle = (Bundle) baseResource;
                formattedData = requestType == RequestType.JSON ? App.getDB().json(bundle) : App.getDB().xml(bundle);
            } else if (resourceType == Database.CLAIM) {
                Claim claim = (Claim) baseResource;
                formattedData = requestType == RequestType.JSON ? App.getDB().json(claim) : App.getDB().xml(claim);
            } else if (resourceType == Database.CLAIM_RESPONSE) {
                Bundle bundleResponse = (Bundle) baseResource;
                formattedData = requestType == RequestType.JSON ? App.getDB().json(bundleResponse)
                        : App.getDB().xml(bundleResponse);
            } else {
                logger.warning("Endpoint::read:invalid resourceType: " + resourceType);
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }
        return new ResponseEntity<>(formattedData, HttpStatus.OK);
    }

    /**
     * Read a resource from an endpoint in either JSON or XML
     * 
     * @param id           - the ID of the resource.
     * @param patient      - the patient ID.
     * @param resourceType - the FHIR resourceType to read.
     * @param requestType  - the RequestType of the request.
     * @return status of the deleted resource
     */
    public static ResponseEntity<String> delete(String id, String patient, String resourceType,
            RequestType requestType) {
        logger.info("DELETE /" + resourceType + ":" + id + "/" + patient + " fhir+" + requestType.name());
        HttpStatus status = HttpStatus.OK;
        OperationOutcome outcome = null;
        if (id == null) {
            // Do not delete everything
            // ID is required...
            status = HttpStatus.BAD_REQUEST;
            outcome = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_ID);
        } else if (patient == null) {
            // Do not delete everything
            // Patient ID is required...
            status = HttpStatus.BAD_REQUEST;
            outcome = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_PATIENT);
        } else {
            // Cascading delete
            App.getDB().delete(Database.BUNDLE, id, patient);
            App.getDB().delete(Database.CLAIM, id, patient);
            App.getDB().delete(Database.CLAIM_RESPONSE, id, patient);
            outcome = FhirUtils.buildOutcome(IssueSeverity.INFORMATION, IssueType.DELETED, DELETED_MSG);
        }
        String formattedData = requestType == RequestType.JSON ? App.getDB().json(outcome) : App.getDB().xml(outcome);
        return new ResponseEntity<>(formattedData, status);
    }
}