package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.davinci.priorauth.FhirUtils.ReviewAction;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.validation.ValidationResult;

public class ClaimResponseFactoryTest {

    private static Claim claim;
    private static Bundle bundle;

    private static String id = "001";
    private static String patient = "pat013";
    private static ClaimResponseStatus status = ClaimResponseStatus.ACTIVE;

    @BeforeClass
    public static void setupClass() throws IOException {
        App.initializeAppDB();

        // Read in the test fixtures...
        Path modulesFolder = Paths.get("src/test/resources");
        Path fixture = modulesFolder.resolve("bundle-items.json");
        String fixtureStr = new String(Files.readAllBytes(fixture));
        bundle = (Bundle) App.getFhirContext().newJsonParser().parseResource(fixtureStr);
        claim = FhirUtils.getClaimFromRequestBundle(bundle);
    }

    @Before
    public void setup() throws IOException {
        // Insert Claim into DB
        Map<String, Object> dataMap = new HashMap<String, Object>();
        dataMap.put("id", id);
        dataMap.put("patient", patient);
        dataMap.put("status", "active");
        dataMap.put("resource", claim);
        App.getDB().write(Table.CLAIM, dataMap);

        // Insert ClaimItems into DB
        Map<String, Object> icDataMap = new HashMap<String, Object>();
        icDataMap.put("id", id);
        icDataMap.put("status", "active");
        icDataMap.put("sequence", null);
        icDataMap.put("outcome", "A1");
        for (org.hl7.fhir.r4.model.Claim.ItemComponent ic : claim.getItem()) {
            icDataMap.replace("sequence", ic.getSequence());
            App.getDB().write(Table.CLAIM_ITEM, icDataMap);
        }
    }

    @After
    public void cleanup() {
        App.getDB().delete(Table.CLAIM);
        App.getDB().delete(Table.CLAIM_ITEM);
        App.getDB().delete(Table.CLAIM_RESPONSE);
    }

    @Test
    public void grantedClaim() {
        Disposition disposition = Disposition.GRANTED;
        ReviewAction reviewAction = ReviewAction.APPROVED;
        validateClaimResponse(disposition, reviewAction);
    }

    @Test
    public void deniedClaim() {
        Disposition disposition = Disposition.DENIED;
        ReviewAction reviewAction = ReviewAction.DENIED;
        validateClaimResponse(disposition, reviewAction);
    }

    @Test
    public void partialClaim() {
        Disposition disposition = Disposition.PARTIAL;
        ReviewAction reviewAction = ReviewAction.PARTIAL;
        validateClaimResponse(disposition, reviewAction);
    }

    @Test
    public void pendedClaim() {
        Disposition disposition = Disposition.PENDING;
        ReviewAction reviewAction = ReviewAction.PENDED;
        validateClaimResponse(disposition, reviewAction);
    }

    @Test
    public void cancelledClaim() {
        Disposition disposition = Disposition.CANCELLED;
        ReviewAction reviewAction = ReviewAction.CANCELLED;
        validateClaimResponse(disposition, reviewAction);
    }

    private void validateClaimResponse(Disposition disposition, ReviewAction reviewAction) {
        // Generate and store the response
        Bundle responseBundle = ClaimResponseFactory.generateAndStoreClaimResponse(bundle, claim, id, disposition,
                status, patient, false);

        // Validate the response Bundle
        ValidationResult result = ValidationHelper.validate(responseBundle);
        Assert.assertTrue(result.isSuccessful());

        // Validate ClaimResponse
        ClaimResponse claimResponse = FhirUtils.getClaimResponseFromResponseBundle(responseBundle);
        Assert.assertNotNull(claimResponse);
        Assert.assertEquals(status, claimResponse.getStatus());
        Assert.assertEquals(disposition.value(), claimResponse.getDisposition());
        Assert.assertTrue(claimResponse.hasIdentifier());

        if (disposition == Disposition.DENIED || disposition == Disposition.GRANTED
                || disposition == Disposition.CANCELLED)
            Assert.assertEquals(RemittanceOutcome.COMPLETE, claimResponse.getOutcome());
        else if (disposition == Disposition.PARTIAL)
            Assert.assertEquals(RemittanceOutcome.PARTIAL, claimResponse.getOutcome());
        else if (disposition == Disposition.PENDING)
            Assert.assertEquals(RemittanceOutcome.QUEUED, claimResponse.getOutcome());

        // Validate ClaimResponse table updated
        Bundle readBundle = (Bundle) App.getDB().read(Table.CLAIM_RESPONSE, Collections.singletonMap("id", id));
        Assert.assertNotNull(readBundle);
    }

}