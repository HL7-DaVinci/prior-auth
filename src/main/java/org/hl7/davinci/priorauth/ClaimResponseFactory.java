package org.hl7.davinci.priorauth;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

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
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Claim.ItemComponent;
import org.hl7.fhir.r4.model.ClaimResponse.AdjudicationComponent;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;
import org.hl7.fhir.r4.model.ClaimResponse.Use;

public class ClaimResponseFactory {

    static final Logger logger = PALogger.getLogger();

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

        // Generate the claim response...
        ClaimResponse response = createClaimResponse(claim, id, responseDisposition, responseStatus, isScheduledUpdate);
        String claimId = App.getDB().getMostRecentId(FhirUtils.getIdFromResource(claim));

        Bundle responseBundle = createClaimResponseBundle(bundle, response, id);

        // Store the claim response...
        Map<String, Object> responseMap = new HashMap<>();
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
    private static ClaimResponse createClaimResponse(Claim claim, String id, Disposition responseDisposition, ClaimResponseStatus responseStatus, boolean isScheduledUpdate) {
        ClaimResponse response = new ClaimResponse();
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
        if (responseDisposition == Disposition.PENDING) {
            response.setOutcome(RemittanceOutcome.QUEUED);
        } else if (responseDisposition == Disposition.PARTIAL) {
            response.setOutcome(RemittanceOutcome.PARTIAL);
        } else {
            response.setOutcome(RemittanceOutcome.COMPLETE);
        }
        response.setItem(setClaimResponseItems(claim, isScheduledUpdate));
        response.setDisposition(responseDisposition.value());
        response.setPreAuthRef(id);
        response.setId(id);

        Identifier identifier = new Identifier();
        identifier.setSystem(App.getBaseUrl());
        identifier.setValue(id);
        response.addIdentifier(identifier);

        Meta meta = new Meta();
        meta.addProfile("http://hl7.org/fhir/us/davinci-pas/StructureDefinition/profile-claimresponse");
        response.setMeta(meta);

        return response;
    }

    /**
     * Set the Items on the ClaimResponse indicating the adjudication of each one
     * 
     * @param claim - the initial Claim which contains the items
     * @param isScheduledUpdate - true if this is an automated update to a pended claim
     * @return a list of ItemComponents to be added to the ClaimResponse.items field
     */
    private static List<ClaimResponse.ItemComponent> setClaimResponseItems(Claim claim, boolean isScheduledUpdate) {
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
        itemComponent.addAdjudication(adjudication);

        // Add the X12 extensions
        Extension reviewActionExtension = new Extension(FhirUtils.REVIEW_ACTION_EXTENSION_URL);
        CodeableConcept reviewActionCode = new CodeableConcept(new Coding(FhirUtils.REVIEW_ACTION_CODE_SYSTEM, action.value(), null));
        reviewActionExtension.addExtension(FhirUtils.REVIEW_ACTION_CODE_EXTENSION_URL, reviewActionCode);
        reviewActionExtension.addExtension(FhirUtils.REVIEW_NUMBER, new StringType(UUID.randomUUID().toString()));
        if (action.equals(ReviewAction.DENIED) || action.equals(ReviewAction.PENDED)) {
            CodeableConcept reasonCodeableConcept = new CodeableConcept(new Coding(FhirUtils.REVIEW_REASON_CODE_SYSTEM, "X", "TODO: unknown"));
            reviewActionExtension.addExtension(FhirUtils.REVIEW_REASON_CODE, reasonCodeableConcept);
        }

        Extension itemAuthorizedProviderExtension = new Extension(FhirUtils.ITEM_AUTHORIZED_PROVIDER_EXTENSION_URL);
        itemAuthorizedProviderExtension.addExtension("provider", provider);

        itemComponent.addExtension(reviewActionExtension);
        itemComponent.addExtension(FhirUtils.ITEM_PREAUTH_ISSUE_DATE_EXTENSION_URL, new DateType(new Date()));
        itemComponent.addExtension(FhirUtils.AUTHORIZATION_NUMBER_EXTENSION_URL, new StringType(UUID.randomUUID().toString()));
        itemComponent.addExtension(itemAuthorizedProviderExtension);

        return itemComponent;
    }
}