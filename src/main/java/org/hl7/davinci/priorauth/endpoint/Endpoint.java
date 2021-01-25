package org.hl7.davinci.priorauth.endpoint;

import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.Audit;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
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
    static String SQL_ERROR = "Unable to perform operation. SQL error while processing. Check logs for more details.";

    /**
     * Read a resource from an endpoint in either JSON or XML
     * 
     * @param table         - the Table to read from.
     * @param constraintMap - map of the column names and values for the SQL query.
     * @param uri           - the base URI for the microservice.
     * @param requestType   - the RequestType of the request.
     * @return the desired resource if successful and an error message otherwise
     */
    public static ResponseEntity<String> read(Table table, Map<String, Object> constraintMap,
            HttpServletRequest request, RequestType requestType) {
        logger.info("GET /" + table.value() + ":" + constraintMap.toString() + " fhir+" + requestType.name());
        App.setBaseUrl(Endpoint.getServiceBaseUrl(request));
        String referenceUrl = table.value() + "/" + constraintMap.get("id");
        if (!constraintMap.containsKey("patient")) {
            logger.warning("Endpoint::read:patient null");
            String description = "Attempted to read " + table.value() + " but is unathorized";
            Audit.createAuditEvent(AuditEventType.QUERY, AuditEventAction.R, AuditEventOutcome.MINOR_FAILURE, referenceUrl, request,
                    description);
            return new ResponseEntity<>(HttpStatus.UNAUTHORIZED);
        }
        String formattedData = null;
        String description = "Read " + referenceUrl;
        if ((!constraintMap.containsKey("id") || constraintMap.get("id") == null)
                && (!constraintMap.containsKey("claimId") || constraintMap.get("claimId") == null)) {
            // Search
            constraintMap.remove("id");
            Bundle searchBundle;
            searchBundle = App.getDB().search(table, constraintMap);
            formattedData = FhirUtils.getFormattedData(searchBundle, requestType);
            description = "Searched for " + referenceUrl;
        } else {
            // Read
            IBaseResource baseResource;
            baseResource = App.getDB().read(table, constraintMap);

            if (baseResource == null)
                return new ResponseEntity<>(HttpStatus.NOT_FOUND);

            // Convert to correct resourceType
            if (table == Table.BUNDLE) {
                Bundle bundle = (Bundle) baseResource;
                formattedData = FhirUtils.getFormattedData(bundle, requestType);
            } else if (table == Table.CLAIM) {
                Claim claim = (Claim) baseResource;
                formattedData = FhirUtils.getFormattedData(claim, requestType);
            } else if (table == Table.CLAIM_RESPONSE) {
                Bundle bundleResponse = (Bundle) baseResource;
                formattedData = FhirUtils.getFormattedData(bundleResponse, requestType);
            } else {
                logger.warning("Endpoint::read:invalid table: " + table.value());
                description = "Attempted to read " + referenceUrl + " but is invalid";
                Audit.createAuditEvent(AuditEventType.QUERY, AuditEventAction.R, AuditEventOutcome.MINOR_FAILURE, referenceUrl,
                        request, description);
                return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
            }
        }

        Audit.createAuditEvent(AuditEventType.QUERY, AuditEventAction.R, AuditEventOutcome.MINOR_FAILURE, referenceUrl, request,
                description);
        return new ResponseEntity<>(formattedData, HttpStatus.OK);
    }

    /**
     * Delete a specific resource
     * 
     * @param id          - the ID of the resource.
     * @param patient     - the patient ID.
     * @param table       - the Table to delete from.
     * @param requestType - the RequestType of the request.
     * @return status of the deleted resource
     */
    public static ResponseEntity<String> delete(String id, String patient, Table table, HttpServletRequest request,
            RequestType requestType) {
        logger.info("DELETE /" + table.value() + ":" + id + "/" + patient + " fhir+" + requestType.name());
        HttpStatus status = HttpStatus.OK;
        OperationOutcome outcome = null;
        AuditEventOutcome auditOutcome = AuditEventOutcome.MINOR_FAILURE;
        String referenceUrl = table.value() + "/" + id;
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
            // Delete the specified resource..
            if (App.getDB().delete(table, id, patient)) {
                outcome = FhirUtils.buildOutcome(IssueSeverity.INFORMATION, IssueType.DELETED, DELETED_MSG);
                auditOutcome = AuditEventOutcome.SUCCESS;
            } else
                outcome = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INCOMPLETE, SQL_ERROR);
        }

        String description = "Delete " + referenceUrl;
        Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.D, auditOutcome, referenceUrl, request, description);

        String formattedData = FhirUtils.getFormattedData(outcome, requestType);
        return new ResponseEntity<>(formattedData, status);
    }

    /**
     * Get the base url of the service from the HttpServletRequest
     * 
     * @param request - the HttpServletRequest from the controller
     * @return the base url for the service
     */
    public static String getServiceBaseUrl(HttpServletRequest request) {
        if (request.getServerPort() != 80)
            return "https://" + request.getServerName() + ":" + request.getServerPort()
                + request.getContextPath();
        else 
            return "https://" + request.getServerName() + request.getContextPath();
    }

}