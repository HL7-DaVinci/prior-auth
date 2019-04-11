package org.hl7.davinci.priorauth;

import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import static org.junit.Assert.*;

import java.io.IOException;

import org.apache.meecrowave.Meecrowave;
import org.apache.meecrowave.junit.MonoMeecrowave;
import org.apache.meecrowave.testing.ConfigurationInject;
import org.hl7.fhir.r4.model.CapabilityStatement;

@RunWith(MonoMeecrowave.Runner.class)
public class MetadataTest {

  @ConfigurationInject
  private Meecrowave.Builder config;
  private static OkHttpClient client;
   
  @BeforeClass
  public static void setup() {
    client = new OkHttpClient();
  }

  @Test
  public void getMetadata() throws IOException {
    String base = "http://localhost:" + config.getHttpPort();

    // Test that we can GET /fhir/metadata.
    Request request = new Request.Builder()
        .url(base + "/metadata")
        .build();
    Response response = client.newCall(request).execute();
    assertEquals(200, response.code());

    // Test the response is a JSON Capability Statement
    String body = response.body().string();
    CapabilityStatement capabilityStatement =
        (CapabilityStatement) App.FHIR_CTX.newJsonParser().parseResource(body);
    Assert.assertNotNull(capabilityStatement);

    // Test the response is VALID
    FhirValidator validator = App.FHIR_CTX.newValidator();
    validator.setValidateAgainstStandardSchema(true);
    validator.setValidateAgainstStandardSchematron(true);
    ValidationResult result = validator.validateWithResult(capabilityStatement);

    // If the validation failed, print out the errors before we fail the test.
    if (!result.isSuccessful()) {
      System.out.println(body);
      for (SingleValidationMessage message : result.getMessages()) {
        System.out.println(message.getSeverity() + ": " + message.getMessage());
      }
    }

    Assert.assertTrue(result.isSuccessful());
  }
}
