package org.hl7.davinci.priorauth;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.davinci.priorauth.FhirUtils.ReviewAction;
import org.hl7.davinci.rules.PriorAuthRule;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Meta;
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
        response.setItem(setClaimResponseItems(claim));
        response.setDisposition(responseDisposition.value());
        response.setPreAuthRef(id);
        // TODO response.setPreAuthPeriod(period)?
        response.setId(id);

        response.addExtension(FhirUtils.REVIEW_ACTION_EXTENSION_URL,
                FhirUtils.dispositionToReviewAction(responseDisposition).value());

        Identifier identifier = new Identifier();
        identifier.setSystem(App.getBaseUrl());
        identifier.setValue(id);
        response.addIdentifier(identifier);

        if (responseDisposition == Disposition.DENIED || responseDisposition == Disposition.PENDING) {
            response.addExtension(FhirUtils.REVIEW_ACTION_REASON_EXTENSION_URL, new StringType("X"));
        }

        // Create the responseBundle
        Bundle responseBundle = new Bundle();
        responseBundle.setId(id);
        responseBundle.setType(Bundle.BundleType.COLLECTION);
        BundleEntryComponent responseEntry = responseBundle.addEntry();
        responseEntry.setResource(response);
        responseEntry.setFullUrl(App.getBaseUrl() + "/ClaimResponse/" + id);
        if (FhirUtils.isDifferential(bundle)) {
            logger.info("ClaimResponseFactory::Adding subsetted tag");
            Meta meta = new Meta();
            meta.addSecurity(FhirUtils.SECURITY_SYSTEM_URL, FhirUtils.SECURITY_SUBSETTED, FhirUtils.SECURITY_SUBSETTED);
            // responseBundle.setMeta(meta); // This causes an error for some reason
        }

        // TODO: update this to only add entries referenced in the ClaimResponse
        // resource
        for (BundleEntryComponent entry : bundle.getEntry()) {
            responseBundle.addEntry(entry);
        }

        // Store the claim response...
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
                Map<String, Object> constraintMap = new HashMap<String, Object>();
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
            PriorAuthRule rule = new PriorAuthRule("HomeOxygenTherapyPriorAuthRule");
            logger.info("ClaimResponseFactory::Created the rule!");
            return rule.computeDisposition(bundle);
        }
    }

    /**
     * Set the Items on the ClaimResponse indicating the adjudication of each one
     * 
     * @param claim - the initial Claim which contains the items
     * @return a list of ItemComponents to be added to the ClaimResponse.items field
     */
    private static List<ClaimResponse.ItemComponent> setClaimResponseItems(Claim claim) {
        List<ClaimResponse.ItemComponent> items = new ArrayList<ClaimResponse.ItemComponent>();

        // Set the Items on the ClaimResponse based on the initial Claim and the
        // Response disposition
        for (ItemComponent item : claim.getItem()) {
            // Read the item outcome from the database
            Map<String, Object> constraintMap = new HashMap<String, Object>();
            constraintMap.put("id", FhirUtils.getIdFromResource(claim));
            constraintMap.put("sequence", item.getSequence());
            String outcome = App.getDB().readString(Table.CLAIM_ITEM, constraintMap, "outcome");

            ClaimResponse.ItemComponent itemComponent = createItemComponent(item, ReviewAction.fromString(outcome),
                    FhirUtils.getIdFromResource(claim));
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
            itemComponent.addExtension(FhirUtils.ITEM_REFERENCE_EXTENSION_URL, itemIdentifier);
        } else if (action == ReviewAction.DENIED || action == ReviewAction.PENDED)
            itemComponent.addExtension(FhirUtils.REVIEW_ACTION_REASON_EXTENSION_URL, new StringType("X"));

        Extension reviewActionExtension = new Extension(FhirUtils.REVIEW_ACTION_EXTENSION_URL);
        reviewActionExtension.setValue(action.value());
        itemComponent.addExtension(reviewActionExtension);

        return itemComponent;
    }

}