package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.hl7.davinci.priorauth.Endpoint.RequestType;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.Coverage;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.hl7.fhir.r4.model.Subscription.SubscriptionStatus;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

public class FhirUtilsTest {

    private static Claim claim;
    private static ClaimResponse claimResponse;
    private static Bundle bundleRequest;
    private static Bundle bundleResponse;

    @BeforeClass
    public static void setup() throws IOException {
        // Read in the test fixtures...
        Path modulesFolder = Paths.get("src/test/resources");
        Path fixture = modulesFolder.resolve("claim-only.json");
        String fixtureStr = new String(Files.readAllBytes(fixture));
        claim = (Claim) App.FHIR_CTX.newJsonParser().parseResource(fixtureStr);

        fixture = modulesFolder.resolve("claimresponse-only.json");
        fixtureStr = new String(Files.readAllBytes(fixture));
        claimResponse = (ClaimResponse) App.FHIR_CTX.newJsonParser().parseResource(fixtureStr);

        fixture = modulesFolder.resolve("bundle-request.json");
        fixtureStr = new String(Files.readAllBytes(fixture));
        bundleRequest = (Bundle) App.FHIR_CTX.newJsonParser().parseResource(fixtureStr);

        fixture = modulesFolder.resolve("bundle-response.json");
        fixtureStr = new String(Files.readAllBytes(fixture));
        bundleResponse = (Bundle) App.FHIR_CTX.newJsonParser().parseResource(fixtureStr);
    }

    @Test
    public void testGetStatusFromResource() {
        // Validate Claim/ClaimResponse status is correct
        Assert.assertEquals("active", FhirUtils.getStatusFromResource(claim));
        Assert.assertEquals("active", FhirUtils.getStatusFromResource(claimResponse));

        // Validate other resource type status is correct
        Coverage coverage = (Coverage) bundleRequest.getEntry().get(4).getResource();
        Assert.assertEquals("active", FhirUtils.getStatusFromResource(coverage));

        // Validate resource without status is correct
        Assert.assertEquals("unknown", FhirUtils.getStatusFromResource(bundleRequest));
    }

    @Test
    public void testGetPatientIdentifierFromBundle() {
        Assert.assertEquals("12345678901", FhirUtils.getPatientIdentifierFromBundle(bundleRequest));
        Assert.assertEquals("12345678901", FhirUtils.getPatientIdentifierFromBundle(bundleResponse));
    }

    @Test
    public void testGetReviewActionFromClaimResponse() {
        Assert.assertEquals("A1", FhirUtils.getReviewActionFromClaimResponse(claimResponse));
        Assert.assertEquals("A1", FhirUtils
                .getReviewActionFromClaimResponse(FhirUtils.getClaimResponseFromResponseBundle(bundleResponse)));
    }

    @Test
    public void testGetClaimResponseFromResponseBundle() {
        // Validate ClaimResponse found in bundle
        ClaimResponse foundResponse = FhirUtils.getClaimResponseFromResponseBundle(bundleResponse);
        Assert.assertNotNull(foundResponse);
        Assert.assertEquals("1", FhirUtils.getIdFromResource(foundResponse));
    }

    @Test
    public void testGetClaimFromRequestBundle() {
        // Validate Claim found in bundle
        Claim foundClaim = FhirUtils.getClaimFromRequestBundle(bundleRequest);
        Assert.assertNotNull(foundClaim);
        Assert.assertEquals("1", FhirUtils.getIdFromResource(foundClaim));
    }

    @Test
    public void testGetEntryComponentFromBundle() {
        // Validate BundleEntryComponent found is the Claim with id "1"
        BundleEntryComponent bec = FhirUtils.getEntryComponentFromBundle(bundleRequest, ResourceType.Claim, "1");
        Assert.assertNotNull(bec);
        Assert.assertNotNull(bec.getResource());
        Assert.assertEquals(ResourceType.Claim, bec.getResource().getResourceType());

        // Validate BundleEntryComponent is null for id which does not exist
        bec = FhirUtils.getEntryComponentFromBundle(bundleRequest, ResourceType.Organization, "id-does-not-exist");
        Assert.assertNull(bec);
    }

    @Test
    public void testGetIdFromResource() {
        // Validate id from resources are correct
        Assert.assertEquals("1", FhirUtils.getIdFromResource(claim));
        Assert.assertEquals("1", FhirUtils.getIdFromResource(claimResponse));
        Assert.assertEquals("pa-request-example-referral", FhirUtils.getIdFromResource(bundleRequest));
        Assert.assertEquals("pa-response-example-referral", FhirUtils.getIdFromResource(bundleResponse));
    }

    @Test
    public void testJson() {
        Bundle bundle = new Bundle();
        bundle.setType(BundleType.SEARCHSET);
        bundle.setTotal(0);

        String json = FhirUtils.json(bundle);
        Assert.assertNotNull(json);
    }

    @Test
    public void testXml() {
        Bundle bundle = new Bundle();
        bundle.setType(BundleType.SEARCHSET);
        bundle.setTotal(0);

        String xml = FhirUtils.xml(bundle);
        Assert.assertNotNull(xml);
    }

    @Test
    public void testFormatResourceStatus() {
        // Validate the status from resources are correct
        Assert.assertEquals("active", FhirUtils.formatResourceStatus(ClaimStatus.ACTIVE));
        Assert.assertEquals("draft", FhirUtils.formatResourceStatus(ClaimStatus.DRAFT));
        Assert.assertEquals("cancelled", FhirUtils.formatResourceStatus(ClaimStatus.CANCELLED));
        Assert.assertEquals("entered in error", FhirUtils.formatResourceStatus(ClaimStatus.ENTEREDINERROR));

        Assert.assertEquals("active", FhirUtils.formatResourceStatus(SubscriptionStatus.ACTIVE));
        Assert.assertEquals("error", FhirUtils.formatResourceStatus(SubscriptionStatus.ERROR));
        Assert.assertEquals("off", FhirUtils.formatResourceStatus(SubscriptionStatus.OFF));
        Assert.assertEquals("requested", FhirUtils.formatResourceStatus(SubscriptionStatus.REQUESTED));
    }

    @Test
    public void testGetFormattedData() {
        // Validate data formatted in JSON correctly
        String json = FhirUtils.getFormattedData(claim, RequestType.JSON);
        Assert.assertNotNull(App.FHIR_CTX.newJsonParser().parseResource(json));

        // Validate data formatted in XML correctly
        String xml = FhirUtils.getFormattedData(claim, RequestType.XML);
        Assert.assertNotNull(App.FHIR_CTX.newXmlParser().parseResource(xml));
    }

    @Test
    public void testBuildOutcome() {
        OperationOutcome.IssueSeverity severity = OperationOutcome.IssueSeverity.ERROR;
        OperationOutcome.IssueType type = OperationOutcome.IssueType.INVALID;
        String message = "test";
        OperationOutcome outcome = FhirUtils.buildOutcome(severity, type, message);
        Assert.assertNotNull(outcome);
        Assert.assertEquals(severity, outcome.getIssueFirstRep().getSeverity());
        Assert.assertEquals(type, outcome.getIssueFirstRep().getCode());
        Assert.assertEquals(message, outcome.getIssueFirstRep().getDiagnostics());
    }
}