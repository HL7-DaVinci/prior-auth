package org.hl7.davinci.priorauth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;
import java.util.Random;

//import org.springframework.core.io.Resource;
import org.springframework.core.io.DefaultResourceLoader;

import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.davinci.priorauth.FhirUtils.ReviewAction;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.DateType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.PositiveIntType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Claim.DiagnosisComponent;
import org.hl7.fhir.r4.model.Claim.ItemComponent;
import org.hl7.fhir.r4.model.ClaimResponse.AdjudicationComponent;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;
import org.hl7.fhir.r4.model.ClaimResponse.Use;
import org.hl7.fhir.r4.model.CommunicationRequest.CommunicationRequestStatus;
import org.hl7.fhir.r4.model.CommunicationRequest;
import org.hl7.fhir.r4.model.CommunicationRequest.CommunicationRequestPayloadComponent;
import org.hl7.fhir.r4.model.DateTimeType;

public class ClaimResponseFactory {

    static final Logger logger = PALogger.getLogger();
    static final String TEMP_REQUEST_CODE = "73722";
    static final String TEMP_REQUEST_SYSTEM = "http://www.ama-assn.org/go/cpt";
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
     * @param isScheduledUpdate   true if this is an automated update to a pended claim
     * @return ClaimResponse that has been generated and stored in the Database.
     */
    public static Bundle generateAndStoreClaimResponse(Bundle bundle, Claim claim, String id,
            Disposition responseDisposition, ClaimResponseStatus responseStatus, String patient,
            boolean isScheduledUpdate) {
        logger.info("ClaimResponseFactory::generateAndStoreClaimResponse(" + id + "/" + patient + ", disposition: "
                + responseDisposition + ", status: " + responseStatus + ")");
        
        boolean hasRequestTrigger = false;
        for(ItemComponent item : claim.getItem())
        {
            if(ItemRequiresFollowup(item))
            {
                logger.info("Found a pending information Request Procedure Code");
                hasRequestTrigger = true;
            }
            /*
            for(Coding procedureCoding : item.getProductOrService().getCoding())
            {
                if(ItemRequiresFollowup(item))
                // TODO: change to configuration file driven check. See file resources/requestMappingTable 
                //if(procedureCoding.getCode().equals(TEMP_REQUEST_CODE) && procedureCoding.getSystem().equals(TEMP_REQUEST_SYSTEM))
                {
                    logger.info("Found a pending information Request Procedure Code");
                    hasRequestTrigger = true;
                }
            }*/
            if(hasRequestTrigger)
            {
                break;
            }
            
        }
        // Check Claim product or services for a special code that requires a pending response with a request for more information from code in "requestMappingTable.json"
        //DefaultResourceLoader resourceLoader = new DefaultResourceLoader();
		//Resource mappingTable = resourceLoader.getResource("requestMappingTable.json");
        //claim
        // Generate the claim response...
        ClaimResponse claimResponse = createClaimResponse(claim, id, responseDisposition, responseStatus, isScheduledUpdate, hasRequestTrigger);
        String claimId = App.getDB().getMostRecentId(FhirUtils.getIdFromResource(claim));
        Bundle responseBundle = new Bundle();
        if(hasRequestTrigger)
        {
            // create CommunicationRequest
            CommunicationRequest communicationRequest = createCommunicationRequest(claim, claimResponse, id, responseDisposition, responseStatus, isScheduledUpdate);  
            //Reference crRef = new Reference("CommunicationReference/" + communicationRequest.getId());
            //List<Reference> crRef = new ArrayList<Reference>(Arrays.asList(new Reference("CommunicationReference/" + communicationRequest.getId())));
            claimResponse.setCommunicationRequest(new ArrayList<Reference>(Arrays.asList(new Reference("CommunicationReference/" + id))));

            responseBundle = createClaimResponseBundle(bundle, claimResponse, communicationRequest, id);
        }
        else{
            responseBundle = createClaimResponseBundle(bundle, claimResponse, id);
        }
        //responseBundle = createClaimResponseBundle(bundle, claimResponse, id);
        //CommunicationRequest communicationRequest = createCommunicationRequest(claim, claimResponse, id, responseDisposition, responseStatus, isScheduledUpdate);

        
        
        // Store the claim response...
        Map<String, Object> responseMap = new HashMap<>();
        responseMap.put("id", id);
        responseMap.put("claimId", claimId);
        responseMap.put("patient", patient);
        responseMap.put("status", FhirUtils.getStatusFromResource(claimResponse));
        responseMap.put("outcome", FhirUtils.dispositionToReviewAction(responseDisposition).value());
        responseMap.put("resource", responseBundle);
        App.getDB().write(Table.CLAIM_RESPONSE, responseMap);

        return responseBundle;
    }
    public static boolean ItemRequiresFollowup(ItemComponent item)
    {
        boolean hasRequestTrigger = false;
        for(Coding procedureCoding : item.getProductOrService().getCoding())
        {
            // TODO: change to configuration file driven check. See file resources/requestMappingTable 
            if(procedureCoding.getCode().equals(TEMP_REQUEST_CODE) && procedureCoding.getSystem().equals(TEMP_REQUEST_SYSTEM))
            {
                logger.info("Found a pending information Request Procedure Code");
                hasRequestTrigger = true;
                break;
            }
        }
        return hasRequestTrigger;
    }

    /**
     * Create the Bundle for a ClaimResponse
     * @param requestBundle - the Claim request Bundle
     * @param claimResponse - the ClaimResponse
     * @param id - the id of the response Bundle
     * @return A Bundle with ClaimResponse, Patient, and Practitioner
     */
    public static Bundle createClaimResponseBundle(Bundle requestBundle, ClaimResponse claimResponse, String id) {
        Bundle responseBundle = new Bundle();
        responseBundle.setId(id);
        responseBundle.setType(Bundle.BundleType.COLLECTION);
        responseBundle.setTimestamp(new Date());
        Meta meta = new Meta();
        meta.addProfile("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-response-bundle");
        BundleEntryComponent responseEntry = responseBundle.addEntry();
        responseEntry.setResource(claimResponse);
        responseEntry.setFullUrl(App.getBaseUrl() + "/ClaimResponse/" + id);

        if (FhirUtils.isDifferential(requestBundle)) {
            logger.info("ClaimResponseFactory::Adding subsetted tag");
            // meta.addSecurity(FhirUtils.SECURITY_SYSTEM_URL, FhirUtils.SECURITY_SUBSETTED, FhirUtils.SECURITY_SUBSETTED); // This causes an error for some reason
        }

        // Add Patient and Provider from Claim Bundle into Response Bundle
        for (BundleEntryComponent entry : requestBundle.getEntry()) {
            Resource r = entry.getResource();
            if (r != null && (r.getResourceType() == ResourceType.Patient || r.getResourceType() == ResourceType.Practitioner)) {
                responseBundle.addEntry(entry);
            }
        }

        responseBundle.setMeta(meta);

        return responseBundle;
    }

    /**
     * Create the Bundle for a ClaimResponse
     * @param requestBundle - the Claim request Bundle
     * @param claimResponse - the ClaimResponse
     * @param communicationRequest - the CommunicationRequest
     * @param id - the id of the response Bundle
     * @return A Bundle with ClaimResponse, Patient, and Practitioner
     */
    public static Bundle createClaimResponseBundle(Bundle requestBundle, ClaimResponse claimResponse, CommunicationRequest communicationRequest, String id) {
        Bundle responseBundle = new Bundle();
        responseBundle.setId(id);
        responseBundle.setType(Bundle.BundleType.COLLECTION);
        responseBundle.setTimestamp(new Date());
        Meta meta = new Meta();
        meta.addProfile("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-pas-response-bundle");
        BundleEntryComponent responseEntry = responseBundle.addEntry();
        responseEntry.setResource(claimResponse);
        responseEntry.setFullUrl(App.getBaseUrl() + "/ClaimResponse/" + id);

        // Add CommunicationRequest
        BundleEntryComponent requestEntry = responseBundle.addEntry();
        requestEntry.setResource(communicationRequest);
        requestEntry.setFullUrl(App.getBaseUrl() + "/CommunicationRequest/" + id);
        // Add Patient and Provider from Claim Bundle into Response Bundle
        for (BundleEntryComponent entry : requestBundle.getEntry()) {
            Resource r = entry.getResource();
            if (r != null && (r.getResourceType() == ResourceType.Patient || r.getResourceType() == ResourceType.Practitioner)) {
                responseBundle.addEntry(entry);
            }
        }

        if (FhirUtils.isDifferential(requestBundle)) {
            logger.info("ClaimResponseFactory::Adding subsetted tag");
            // meta.addSecurity(FhirUtils.SECURITY_SYSTEM_URL, FhirUtils.SECURITY_SUBSETTED, FhirUtils.SECURITY_SUBSETTED); // This causes an error for some reason
        }
        

        responseBundle.setMeta(meta);

        return responseBundle;
    }

    /**
     * Determine the Disposition for the Claim
     * 
     * @param bundle - the Claim Bundle with all supporting documentation
     * @return Disposition of Pending, Partial, Granted, or Denied
     */
    public static Disposition determineDisposition(Bundle bundle) {
        Claim claim = FhirUtils.getClaimFromRequestBundle(bundle);
        String claimId = FhirUtils.getIdFromResource(claim);
        if (claim.hasItem()) {
            // Go through each claim and determine what the complete disposition is
            boolean atleastOneGranted = false;
            boolean atleastOneDenied = false;
            boolean atleastOnePended = false;
            for (ItemComponent item : claim.getItem()) {
                Map<String, Object> constraintMap = new HashMap<>();
                constraintMap.put("id", claimId);
                constraintMap.put("sequence", item.getSequence());
                String outcome = App.getDB().readString(Table.CLAIM_ITEM, constraintMap, "outcome");
                ReviewAction reviewAction = ReviewAction.fromString(outcome);

                if (reviewAction == ReviewAction.APPROVED)
                    atleastOneGranted = true;
                else if (reviewAction == ReviewAction.DENIED)
                    atleastOneDenied = true;
                else if (reviewAction == ReviewAction.PENDED)
                    atleastOnePended = true;
            }

            Disposition disposition = Disposition.UNKNOWN;
            if (atleastOnePended)
                disposition = Disposition.PENDING;
            else if (atleastOneGranted && atleastOneDenied)
                disposition = Disposition.PARTIAL;
            else if (atleastOneGranted && !atleastOneDenied)
                disposition = Disposition.GRANTED;
            else if (atleastOneDenied && !atleastOneGranted)
                disposition = Disposition.DENIED;

            logger.info("ClaimResponseFactory::determineDisposition:Claim " + claimId + ":" + disposition.value());
            return disposition;
        } else {
            // There were no items on this claim so determine the disposition here
            logger.warning(
                    "ClaimResponseFactory::determineDisposition:Request had no items to compute disposition from. Returning in pended by default");
            return Disposition.PENDING;
        }
    }

    /**
     * Create the ClaimResponse resource for the response bundle
     * 
     * @param claim               The claim which this ClaimResponse is in reference
     *                            to.
     * @param id                  The new identifier for this ClaimResponse.
     * @param responseDisposition The new disposition for this ClaimResponse
     *                            (Granted, Pending, Cancelled, Declined ...).
     * @param responseStatus      The new status for this ClaimResponse (Active,
     *                            Cancelled, ...).
     * @param isScheduledUpdate   true if this is an automated update to a pended claim
     * @return ClaimResponse resource
     */
    private static ClaimResponse createClaimResponse(Claim claim, String id, Disposition responseDisposition, ClaimResponseStatus responseStatus, boolean isScheduledUpdate, boolean isFollowupRequired) {
        ClaimResponse response = new ClaimResponse();
        response.setStatus(responseStatus);
        response.setType(claim.getType());
        response.setUse(Use.PREDETERMINATION);
        response.setPatient(claim.getPatient());
        response.setCreated(new Date());
        if (claim.hasInsurer()) {
            response.setInsurer(claim.getInsurer());
        } else {
            response.setInsurer(new Reference().setDisplay("Unknown"));
        }
        if (responseDisposition == Disposition.PENDING) {
            response.setOutcome(RemittanceOutcome.QUEUED);
        } else if (responseDisposition == Disposition.PARTIAL) {
            response.setOutcome(RemittanceOutcome.PARTIAL);
        } else {
            response.setOutcome(RemittanceOutcome.COMPLETE);
        }
        response.setItem(setClaimResponseItems(claim, isScheduledUpdate, isFollowupRequired));
        //response.setDisposition(responseDisposition.value());
        response.setDisposition("Pending");
        response.setPreAuthRef(id);
        response.setId(id);

        Identifier identifier = new Identifier();
        identifier.setSystem(App.getBaseUrl());
        identifier.setValue(id);
        response.addIdentifier(identifier);

        // TODO: Add patient event Tracer ID - http://example.org/payer/PATIENT_EVENT_TRACE_NUMBER
        /* Set the ClaimResponse.identifier.system=”http://example.org/payer/PATIENT_EVENT_TRACE_NUMBER” and the value to an incremented number 
        (If the number is not initiated, make it a random number of 7 digits, increment one for each ClaimResponse Created). 
        This same Identifier will be use for the CommunicationRequest
        */
        Random rand = new Random();
        int int_random = rand.nextInt(999999);
        Identifier traceIdentifier = new Identifier();
        traceIdentifier.setSystem("http://example.org/payer/PATIENT_EVENT_TRACE_NUMBER");
        traceIdentifier.setValue("1" + String.format("%0" + 6 + "d", int_random));
        logger.info("!!!!! ClaimResponse ID (May be used for release pending Claim): " + traceIdentifier.getValue());
        response.addIdentifier(traceIdentifier);

        // echo back the claim identifiers
        for(Identifier claimIdentifier : claim.getIdentifier())
        {
            response.addIdentifier(claimIdentifier);
        }

        Meta meta = new Meta();
        meta.addProfile("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse");
        response.setMeta(meta);

        

        return response;
    }
    private static CommunicationRequest createCommunicationRequest(Claim claim, ClaimResponse claimResponse, String id, Disposition responseDisposition, ClaimResponseStatus responseStatus, boolean isScheduledUpdate) {
        CommunicationRequest communicationRequest = new CommunicationRequest();
        communicationRequest.setId(id);
        /* Set the ClaimResponse.identifier.system=”http://example.org/payer/PATIENT_EVENT_TRACE_NUMBER” and the value to an incremented number 
        (If the number is not initiated, make it a random number of 7 digits, increment one for each ClaimResponse Created). 
        This same Identifier will be use for the CommunicationRequest
        */
        for(Identifier identifier : claimResponse.getIdentifier())
        {
            if(identifier.getSystem().equals("http://example.org/payer/PATIENT_EVENT_TRACE_NUMBER"))
            {
                communicationRequest.addIdentifier(identifier);
            }
        }
        communicationRequest.setStatus(CommunicationRequestStatus.ACTIVE);

        for(ItemComponent item : claim.getItem())
        {
            if(ItemRequiresFollowup(item))
            {
                CommunicationRequestPayloadComponent crPayload = new CommunicationRequestPayloadComponent();
                crPayload.addExtension(FhirUtils.PAYLOAD_SERVICE_LINE_NUMBER, new PositiveIntType(item.getSequence()));
                // TODO, this needs to be loaded from the request mapping table
                crPayload.addExtension(FhirUtils.PAYLOAD_CONTENT_MODIFIER, new CodeableConcept(new Coding("http://loinc.org", "18748-4", "Diagnostic imaging study")));
                for(DiagnosisComponent diagnosis : claim.getDiagnosis())
                {
                    
                    if(diagnosis.hasDiagnosisCodeableConcept())
                    {
                        crPayload.addExtension(FhirUtils.PAYLOAD_COMMUNICATED_DIAGNOSIS, diagnosis.getDiagnosisCodeableConcept());    
                    }
                    crPayload.addExtension(FhirUtils.PAYLOAD_CONTENT_MODIFIER, new CodeableConcept(new Coding("http://loinc.org", "18748-4", "Diagnostic imaging study")));
                }
                crPayload.setContent(new StringType("Please provide the additional requested information"));
                communicationRequest.addPayload(crPayload);
            }
        }

        return communicationRequest;
    }

    /**
     * Set the Items on the ClaimResponse indicating the adjudication of each one
     * 
     * @param claim - the initial Claim which contains the items
     * @param isScheduledUpdate - true if this is an automated update to a pended claim
     * @return a list of ItemComponents to be added to the ClaimResponse.items field
     */
    private static List<ClaimResponse.ItemComponent> setClaimResponseItems(Claim claim, boolean isScheduledUpdate, boolean isFollowupRequired) {
        List<ClaimResponse.ItemComponent> items = new ArrayList<>();

        // Set the Items on the ClaimResponse based on the initial Claim and the
        // Response disposition
        for (ItemComponent item : claim.getItem()) {
            // Read the item outcome from the database
            Map<String, Object> constraintMap = new HashMap<>();
            constraintMap.put("id", FhirUtils.getIdFromResource(claim));
            constraintMap.put("sequence", item.getSequence());
            String outcome = App.getDB().readString(Table.CLAIM_ITEM, constraintMap, "outcome");
            ReviewAction reviewAction = isScheduledUpdate ? ReviewAction.APPROVED : ReviewAction.fromString(outcome);
            if(isFollowupRequired)
            {
                reviewAction = ReviewAction.PENDEDFOLLOWUP;
            }

            ClaimResponse.ItemComponent itemComponent = createItemComponent(item, reviewAction,
                    claim.getProvider());
            items.add(itemComponent);
        }
        return items;
    }

    /**
     * Set the item for a ClaimResponse ItemComponent based on the submitted item
     * and the outcome
     * 
     * @param item   - the ItemComponent from the Claim
     * @param action - ReviewAction representing the outcome of the claim item
     * @return ClaimResponse ItemComponent with appropriate elements set
     */
    private static ClaimResponse.ItemComponent createItemComponent(ItemComponent item, ReviewAction action, Reference provider) {
        ClaimResponse.ItemComponent itemComponent = new ClaimResponse.ItemComponent();
        itemComponent.setItemSequence(item.getSequence());

        // Add the adjudication
        Coding adjudicationCoding = new Coding();
        adjudicationCoding.setCode("submitted");
        adjudicationCoding.setSystem("http://terminology.hl7.org/CodeSystem/adjudication");
        CodeableConcept category = new CodeableConcept(adjudicationCoding);
        AdjudicationComponent adjudication = new AdjudicationComponent(category);
        
        // ”http://example.org/payer/PATIENT_EVENT_TRACE_NUMBER
        

        //Serviced[x]
        if(item.hasServicedDateType())
        {
            itemComponent.addExtension(FhirUtils.ITEM_REQUESTED_SERVICE_DATE, new DateTimeType(item.getServicedDateType().getValueAsString()));
        }
        else if(item.hasServicedPeriod())
        {
            itemComponent.addExtension(FhirUtils.ITEM_REQUESTED_SERVICE_DATE, item.getServicedPeriod());
        }
        // Add reviewAction extension
        Extension adjudicationReviewItemExtension = new Extension(FhirUtils.REVIEW_ACTION_EXTENSION_URL);
        adjudicationReviewItemExtension.addExtension(FhirUtils.REVIEW_ACTION_CODE_EXTENSION_URL, new CodeableConcept(new Coding(action.getCodeSystem(), action.value(), action.getDisplay())));
        
        if(action == ReviewAction.PENDEDFOLLOWUP)
        {
            // TODO, The item trace number should be mapped from the request mapping table
            Identifier traceIdentifier = new Identifier();
            traceIdentifier.setSystem("http://example.org/payer/PATIENT_EVENT_LINE_TRACE_NUMBER");
            traceIdentifier.setValue("1111111");
            itemComponent.addExtension(FhirUtils.ITEM_TRACE_NUMBER_EXTENSION_URL, traceIdentifier);
            
            adjudicationReviewItemExtension.addExtension(FhirUtils.REVIEW_REASON_CODE, new Coding("https://codesystem.x12.org/external/886", "OS", "Open, Waiting for Supplier Feedback"));
            adjudication.addExtension(adjudicationReviewItemExtension);
        }
        

        itemComponent.addAdjudication(adjudication);
        

        // Add the X12 extensions
        Extension itemAuthorizedProviderExtension = new Extension(FhirUtils.ITEM_AUTHORIZED_PROVIDER_EXTENSION_URL);
        itemAuthorizedProviderExtension.addExtension("provider", provider);

        
        
        itemComponent.addExtension(FhirUtils.ITEM_PREAUTH_ISSUE_DATE_EXTENSION_URL, new DateType(new Date()));
        itemComponent.addExtension(FhirUtils.AUTHORIZATION_NUMBER_EXTENSION_URL, new StringType(UUID.randomUUID().toString()));
        itemComponent.addExtension(itemAuthorizedProviderExtension);

        return itemComponent;
    }
}