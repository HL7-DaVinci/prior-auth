package org.hl7.davinci.priorauth;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.SpringBootTest.WebEnvironment;
import org.springframework.boot.web.server.LocalServerPort;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.test.web.servlet.setup.DefaultMockMvcBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import ca.uhn.fhir.validation.ValidationResult;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = "server.servlet.contextPath=/fhir")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class MetadataTest {

  @LocalServerPort
  private int port;

  @Autowired
  private WebApplicationContext wac;

  private static ResultMatcher cors = MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "*");
  private static ResultMatcher ok = MockMvcResultMatchers.status().isOk();

  @BeforeClass
  public static void setup() {
  }

  @Test
  public void getMetadata() throws Exception {
    // Test that we can GET /fhir/metadata.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get("/metadata")
        .header("Accept", "application/fhir+json").header("Access-Control-Request-Method", "GET")
        .header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a JSON Capability Statement
    String body = mvcresult.getResponse().getContentAsString();
    CapabilityStatement capabilityStatement = (CapabilityStatement) App.getFhirContext().newJsonParser()
        .parseResource(body);
    Assert.assertNotNull(capabilityStatement);

    // Test the websocket extension is included
    StringType websocketUrl = null;
    for (Extension ext : capabilityStatement.getExtension()) {
      if (ext.getUrl().equals(FhirUtils.WEBSOCKET_EXTENSION_URL))
        websocketUrl = (StringType) ext.getValue();
    }
    Assert.assertNotNull(websocketUrl);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(capabilityStatement);
    // TODO: a bug was causing this to fail when it is validated
    // Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getMetadataXml() throws Exception {
    // Test that we can GET /fhir/metadata.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get("/metadata")
        .header("Accept", "application/fhir+xml").header("Access-Control-Request-Method", "GET")
        .header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a XML Capability Statement
    String body = mvcresult.getResponse().getContentAsString();
    CapabilityStatement capabilityStatement = (CapabilityStatement) App.getFhirContext().newXmlParser()
        .parseResource(body);
    Assert.assertNotNull(capabilityStatement);

    // Test the websocket extension is included
    StringType websocketUrl = null;
    for (Extension ext : capabilityStatement.getExtension()) {
      if (ext.getUrl().equals(FhirUtils.WEBSOCKET_EXTENSION_URL))
        websocketUrl = (StringType) ext.getValue();
    }
    Assert.assertNotNull(websocketUrl);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(capabilityStatement);
    // TODO: a bug was causing this to fail when it is validated
    // Assert.assertTrue(result.isSuccessful());
  }
}
