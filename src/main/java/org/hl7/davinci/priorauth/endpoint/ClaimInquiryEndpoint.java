package org.hl7.davinci.priorauth.endpoint;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.Audit;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.davinci.priorauth.authorization.AuthUtils;
import org.hl7.davinci.priorauth.endpoint.Endpoint.RequestType;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.hl7.fhir.r4.model.Claim.ItemComponent;
import org.hl7.fhir.r4.model.Claim.Use;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
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
import java.util.Date;
import java.util.List;
import java.util.logging.Logger;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Identifier;

import ca.uhn.fhir.parser.IParser;

/**
 * The Claim endpoint to claim inquiry operations
 */
@CrossOrigin
@RestController
@RequestMapping("/ClaimInquiry")
public class ClaimInquiryEndpoint {

    static final Logger logger = PALogger.getLogger();

    static final String REQUIRES_BUNDLE = "Prior Authorization Claim/$submit Operation requires a Bundle with a single Claim as the first entry and supporting resources.";
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
                        OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID,
                                PROCESS_FAILED);
                        formattedData = FhirUtils.getFormattedData(error, requestType);
                        logger.severe("ClaimEndpoint::InquiryOperation:Failed to process Bundle:" + bundle.getId());
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
                    OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID,
                            REQUIRES_BUNDLE);
                    formattedData = FhirUtils.getFormattedData(error, requestType);
                    logger.severe("ClaimEndpoint::InquiryOperation:First bundle entry is not a PASClaim");
                }
            } else {
                // Bundle is required...
                OperationOutcome error = FhirUtils.buildOutcome(IssueSeverity.ERROR, IssueType.INVALID,
                        REQUIRES_BUNDLE);
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
        Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, auditOutcome, null, request,
                "POST /Claim/$submit");
        MediaType contentType = requestType == RequestType.JSON ? MediaType.APPLICATION_JSON
                : MediaType.APPLICATION_XML;
        return ResponseEntity.status(status).contentType(contentType)
                .header(HttpHeaders.LOCATION,
                        App.getBaseUrl() + "/ClaimResponse?identifier=" + id + "&patient.identifier=" + patient)
                .body(formattedData);

    }

    private Bundle processBundle(Bundle bundle) {
        logger.fine("ClaimEndpoint::processBundle:" + bundle.getId());
        String id = FhirUtils.getIdFromResource(bundle);// this should be inquiry but if it's not
        String patient = FhirUtils.getPatientIdentifierFromBundle(bundle);
        Claim claimInq = FhirUtils.getClaimFromRequestBundle(bundle);
        Claim[] claim = (Claim[]) App.getDB().read(Table.CLAIM, Collections.singletonMap("patient", patient));
        List<Claim.ItemComponent> queriedItems = claim.getItem();
        if (id.contains("inquiry")) {
            if (patient.equals(claim.getPatient())) {
                return handleClaimInquiry(bundle);
            } else if (claim.getType() == claimInq.getType()) {

                return handleClaimInquiry(bundle);
            }
        } else {
            return new Bundle();
        }
    }

    private Bundle handleClaimInquiry(Bundle claimInquiryBundle) {
        String id = FhirUtils.getIdFromResource(claimInquiryBundle);// this should be inquiry but if it's not
        String patient = FhirUtils.getPatientIdentifierFromBundle(claimInquiryBundle);
        Claim claim = (Claim) App.getDB().read(Table.CLAIM, Collections.singletonMap("patient", patient));
        List<Claim.ItemComponent> queriedItems = claim.getItem();
        // Disposition responseDisposition =
        // ClaimResponseFactory.determineDisposition(claimInquiryBundle);

        ClaimResponse response = new ClaimResponse();
        String claimId = App.getDB().getMostRecentId(FhirUtils.getIdFromResource(claim));
        ClaimStatus status = claim.getStatus();
        if (status != ClaimStatus.CANCELLED) {

            response.setStatus(ClaimResponseStatus.ACTIVE);
        }
        response.setType(claim.getType());
        // response.setUse(Use.PREAUTHORIZATION);

        response.setPatient(claim.getPatient());
        response.setCreated(new Date());
        if (claim.hasInsurer()) {
            response.setInsurer(claim.getInsurer());
        } else {
            response.setInsurer(new Reference().setDisplay("Unknown"));
        }

        Identifier identifier = new Identifier();
        identifier.setSystem(App.getBaseUrl());
        identifier.setValue(claimId);
        response.addIdentifier(identifier);
        BundleEntryComponent entry = new BundleEntryComponent();

        Bundle responseBundle = new Bundle();
        responseBundle.setId(claimId);
        responseBundle.setType(Bundle.BundleType.COLLECTION);
        BundleEntryComponent responseEntry = responseBundle.addEntry();
        responseEntry.setResource(response);
        for (ItemComponent item : queriedItems) {
            entry.addExtension(FhirUtils.ITEM_TRACE_NUMBER_EXTENSION_URL,
                    new StringType(item.getSequenceElement().asStringValue()));
            response.setRequest(new Reference(App.getBaseUrl() + "Claim?identifier="
                    + FhirUtils.getIdFromResource(claim) + "&patient.identifier=" + patient));
            response.setItem((List<org.hl7.fhir.r4.model.ClaimResponse.ItemComponent>) item);
            Disposition responseDisposition = FhirUtils.Disposition.UNKNOWN;
            response.setDisposition(responseDisposition.value());
            response.setPreAuthRef(claimId);
            response.setId(claimId);
            response.addExtension(FhirUtils.REVIEW_ACTION_EXTENSION_URL,
                    FhirUtils.dispositionToReviewAction(responseDisposition).valueCode());
            entry.addExtension(FhirUtils.ITEM_TRACE_NUMBER_EXTENSION_URL,
                    new StringType(item.getSequenceElement().asStringValue()));

            responseBundle.addEntry(entry);

        }
        return responseBundle;

    }

}
