package org.hl7.davinci.priorauth;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MonoMeecrowave;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import ca.uhn.fhir.validation.ValidationResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RunWith(MonoMeecrowave.Runner.class)
public class ClaimEndpointTest {

  @ConfigurationInject
  private Meecrowave.Builder config;
  private static OkHttpClient client;
   
  @BeforeClass
  public static void setup() throws FileNotFoundException {
    client = new OkHttpClient();

    // Create a single test Claim
    Path modulesFolder = Paths.get("src/test/resources");
    Path fixture = modulesFolder.resolve("claim-minimal.json");
    FileInputStream inputStream = new FileInputStream(fixture.toString());
    Claim claim = (Claim) App.FHIR_CTX.newJsonParser().parseResource(inputStream);
    App.DB.write(Database.CLAIM, "minimal", claim);
  }

  @AfterClass
  public static void cleanup() {
    App.DB.delete(Database.CLAIM, "minimal");
  }

  @Test
  public void searchClaims() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can GET /fhir/Claim.
    Request request = new Request.Builder()
        .url(base + "/Claim")
        .header("Accept", "application/fhir+json")
        .build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is a JSON Bundle
    String body = response.body().string();
    Bundle bundle =
        (Bundle) App.FHIR_CTX.newJsonParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void searchClaimsXml() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can GET /fhir/Claim.
    Request request = new Request.Builder()
        .url(base + "/Claim")
        .header("Accept", "application/fhir+xml")
        .build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is an XML Bundle
    String body = response.body().string();
    Bundle bundle =
        (Bundle) App.FHIR_CTX.newXmlParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void claimExists() {
    Claim claim = (Claim) App.DB.read(Database.CLAIM, "minimal");
    Assert.assertNotNull(claim);
  }

  @Test
  public void getClaim() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can GET /fhir/Claim/minimal.
    Request request = new Request.Builder()
        .url(base + "/Claim/minimal")
        .header("Accept", "application/fhir+json")
        .build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is a JSON Bundle
    String body = response.body().string();
    Claim claim =
        (Claim) App.FHIR_CTX.newJsonParser().parseResource(body);
    Assert.assertNotNull(claim);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(claim);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getClaimXml() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can GET /fhir/Claim/minimal.
    Request request = new Request.Builder()
        .url(base + "/Claim/minimal")
        .header("Accept", "application/fhir+xml")
        .build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is an XML Bundle
    String body = response.body().string();
    Claim claim =
        (Claim) App.FHIR_CTX.newXmlParser().parseResource(body);
    Assert.assertNotNull(claim);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(claim);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getClaimThatDoesNotExist() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that non-existent Claim returns 404.
    Request request = new Request.Builder()
        .url(base + "/Claim/ClaimThatDoesNotExist")
        .build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(404, response.code());
  }
}
