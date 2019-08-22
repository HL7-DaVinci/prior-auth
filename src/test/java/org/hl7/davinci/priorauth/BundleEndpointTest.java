package org.hl7.davinci.priorauth;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MonoMeecrowave;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.hl7.fhir.r4.model.Bundle;
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
public class BundleEndpointTest {

  @ConfigurationInject
  private Meecrowave.Builder config;
  private static OkHttpClient client;

  @BeforeClass
  public static void setup() throws FileNotFoundException {
    client = new OkHttpClient();

    // Create a single test Bundle
    Path modulesFolder = Paths.get("src/test/resources");
    Path fixture = modulesFolder.resolve("bundle-minimal.json");
    FileInputStream inputStream = new FileInputStream(fixture.toString());
    Bundle bundle = (Bundle) App.FHIR_CTX.newJsonParser().parseResource(inputStream);
    Map<String, Object> bundleMap = new HashMap<String, Object>();
    bundleMap.put("id", "minimal");
    bundleMap.put("patient", "1");
    bundleMap.put("status", Database.getStatusFromResource(bundle));
    bundleMap.put("resource", bundle);
    App.DB.write(Database.BUNDLE, bundleMap);
  }

  @AfterClass
  public static void cleanup() {
    App.DB.delete(Database.BUNDLE, "minimal", "1");
  }

  @Test
  public void searchBundles() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can GET /fhir/Bundle.
    Request request = new Request.Builder().url(base + "/Bundle?patient.identifier=1")
        .header("Accept", "application/fhir+json").build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is a JSON Bundle
    String body = response.body().string();
    Bundle bundle = (Bundle) App.FHIR_CTX.newJsonParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void searchBundlesXml() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can GET /fhir/Bundle.
    Request request = new Request.Builder().url(base + "/Bundle?patient.identifier=1")
        .header("Accept", "application/fhir+xml").build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is an XML Bundle
    String body = response.body().string();
    Bundle bundle = (Bundle) App.FHIR_CTX.newXmlParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void bundleExists() {
    Bundle bundle = (Bundle) App.DB.read(Database.BUNDLE, "minimal", "1");
    Assert.assertNotNull(bundle);
  }

  @Test
  public void getBundle() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can get fhir/Bundle/minimal
    Request request = new Request.Builder().url(base + "/Bundle?identifier=minimal&patient.identifier=1")
        .header("Accept", "application/fhir+json").build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is a JSON Bundle
    String body = response.body().string();
    Bundle bundle = (Bundle) App.FHIR_CTX.newJsonParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getBundleXml() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can get fhir/Bundle/minimal
    Request request = new Request.Builder().url(base + "/Bundle?identifier=minimal&patient.identifier=1")
        .header("Accept", "application/fhir+xml").build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is an XML Bundle
    String body = response.body().string();
    Bundle bundle = (Bundle) App.FHIR_CTX.newXmlParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getBundleThatDoesNotExist() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that non-existent Bundle returns 404.
    Request request = new Request.Builder()
        .url(base + "/Bundle?identifier=BundleThatDoesNotExist&patient.identifier=45").build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(404, response.code());
  }
}
