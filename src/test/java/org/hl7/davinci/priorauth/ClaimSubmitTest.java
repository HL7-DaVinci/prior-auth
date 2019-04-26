package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MonoMeecrowave;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import ca.uhn.fhir.validation.ValidationResult;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@RunWith(MonoMeecrowave.Runner.class)
public class ClaimSubmitTest {

  public static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

  @ConfigurationInject
  private Meecrowave.Builder config;
  private static OkHttpClient client;

  /** List of resource IDs that will need to be cleaned up after the tests */
  private static List<String> resourceIds;
  /** Prior Authorization claim fixture */
  private static String completeClaim;
  private static String emptyBundle;
  private static String claimOnly;
  private static String bundleWithOnlyClaim;

  @BeforeClass
  public static void setup() throws IOException {
    client = new OkHttpClient();
    resourceIds = new ArrayList<String>();

    // Read in the test fixtures...
    Path modulesFolder = Paths.get("src/test/resources");
    Path fixture = modulesFolder.resolve("bundle-prior-auth.json");
    completeClaim = new String(Files.readAllBytes(fixture));

    fixture = modulesFolder.resolve("bundle-minimal.json");
    emptyBundle = new String(Files.readAllBytes(fixture));

    fixture = modulesFolder.resolve("claim-minimal.json");
    claimOnly = new String(Files.readAllBytes(fixture));

    fixture = modulesFolder.resolve("bundle-with-only-claim.json");
    bundleWithOnlyClaim = new String(Files.readAllBytes(fixture));
  }

  @AfterClass
  public static void cleanup() {
    for (String id : resourceIds) {
      System.out.println("Deleting Resources with ID = " + id);
      App.DB.delete(Database.BUNDLE, id);
      App.DB.delete(Database.CLAIM, id);
      App.DB.delete(Database.CLAIM_RESPONSE, id);
    }
  }

  @Test
  public void completeClaimValidation() {
    Bundle bundle =
        (Bundle) App.FHIR_CTX.newJsonParser().parseResource(completeClaim);
    Assert.assertNotNull(bundle);
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void submitCompleteClaim() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can POST /fhir/Claim/$submit
    RequestBody requestBody = RequestBody.create(JSON, completeClaim);
    Request request = new Request.Builder()
        .url(base + "/Claim/$submit")
        .post(requestBody)
        .build();
    Response response = client.newCall(request).execute();

    // Check Location header if it exists...
    String location = response.header("Location");
    if (location != null) {
      int index = location.indexOf("fhir/ClaimResponse/");
      if (index >= 0) {
        resourceIds.add(location.substring(index + 19));
      }
    }

    // Check that the claim succeeded
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is a JSON ClaimResponse
    String responseBody = response.body().string();
    ClaimResponse claimResponse =
        (ClaimResponse) App.FHIR_CTX.newJsonParser().parseResource(responseBody);
    Assert.assertNotNull(claimResponse);

    // Make sure we clean up afterwards...
    String id = claimResponse.getId().substring(14);
    resourceIds.add(id);

    // Test that the database contains the proper entries
    Assert.assertNotNull(App.DB.read(Database.BUNDLE, id));
    Assert.assertNotNull(App.DB.read(Database.CLAIM, id));
    Assert.assertNotNull(App.DB.read(Database.CLAIM_RESPONSE, id));

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(claimResponse);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void submitEmptyBundle() throws IOException {
    checkErrors(emptyBundle);
  }

  @Test
  public void submitClaimOnly() throws IOException {
    checkErrors(claimOnly);
  }

  @Test
  public void submitBundleWithOnlyClaim() throws IOException {
    checkErrors(bundleWithOnlyClaim);
  }

  private void checkErrors(String body) throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can POST /fhir/Claim/$submit
    RequestBody requestBody = RequestBody.create(JSON, body);
    Request request = new Request.Builder()
        .url(base + "/Claim/$submit")
        .post(requestBody)
        .build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(400, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is a JSON OperationOutcome
    String responseBody = response.body().string();
    OperationOutcome error =
        (OperationOutcome) App.FHIR_CTX.newJsonParser().parseResource(responseBody);
    Assert.assertNotNull(error);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(error);
    Assert.assertTrue(result.isSuccessful());
  }
}
