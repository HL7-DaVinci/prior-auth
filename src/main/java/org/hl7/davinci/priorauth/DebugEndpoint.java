package org.hl7.davinci.priorauth;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;

import ca.uhn.fhir.context.FhirContext;

@CrossOrigin
@RestController
@RequestMapping("/debug")
public class DebugEndpoint {

  static final Logger logger = PALogger.getLogger();

  @GetMapping("/Bundle")
  public ResponseEntity<String> getBundles() {
    return query(Table.BUNDLE);
  }

  @GetMapping("/Claim")
  public ResponseEntity<String> getClaims() {
    return query(Table.CLAIM);
  }

  @GetMapping("/ClaimResponse")
  public ResponseEntity<String> getClaimResponses() {
    return query(Table.CLAIM_RESPONSE);
  }

  @GetMapping("/ClaimItem")
  public ResponseEntity<String> getClaimItems() {
    return query(Table.CLAIM_ITEM);
  }

  @GetMapping("/Subscription")
  public ResponseEntity<String> getSubscription() {
    return query(Table.SUBSCRIPTION);
  }

  @PostMapping("/PopulateDatabaseTestData")
  public ResponseEntity<String> populateDatabase() {
    if (App.debugMode)
      return populateDB();
    else {
      logger.warning("DebugEndpoint::populate datatbase with test data disabeled");
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  private ResponseEntity<String> populateDB() {
    logger.info("DebugEndpoint::Prepopulating database with data");
    String responseData = "Success!";
    HttpStatus status = HttpStatus.OK;

    try {
      // Submit a claim and then update twice
      writeClaim(getResource("Claim.json"), null, "2200-09-09 15:23:34.1");
      writeClaimResponse(getResource("DeniedResponse.json"), "451fe716-4701-4cdf-b5bb-a6eedbe43bbb",
          "2200-09-09 15:23:39.5");
      writeClaim(getResource("ClaimUpdate1.json"), "451fe716-4701-4cdf-b5bb-a6eedbe43bbb", "2200-09-09 18:37:15.2");
      writeClaimResponse(getResource("GrantedResponse.json"), "ee7f76f8-5e4e-4733-8f85-3fe3c29cf30f",
          "2200-09-09 18:37:22.5");
      writeClaim(getResource("ClaimUpdate2.json"), "ee7f76f8-5e4e-4733-8f85-3fe3c29cf30f", "2200-09-10 08:43:32.9");
      writeClaimResponse(getResource("PendedClaimResponse.json"), "f57a4af7-e3b7-475e-9fae-b31cc0319e36",
          "2200-09-10 08:43:38.3");
      writeClaimResponse(getResource("PendedFinalResponse.json"), "f57a4af7-e3b7-475e-9fae-b31cc0319e36",
          "2200-09-10 10:55:03.0");

      Map<String, Object> dataMap = new HashMap<String, Object>();
      dataMap.put("id", "f57a4af7-e3b7-475e-9fae-b31cc0319e36");
      dataMap.put("sequence", "1");
      dataMap.put("status", "active");
      dataMap.put("timestamp", "2200-09-10 08:43:32.9");
      App.getDB().write(Table.CLAIM_ITEM, dataMap);
      dataMap.replace("sequence", "2");
      App.getDB().write(Table.CLAIM_ITEM, dataMap);
    } catch (FileNotFoundException e) {
      status = HttpStatus.BAD_REQUEST;
      responseData = "ERROR: Unable to read all resources to populate database";
      logger.log(Level.SEVERE, "DebugEndpoint::FileNotFoundException reading resource file to insert into database", e);
    }

    logger.info("DebugEndpoint::Prepopulating database complete");
    return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON).body(responseData);
  }

  private static Bundle getResource(String fileName) throws FileNotFoundException {
    java.nio.file.Path modulesFolder = Paths.get("src/main/resources/DatabaseResources");
    java.nio.file.Path fixture = modulesFolder.resolve(fileName);
    FileInputStream inputStream = new FileInputStream(fixture.toString());
    return (Bundle) FhirContext.forR4().newJsonParser().parseResource(inputStream);
  }

  private static boolean writeClaim(Bundle claimBundle, String related, String timestamp) {
    Claim claim = (Claim) claimBundle.getEntry().get(0).getResource();
    String id = FhirUtils.getIdFromResource(claim);
    String patient = FhirUtils.getPatientIdentifierFromBundle(claimBundle);
    String status = FhirUtils.getStatusFromResource(claim);
    App.getDB().delete(Table.CLAIM, id, patient);
    App.getDB().delete(Table.BUNDLE, id, patient);
    Map<String, Object> dataMap = new HashMap<String, Object>();
    dataMap.put("id", id);
    dataMap.put("patient", patient);
    dataMap.put("resource", claimBundle);
    dataMap.put("timestamp", timestamp);

    App.getDB().write(Table.BUNDLE, dataMap);

    dataMap.put("status", status);
    if (related != null)
      dataMap.put("related", related);

    dataMap.replace("resource", claim);
    return App.getDB().write(Table.CLAIM, dataMap);
  }

  private static boolean writeClaimResponse(Bundle claimResponseBundle, String claimId, String timestamp) {
    ClaimResponse claimResponse = (ClaimResponse) claimResponseBundle.getEntry().get(0).getResource();
    String id = FhirUtils.getIdFromResource(claimResponse);
    String patient = FhirUtils.getPatientIdentifierFromBundle(claimResponseBundle);
    String status = FhirUtils.getStatusFromResource(claimResponse);
    String outcome = claimResponse.getExtensionByUrl(FhirUtils.REVIEW_ACTION_EXTENSION_URL).getValue().primitiveValue();
    App.getDB().delete(Table.CLAIM_RESPONSE, id, patient);

    Map<String, Object> dataMap = new HashMap<String, Object>();
    dataMap.put("id", id);
    dataMap.put("patient", patient);
    dataMap.put("claimId", claimId);
    dataMap.put("status", status);
    dataMap.put("outcome", outcome);
    dataMap.put("timestamp", timestamp);
    dataMap.put("resource", claimResponseBundle);

    return App.getDB().write(Table.CLAIM_RESPONSE, dataMap);
  }

  private ResponseEntity<String> query(Table table) {
    logger.info("GET /debug/" + table.value());
    if (App.debugMode) {
      return new ResponseEntity<>(App.getDB().generateAndRunQuery(table), HttpStatus.OK);
    } else {
      logger.warning("DebugEndpoint::query disabled");
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }
}