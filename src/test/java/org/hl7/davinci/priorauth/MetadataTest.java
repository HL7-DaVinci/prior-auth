package org.hl7.davinci.priorauth;

import java.io.IOException;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import ca.uhn.fhir.validation.ValidationResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = "server.servlet.contextPath=/fhir")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class MetadataTest {

  @LocalServerPort
  private int port;

  private static OkHttpClient client;

  @BeforeClass
  public static void setup() {
    client = new OkHttpClient();
  }

  @Test
  public void getMetadata() throws IOException {
    String base = "http://localhost:" + port + "/fhir";

    // Test that we can GET /fhir/metadata.
    Request request = new Request.Builder().url(base + "/metadata").header("Accept", "application/fhir+json").build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is a JSON Capability Statement
    String body = response.body().string();
    CapabilityStatement capabilityStatement = (CapabilityStatement) App.FHIR_CTX.newJsonParser().parseResource(body);
    Assert.assertNotNull(capabilityStatement);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(capabilityStatement);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getMetadataXml() throws IOException {
    String base = "http://localhost:" + port + "/fhir";

    // Test that we can GET /fhir/metadata.
    Request request = new Request.Builder().url(base + "/metadata").header("Accept", "application/fhir+xml").build();
    Response response = client.newCall(request).execute();
    Assert.assertEquals(200, response.code());

    // Test the response has CORS headers
    String cors = response.header("Access-Control-Allow-Origin");
    Assert.assertEquals("*", cors);

    // Test the response is an XML Capability Statement
    String body = response.body().string();
    CapabilityStatement capabilityStatement = (CapabilityStatement) App.FHIR_CTX.newXmlParser().parseResource(body);
    Assert.assertNotNull(capabilityStatement);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(capabilityStatement);
    Assert.assertTrue(result.isSuccessful());
  }
}
