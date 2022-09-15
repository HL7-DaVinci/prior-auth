package org.hl7.davinci.priorauth.endpoint;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.Audit;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.authorization.AuthUtils;
import org.hl7.davinci.priorauth.endpoint.Endpoint.RequestType;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Identifier;

import ca.uhn.fhir.parser.IParser;

/**
 * The Claim endpoint to claim inquiry operations
 */
@CrossOrigin
@RestController
@RequestMapping("/Claim")
public class ClaimInquiryEndpoint {

    static final Logger logger = PALogger.getLogger();

    static final String REQUIRES_BUNDLE = "Prior Authorization Claim/$inquiry Operation requires a Bundle with a single Claim as the first entry and supporting resources.";
    static final String PROCESS_FAILED = "Unable to process the request properly. Check the log for more details.";

    @PostMapping(value = "/$inquiry", consumes = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
    public ResponseEntity<String> submitOperationJson(HttpServletRequest request, HttpEntity<String> entity) {
        return inquiryOperation(entity.getBody(), RequestType.JSON, request);
    }

    @PostMapping(value = "/$inquiry", consumes = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
    public ResponseEntity<String> submitOperationXml(HttpServletRequest request, HttpEntity<String> entity) {
        return inquiryOperation(entity.getBody(), RequestType.XML, request);
    }

    /**
     * The inquiryOperation function for both json and xml
     *
     * @param body        - the body of the post request.
     * @param requestType - the RequestType of the request.
     * @return - claimResponse response
     */

    private ResponseEntity<String> inquiryOperation(String body, RequestType requestType, HttpServletRequest request) {
        logger.info("POST /Claim/$inquiry fhir+" + requestType.name());
        App.setBaseUrl(Endpoint.getServiceBaseUrl(request));

        if (!AuthUtils.validateAccessToken(request))
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                    .body("{ error: \"Invalid access token. Make sure to use Authorization: Bearer (token)\" }");

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

                    Claim claimInq = (Claim) bundle.getEntry().get(0).getResource();
                    if (claimInq.hasProvider() && claimInq.hasInsurer()
                            && (FhirUtils.getPatientIdentifierFromBundle(bundle) != null)) {
                        Bundle responseBundle = getClaimResponseBundle(bundle);
                        if (responseBundle == null) {
                            // Failed to find ClaimResponse
                            OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID,
                                    PROCESS_FAILED);
                            formattedData = FhirUtils.getFormattedData(error, requestType);
                            logger.severe(
                                    "ClaimInquiryEndpoint::InquiryOperation:Could not find matching ClaimResponse for inquiry Bundle:"
                                            + bundle.getId());
                            status = HttpStatus.NOT_FOUND;
                            auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
                        } else {
                            formattedData = FhirUtils.getFormattedData(responseBundle, requestType);
                            status = HttpStatus.OK;
                            auditOutcome = AuditEventOutcome.SUCCESS;
                        }
                    } else {
                        // Failed because the inquriy didn't include the required elements
                        OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID,
                                PROCESS_FAILED);
                        formattedData = FhirUtils.getFormattedData(error, requestType);
                        logger.severe(
                                "ClaimInquiryEndpoint::InquiryOperation: Required elements were not found in inquiry Bundle:"
                                        + bundle.getId());
                        status = HttpStatus.NOT_FOUND;
                        auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
                    }
                } else {
                    // Claim is required...
                    OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID,
                            REQUIRES_BUNDLE);
                    formattedData = FhirUtils.getFormattedData(error, requestType);
                    logger.severe("ClaimInquiryEndpoint::InquiryOperation:First bundle entry is not a PASClaim");
                }
            } else {
                // Bundle is required...
                OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID,
                        REQUIRES_BUNDLE);
                formattedData = FhirUtils.getFormattedData(error, requestType);
                logger.severe("ClaimInquiryEndpoint::SubmitOperation:Body is not a Bundle");
            }
        } catch (Exception e) {
            // The inquiry failed so spectacularly that we need to
            // catch an exception and send back an error message...
            OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
            formattedData = FhirUtils.getFormattedData(error, requestType);
            auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
        }
        Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, auditOutcome, null, request,
                "POST /Claim/$inquiry");
        MediaType contentType = requestType == RequestType.JSON ? MediaType.APPLICATION_JSON
                : MediaType.APPLICATION_XML;
        String fhirContentType = requestType == RequestType.JSON ? "application/fhir+json" : "application/fhir+xml";
        return ResponseEntity.status(status).contentType(contentType)
                .header(HttpHeaders.CONTENT_TYPE, fhirContentType + "; charset=utf-8").body(formattedData);
    }

    private Bundle getClaimResponseBundle(Bundle requestBundle) {
        String patient = FhirUtils.getPatientIdentifierFromBundle(requestBundle);
        Claim claimInquiry = FhirUtils.getClaimFromRequestBundle(requestBundle);
        Identifier claimInquiryIdentifier = claimInquiry.getIdentifier().get(0);

        // Search DB for all claims for patient then filter to where identifiers match
        List<IBaseResource> claims = App.getDB().readAll(Table.CLAIM, Collections.singletonMap("patient", patient));
        logger.fine("Found " + claims.size() + " claims for Patient " + patient + " (Inquiry bundle: "
                + requestBundle.getId() + ")");
        for (IBaseResource resource : claims) {
            Claim claim = (Claim) resource;
            Identifier claimIdentifier = claim.getIdentifier().get(0);
            if (identifiersMatch(claimIdentifier, claimInquiryIdentifier)) {
                // Find the ClaimResponse
                String claimId = FhirUtils.getIdFromResource(claim);
                Map<String, Object> constraintMap = new HashMap<>();
                constraintMap.put("patient", patient);
                constraintMap.put("claimId", claimId);
                logger.fine("Found matching Claim. Getting ClaimResponse with patient: " + patient + " and claimId: "
                        + claimId);
                return (Bundle) App.getDB().read(Table.CLAIM_RESPONSE, constraintMap);
            }
        }

        return null;
    }

    private boolean identifiersMatch(Identifier identifier1, Identifier identifier2) {
        return identifier1.getSystem().equals(identifier2.getSystem())
                && identifier1.getValue().equals(identifier2.getValue());
    }

}
