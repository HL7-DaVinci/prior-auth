package org.hl7.davinci.priorauth;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.HashMap;

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
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import ca.uhn.fhir.validation.ValidationResult;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = "server.servlet.contextPath=/fhir")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class ClaimEndpointTest {

  @LocalServerPort
  private int port;

  @Autowired
  private WebApplicationContext wac;

  private static ResultMatcher cors = MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "*");
  private static ResultMatcher ok = MockMvcResultMatchers.status().isOk();
  private static ResultMatcher notFound = MockMvcResultMatchers.status().isNotFound();

  @BeforeClass
  public static void setup() throws FileNotFoundException {
    App.initializeAppDB();

    // Create a single test Claim
    Path modulesFolder = Paths.get("src/test/resources");
    Path fixture = modulesFolder.resolve("claim-minimal.json");
    FileInputStream inputStream = new FileInputStream(fixture.toString());
    Claim claim = (Claim) App.getFhirContext().newJsonParser().parseResource(inputStream);
    Map<String, Object> claimMap = new HashMap<String, Object>();
    claimMap.put("id", "minimal");
    claimMap.put("patient", "1");
    claimMap.put("status", FhirUtils.getStatusFromResource(claim));
    claimMap.put("resource", claim);
    App.getDB().write(Table.CLAIM, claimMap);
  }

  @AfterClass
  public static void cleanup() {
    App.getDB().delete(Table.CLAIM, "minimal", "1");
  }

  @Test
  public void searchClaims() throws Exception {
    // Test that we can GET /fhir/Claim.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get("/Claim?patient.identifier=1")
        .header("Accept", "application/fhir+json").header("Access-Control-Request-Method", "GET")
        .header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a JSON Bundle
    String body = mvcresult.getResponse().getContentAsString();
    Bundle bundle = (Bundle) App.getFhirContext().newJsonParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void searchClaimsXml() throws Exception {
    // Test that we can GET /fhir/Claim.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.get("/Claim?patient.identifier=1")
        .header("Accept", "application/fhir+xml").header("Access-Control-Request-Method", "GET")
        .header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a XML Bundle
    String body = mvcresult.getResponse().getContentAsString();
    Bundle bundle = (Bundle) App.getFhirContext().newXmlParser().parseResource(body);
    Assert.assertNotNull(bundle);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(bundle);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void claimExists() {
    Map<String, Object> constraintMap = new HashMap<String, Object>();
    constraintMap.put("id", "minimal");
    constraintMap.put("patient", "1");
    Claim claim = (Claim) App.getDB().read(Table.CLAIM, constraintMap);
    Assert.assertNotNull(claim);
  }

  @Test
  public void getClaim() throws Exception {
    // Test that we can GET /fhir/Claim/minimal.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
        .get("/Claim?identifier=minimal&patient.identifier=1").header("Accept", "application/fhir+json")
        .header("Access-Control-Request-Method", "GET").header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a JSON Bundle
    String body = mvcresult.getResponse().getContentAsString();
    Claim claim = (Claim) App.getFhirContext().newJsonParser().parseResource(body);
    Assert.assertNotNull(claim);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(claim);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getClaimXml() throws Exception {
    // Test that we can GET /fhir/Claim/minimal.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
        .get("/Claim?identifier=minimal&patient.identifier=1").header("Accept", "application/fhir+xml")
        .header("Access-Control-Request-Method", "GET").header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 200
    MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

    // Test the response is a XML Bundle
    String body = mvcresult.getResponse().getContentAsString();
    Claim claim = (Claim) App.getFhirContext().newXmlParser().parseResource(body);
    Assert.assertNotNull(claim);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(claim);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void getClaimThatDoesNotExist() throws Exception {
    // Test that non-existent Claim returns 404.
    DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
    MockMvc mockMvc = builder.build();
    MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
        .get("/Claim?identifier=ClaimThatDoesNotExist&patient.identifier=45").header("Accept", "application/fhir+json")
        .header("Access-Control-Request-Method", "GET").header("Origin", "http://localhost:" + port);

    // Test the response has CORS headers and returned status 404
    mockMvc.perform(requestBuilder).andExpect(notFound).andExpect(cors);
  }
}
