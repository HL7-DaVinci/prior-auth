package org.hl7.davinci.priorauth;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.davinci.priorauth.FhirUtils.ReviewAction;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Claim.ItemComponent;
import org.hl7.fhir.r4.model.ClaimResponse.AdjudicationComponent;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;
import org.hl7.fhir.r4.model.ClaimResponse.Use;

public class ClaimResponseFactory {

    static final Logger logger = PALogger.getLogger();

    static final String ITEM_REFERENCE_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemReference";
    static final String REVIEW_ACTION_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewAction";
    static final String REVIEW_ACTION_REASON_EXTENSION_URL = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-reviewActionReason";

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
     * @return ClaimResponse that has been generated and stored in the Database.
     */
    public static Bundle generateAndStoreClaimResponse(Bundle bundle, Claim claim, String id,
            Disposition responseDisposition, ClaimResponseStatus responseStatus, String patient) {
        logger.info("ClaimResponseFactory::generateAndStoreClaimResponse(" + id + "/" + patient + ", disposition: "
                + responseDisposition + ", status: " + responseStatus + ")");

        // Generate the claim response...
        ClaimResponse response = new ClaimResponse();
        String claimId = App.getDB().getMostRecentId(FhirUtils.getIdFromResource(claim));
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
        response.setRequest(new Reference(App.getBaseUrl() + "Claim?identifier=" + FhirUtils.getIdFromResource(claim)
                + "&patient.identifier=" + patient));
        if (responseDisposition == Disposition.PENDING) {
            response.setOutcome(RemittanceOutcome.QUEUED);
        } else if (responseDisposition == Disposition.PARTIAL) {
            response.setOutcome(RemittanceOutcome.PARTIAL);
        } else {
            response.setOutcome(RemittanceOutcome.COMPLETE);
        }
        response.setItem(setClaimResponseItems(claim, responseDisposition));
        response.setDisposition(responseDisposition.value());
        response.setPreAuthRef(id);
        // TODO response.setPreAuthPeriod(period)?
        response.setId(id);

        response.addExtension(REVIEW_ACTION_EXTENSION_URL,
                FhirUtils.dispositionToReviewAction(responseDisposition).value());

        Identifier identifier = new Identifier();
        identifier.setSystem(App.getBaseUrl());
        identifier.setValue(id);
        response.addIdentifier(identifier);

        if (responseDisposition == Disposition.DENIED || responseDisposition == Disposition.PENDING) {
            response.addExtension(REVIEW_ACTION_REASON_EXTENSION_URL, new StringType("X"));
        }

        // Create the responseBundle
        Bundle responseBundle = new Bundle();
        responseBundle.setId(id);
        responseBundle.setType(Bundle.BundleType.COLLECTION);
        BundleEntryComponent responseEntry = responseBundle.addEntry();
        responseEntry.setResource(response);
        responseEntry.setFullUrl(App.getBaseUrl() + "/ClaimResponse/" + id);
        for (BundleEntryComponent entry : bundle.getEntry()) {
            responseBundle.addEntry(entry);
        }

        // Store the claim respnose...
        Map<String, Object> responseMap = new HashMap<String, Object>();
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
     * Set the Items on the ClaimResponse indicating the adjudication of each one
     * 
     * @param claim               - the initial Claim which contains the items
     * @param responseDisposition - the overall disposition
     * @return a list of ItemComponents to be added to the ClaimResponse.items field
     */
    private static List<ClaimResponse.ItemComponent> setClaimResponseItems(Claim claim,
            Disposition responseDisposition) {
        List<ClaimResponse.ItemComponent> items = new ArrayList<ClaimResponse.ItemComponent>();
        // ReviewAction reviewAction = ReviewAction.DENIED;
        ReviewAction reviewAction = null;
        boolean hasDeniedAtLeastOne = false;
        boolean hasApprovedAtLeastOne = false;

        // Set the Items on the ClaimResponse based on the initial Claim and the
        // Response disposition
        for (ItemComponent item : claim.getItem()) {
            reviewAction = FhirUtils.dispositionToReviewAction(responseDisposition);
            if (responseDisposition == Disposition.PARTIAL) {
                // Deny some and approve some
                if (!hasDeniedAtLeastOne) {
                    reviewAction = ReviewAction.DENIED;
                    hasDeniedAtLeastOne = true;
                } else if (!hasApprovedAtLeastOne) {
                    reviewAction = ReviewAction.APPROVED;
                    hasApprovedAtLeastOne = true;
                } else {
                    switch (FhirUtils.getRand(2)) {
                    case 1:
                        reviewAction = ReviewAction.DENIED;
                        break;
                    case 2:
                    default:
                        reviewAction = ReviewAction.APPROVED;
                        break;
                    }
                }
            }

            ClaimResponse.ItemComponent itemComponent = createItemComponent(item, reviewAction,
                    FhirUtils.getIdFromResource(claim));
            items.add(itemComponent);

            // Update the ClaimItem database
            Map<String, Object> constraintMap = new HashMap<String, Object>();
            constraintMap.put("id", FhirUtils.getIdFromResource(claim));
            constraintMap.put("sequence", item.getSequence());
            App.getDB().update(Table.CLAIM_ITEM, constraintMap,
                    Collections.singletonMap("outcome", reviewAction.value()));
        }
        return items;
    }

    /**
     * Set the item for a ClaimResponse ItemComponent based on the submitted item
     * and the outcome
     * 
     * @param item   - the ItemComponent from the Claim
     * @param action - ReviewAction representing the outcome of the claim item
     * @param id     - the ClaimResponse ID (used to set the identifier system)
     * @return ClaimResponse ItemComponent with appropriate elements set
     */
    private static ClaimResponse.ItemComponent createItemComponent(ItemComponent item, ReviewAction action, String id) {
        ClaimResponse.ItemComponent itemComponent = new ClaimResponse.ItemComponent();
        itemComponent.setItemSequence(item.getSequence());

        // Add the adjudication
        Coding adjudicationCoding = new Coding();
        adjudicationCoding.setCode("eligpercent");
        adjudicationCoding.setDisplay("Eligible %");
        adjudicationCoding.setSystem("http://terminology.hl7.org/CodeSystem/adjudication");
        CodeableConcept category = new CodeableConcept(adjudicationCoding);
        AdjudicationComponent adjudication = new AdjudicationComponent(category);
        Double adjudicationValue = action == ReviewAction.APPROVED ? 1.0 : 0.0;
        adjudication.setValue(adjudicationValue);
        itemComponent.addAdjudication(adjudication);

        if (action == ReviewAction.APPROVED) {
            // Add identifier for X12 mapping
            Identifier itemIdentifier = new Identifier();
            itemIdentifier.setSystem(App.getBaseUrl() + "/ClaimResponse/" + id);
            itemIdentifier.setValue(Integer.toString(item.getSequence()));
            itemComponent.addExtension(ITEM_REFERENCE_EXTENSION_URL, itemIdentifier);
        } else if (action == ReviewAction.DENIED || action == ReviewAction.PENDED)
            itemComponent.addExtension(REVIEW_ACTION_REASON_EXTENSION_URL, new StringType("X"));

        Extension reviewActionExtension = new Extension(REVIEW_ACTION_EXTENSION_URL);
        reviewActionExtension.setValue(action.value());
        itemComponent.addExtension(reviewActionExtension);

        return itemComponent;
    }

}