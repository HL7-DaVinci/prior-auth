package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.dstu3.model.codesystems.IssueSeverity;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Subscription;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Subscription.SubscriptionStatus;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;
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

import ca.uhn.fhir.validation.ValidationResult;

@RunWith(SpringRunner.class)
@TestPropertySource(properties = "server.servlet.contextPath=/fhir")
@SpringBootTest(webEnvironment = WebEnvironment.RANDOM_PORT)
public class SubscriptionEndpointTest {

    @LocalServerPort
    private int port;

    @Autowired
    private WebApplicationContext wac;

    private static ResultMatcher cors = MockMvcResultMatchers.header().string("Access-Control-Allow-Origin", "*");
    private static ResultMatcher ok = MockMvcResultMatchers.status().isOk();
    private static ResultMatcher badRequest = MockMvcResultMatchers.status().isBadRequest();

    private static String claim;
    private static String subscriptionEmail;
    private static String subscriptionPended;
    private static String subscriptionGranted;

    @BeforeClass
    public static void setup() throws IOException {
        App.initializeAppDB();

        Path modulesFolder = Paths.get("src/test/resources");
        Path fixture = modulesFolder.resolve("subscription-resthook.json");
        subscriptionPended = new String(Files.readAllBytes(fixture));
        fixture = modulesFolder.resolve("subscription-websocket.json");
        subscriptionGranted = new String(Files.readAllBytes(fixture));
        fixture = modulesFolder.resolve("subscription-email.json");
        subscriptionEmail = new String(Files.readAllBytes(fixture));

        // Add Claim to the Database so a ClaimResponse can be added
        fixture = modulesFolder.resolve("claim-minimal.json");
        claim = new String(Files.readAllBytes(fixture));
        Map<String, Object> claimMap = new HashMap<String, Object>();
        claimMap.put("id", "minimal-granted");
        claimMap.put("patient", "pat013");
        claimMap.put("status", "active");
        claimMap.put("resource", claim);
        App.getDB().write(Table.CLAIM, claimMap);

        // Add Claim to the Database so a ClaimResponse can be added
        fixture = modulesFolder.resolve("claim-minimal.json");
        claim = new String(Files.readAllBytes(fixture));
        claimMap = new HashMap<String, Object>();
        claimMap.put("id", "minimal-pended");
        claimMap.put("patient", "pat013");
        claimMap.put("status", "active");
        claimMap.put("resource", claim);
        App.getDB().write(Table.CLAIM, claimMap);

        // Add a granted ClaimResponse to the Database so a Subscription can be added
        fixture = modulesFolder.resolve("claimresponse-pended.json");
        String claimResponse = new String(Files.readAllBytes(fixture));
        Map<String, Object> claimResponseMap = new HashMap<String, Object>();
        claimResponseMap.put("id", "granted");
        claimResponseMap.put("claimId", "minimal-granted");
        claimResponseMap.put("patient", "pat013");
        claimResponseMap.put("status", "active");
        claimResponseMap.put("outcome", "A1");
        claimResponseMap.put("resource", claimResponse);
        App.getDB().write(Table.CLAIM_RESPONSE, claimResponseMap);

        // Add a pended ClaimResponse to the Database so a Subscription can be added
        fixture = modulesFolder.resolve("claimresponse-pended.json");
        claimResponse = new String(Files.readAllBytes(fixture));
        claimResponseMap = new HashMap<String, Object>();
        claimResponseMap.put("id", "pended");
        claimResponseMap.put("claimId", "minimal-pended");
        claimResponseMap.put("patient", "pat013");
        claimResponseMap.put("status", "active");
        claimResponseMap.put("outcome", "A4");
        claimResponseMap.put("resource", claimResponse);
        App.getDB().write(Table.CLAIM_RESPONSE, claimResponseMap);

        // Add a resthook subscription to the Database
        Map<String, Object> subscriptionMap = new HashMap<String, Object>();
        subscriptionMap.put("id", "minimal");
        subscriptionMap.put("claimResponseId", "pended");
        subscriptionMap.put("patient", "pat013");
        subscriptionMap.put("status", "active");
        subscriptionMap.put("resource", subscriptionPended);
        App.getDB().write(Table.SUBSCRIPTION, subscriptionMap);
    }

    @AfterClass
    public static void cleanup() {
        App.getDB().delete(Table.CLAIM);
        App.getDB().delete(Table.CLAIM_RESPONSE);
        App.getDB().delete(Table.SUBSCRIPTION);
    }

    @Test
    public void submitSubscriptionToPendedClaimTest() throws Exception {
        // Test that we can POST /fhir/Subscription
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post("/Subscription")
                .content(subscriptionPended).header("Content-Type", "application/fhir+json")
                .header("Access-Control-Request-Method", "POST").header("Origin", "http://localhost:" + port);

        // Test the response has CORS headers and returned status 200
        MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

        // Test the response is a JSON Subscription
        String responseBody = mvcresult.getResponse().getContentAsString();
        Subscription subscriptionResponse = (Subscription) App.getFhirContext().newJsonParser()
                .parseResource(responseBody);
        Assert.assertNotNull(subscriptionResponse);

        // Test the Subscription has an ID and the status is active
        Assert.assertNotNull(FhirUtils.getIdFromResource(subscriptionResponse));
        Assert.assertEquals(SubscriptionStatus.ACTIVE, subscriptionResponse.getStatus());

        // Test that the database contains the proper entries
        Map<String, Object> constraintMap = new HashMap<String, Object>();
        constraintMap.put("claimResponseId", "pended");
        constraintMap.put("patient", "pat013");
        Assert.assertNotNull(App.getDB().read(Table.SUBSCRIPTION, constraintMap));

        // Validate the response
        ValidationResult result = ValidationHelper.validate(subscriptionResponse);
        Assert.assertTrue(result.isSuccessful());
    }

    @Test
    public void submitSubscriptionToGrantedClaimTest() throws Exception {
        // Test that we can POST /fhir/Subscription
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post("/Subscription")
                .content(subscriptionGranted).header("Content-Type", "application/fhir+json")
                .header("Access-Control-Request-Method", "POST").header("Origin", "http://localhost:" + port);

        // Test the response has CORS headers and returned status 400
        MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(badRequest).andExpect(cors).andReturn();

        // Test the response is a JSON OperationOutcome
        String responseBody = mvcresult.getResponse().getContentAsString();
        OperationOutcome subscriptionResponse = (OperationOutcome) App.getFhirContext().newJsonParser()
                .parseResource(responseBody);
        Assert.assertNotNull(subscriptionResponse);

        // Validate the response
        ValidationResult result = ValidationHelper.validate(subscriptionResponse);
        Assert.assertTrue(result.isSuccessful());
    }

    @Test
    public void submitWrongResource() throws Exception {
        // Test posting the wrong resource gives an OperationOutcome
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post("/Subscription").content(claim)
                .header("Content-Type", "application/fhir+json").header("Access-Control-Request-Method", "POST")
                .header("Origin", "http://localhost:" + port);

        // Test the response has CORS headers and returned status 400
        MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(badRequest).andExpect(cors).andReturn();

        // Test the response is a JSON OperationOutcome
        String responseBody = mvcresult.getResponse().getContentAsString();
        OperationOutcome subscriptionResponse = (OperationOutcome) App.getFhirContext().newJsonParser()
                .parseResource(responseBody);
        Assert.assertNotNull(subscriptionResponse);

        // Validate the response
        ValidationResult result = ValidationHelper.validate(subscriptionResponse);
        Assert.assertTrue(result.isSuccessful());
    }

    @Test
    public void submitInvalidSubscriptionType() throws Exception {
        // Test a subscription with an invalid channel type fails
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.post("/Subscription")
                .content(subscriptionEmail).header("Content-Type", "application/fhir+json")
                .header("Access-Control-Request-Method", "POST").header("Origin", "http://localhost:" + port);

        // Test the response has CORS headers and returned status 400
        MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(badRequest).andExpect(cors).andReturn();

        // Test the response is a JSON OperationOutcome
        String responseBody = mvcresult.getResponse().getContentAsString();
        OperationOutcome subscriptionResponse = (OperationOutcome) App.getFhirContext().newJsonParser()
                .parseResource(responseBody);
        Assert.assertNotNull(subscriptionResponse);

        // Validate the response
        ValidationResult result = ValidationHelper.validate(subscriptionResponse);
        Assert.assertTrue(result.isSuccessful());
    }

    @Test
    public void deleteSubscription() throws Exception {
        // Test we can DELETE /fhir/Subscription
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.delete("/Subscription")
                .param("identifier", "minimal").param("patient.identifier", "pat013")
                .header("Content-Type", "application/fhir+json").header("Access-Control-Request-Method", "DELETE")
                .header("Origin", "http://localhost:" + port);

        // Test the response has CORS headers and returned status 200
        MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

        // Test the response is a JSON OperationOutcome
        String responseBody = mvcresult.getResponse().getContentAsString();
        OperationOutcome subscriptionResponse = (OperationOutcome) App.getFhirContext().newJsonParser()
                .parseResource(responseBody);
        Assert.assertNotNull(subscriptionResponse);

        // Validate there no errors in the outcome
        for (OperationOutcomeIssueComponent issue : subscriptionResponse.getIssue()) {
            Assert.assertNotEquals(issue.getSeverity(), IssueSeverity.FATAL);
            Assert.assertNotEquals(issue.getSeverity(), IssueSeverity.ERROR);
        }

        // Validate the response
        ValidationResult result = ValidationHelper.validate(subscriptionResponse);
        Assert.assertTrue(result.isSuccessful());
    }

    @Test
    public void searchSubscriptions() throws Exception {
        // Test that we can GET /fhir/Subscription.
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/Subscription?patient.identifier=pat013").header("Accept", "application/fhir+json")
                .header("Access-Control-Request-Method", "GET").header("Origin", "http://localhost:" + port);

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
    public void searchSubscriptionsXml() throws Exception {
        // Test that we can GET /fhir/Subscription.
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/Subscription?patient.identifier=pat013").header("Accept", "application/fhir+xml")
                .header("Access-Control-Request-Method", "GET").header("Origin", "http://localhost:" + port);

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
    public void subscriptionExists() {
        Map<String, Object> constraintMap = new HashMap<String, Object>();
        constraintMap.put("id", "minimal");
        constraintMap.put("patient", "pat013");
        Subscription subscription = (Subscription) App.getDB().read(Table.SUBSCRIPTION, constraintMap);
        Assert.assertNotNull(subscription);
    }

    @Test
    public void getSubscription() throws Exception {
        // Test that we can get fhir/Subscription/minimal
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/Subscription?identifier=pended&patient.identifier=pat013")
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
    public void getSubscriptionXml() throws Exception {
        // Test that we can get fhir/Subscription/minimal
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/Subscription?identifier=pended&patient.identifier=pat013")
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
    public void getSubscriptionThatDoesNotExist() throws Exception {
        // Test that non-existent Subscription returns 200 and an empty bundle
        DefaultMockMvcBuilder builder = MockMvcBuilders.webAppContextSetup(wac);
        MockMvc mockMvc = builder.build();
        MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders
                .get("/Subscription?identifier=ClaimResponseThatDoesNotExist&patient.identifier=45")
                .header("Accept", "application/fhir+json").header("Access-Control-Request-Method", "GET")
                .header("Origin", "http://localhost:" + port);

        // Test the response has CORS headers and returned status 200
        MvcResult mvcresult = mockMvc.perform(requestBuilder).andExpect(ok).andExpect(cors).andReturn();

        // Test the response is a JSON Bundle
        String body = mvcresult.getResponse().getContentAsString();
        Bundle bundle = (Bundle) App.getFhirContext().newJsonParser().parseResource(body);
        Assert.assertNotNull(bundle);

        // Validate the bundle is empty
        Assert.assertEquals(0, bundle.getTotal());
    }
}