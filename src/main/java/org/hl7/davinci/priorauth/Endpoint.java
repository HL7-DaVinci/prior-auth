package org.hl7.davinci.priorauth;

import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Endpoint {

    static final Logger logger = LoggerFactory.getLogger(Endpoint.class);

    static final Boolean requestXml = false;
    static final Boolean requestJson = true;

    static String REQUIRES_ID = "Instance ID is required: DELETE ClaimResponse?identifier=";
    static String REQUIRES_PATIENT = "Patient Identifier is required: DELETE ClaimResponse?patient.identifier=";
    static String DELETED_MSG = "Deleted ClaimResponse and all related and referenced resources.";

    /**
     * Read a resource from an endpoint in either JSON or XML
     * 
     * @param id            - the ID of the resource.
     * @param patient       - the patient ID.
     * @param status        - the current status.
     * @param resourceType  - the FHIR resourceType to read.
     * @param uri           - the base URI for the microservice.
     * @param isJsonRequest - true if the request is to return JSON and false for
     *                      XML
     * @return the desired resource if successful and an error message otherwise
     */
    public static Response read(String id, String patient, String status, String resourceType, UriInfo uri,
            Boolean isJsonRequest) {
        logger.info("GET /" + resourceType + ":" + id + "/" + patient + "/" + status + " fhir+json: "
                + isJsonRequest.toString());
        if (patient == null) {
            logger.info("patient null");
            return Response.status(Status.UNAUTHORIZED).build();
        }
        String formattedData = null;
        if (id == null) {
            // Search
            App.DB.setBaseUrl(uri.getBaseUri());
            Bundle searchBundle;
            if (status == null) {
                searchBundle = App.DB.search(resourceType, patient);
            } else {
                searchBundle = App.DB.search(resourceType, patient, status);
            }
            formattedData = isJsonRequest ? App.DB.json(searchBundle) : App.DB.xml(searchBundle);
        } else {
            // Read
            IBaseResource baseResource;
            if (status == null)
                baseResource = App.DB.read(resourceType, id, patient);
            else
                baseResource = App.DB.read(resourceType, id, patient, status);

            if (baseResource == null)
                return Response.status(Status.NOT_FOUND).build();

            // Convert to correct resourceType
            if (resourceType == Database.BUNDLE) {
                Bundle bundle = (Bundle) baseResource;
                formattedData = isJsonRequest ? App.DB.json(bundle) : App.DB.xml(bundle);
            } else if (resourceType == Database.CLAIM) {
                Claim claim = (Claim) baseResource;
                formattedData = isJsonRequest ? App.DB.json(claim) : App.DB.xml(claim);
            } else if (resourceType == Database.CLAIM_RESPONSE) {
                ClaimResponse claimResponse = (ClaimResponse) baseResource;
                formattedData = isJsonRequest ? App.DB.json(claimResponse) : App.DB.xml(claimResponse);
            } else {
                logger.info("invalid resourceType: " + resourceType);
                return Response.status(Status.BAD_REQUEST).build();
            }
        }
        return Response.ok(formattedData).build();
    }

    /**
     * Read a resource from an endpoint in either JSON or XML
     * 
     * @param id            - the ID of the resource.
     * @param patient       - the patient ID.
     * @param resourceType  - the FHIR resourceType to read.
     * @param isJsonRequest - true if the request is to return JSON and false for
     *                      XML
     * @return status of the deleted resource
     */
    public static Response delete(String id, String patient, String resourceType, Boolean isJsonRequest) {
        logger.info("DELETE /" + resourceType + ":" + id + "/" + patient + " fhir+json: " + isJsonRequest.toString());
        Status status = Status.OK;
        OperationOutcome outcome = null;
        if (id == null) {
            // Do not delete everything
            // ID is required...
            status = Status.BAD_REQUEST;
            outcome = App.DB.outcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_ID);
        } else if (patient == null) {
            // Do not delete everything
            // Patient ID is required...
            status = Status.UNAUTHORIZED;
            outcome = App.DB.outcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_PATIENT);
        } else {
            // Cascading delete
            App.DB.delete(Database.BUNDLE, id, patient);
            App.DB.delete(Database.CLAIM, id, patient);
            App.DB.delete(Database.CLAIM_RESPONSE, id, patient);
            outcome = App.DB.outcome(IssueSeverity.INFORMATION, IssueType.DELETED, DELETED_MSG);
        }
        String formattedData = isJsonRequest ? App.DB.json(outcome) : App.DB.xml(outcome);
        return Response.status(status).entity(formattedData).build();
    }
}