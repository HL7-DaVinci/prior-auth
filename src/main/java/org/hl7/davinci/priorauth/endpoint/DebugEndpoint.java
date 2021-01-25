package org.hl7.davinci.priorauth.endpoint;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.Audit;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.PropertyProvider;
import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.ruleutils.CqlUtils;
import org.hl7.davinci.rules.PriorAuthRule;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;

@CrossOrigin
@RestController
@RequestMapping("/debug")
public class DebugEndpoint {

  static final Logger logger = PALogger.getLogger();

  @GetMapping("/Bundle")
  public ResponseEntity<String> getBundles(HttpServletRequest request) {
    return query(Table.BUNDLE, request);
  }

  @GetMapping("/Claim")
  public ResponseEntity<String> getClaims(HttpServletRequest request) {
    return query(Table.CLAIM, request);
  }

  @GetMapping("/ClaimResponse")
  public ResponseEntity<String> getClaimResponses(HttpServletRequest request) {
    return query(Table.CLAIM_RESPONSE, request);
  }

  @GetMapping("/ClaimItem")
  public ResponseEntity<String> getClaimItems(HttpServletRequest request) {
    return query(Table.CLAIM_ITEM, request);
  }

  @GetMapping("/Subscription")
  public ResponseEntity<String> getSubscription(HttpServletRequest request) {
    return query(Table.SUBSCRIPTION, request);
  }

  @GetMapping("/Rules")
  public ResponseEntity<String> getRules(HttpServletRequest request) {
    return query(Table.RULES, request);
  }

  @GetMapping("/Audit")
  public ResponseEntity<String> getAudit(HttpServletRequest request) {
    return query(Table.AUDIT, request);
  }

  @GetMapping("/Client")
  public ResponseEntity<String> getClient(HttpServletRequest request) {
    return query(Table.CLIENT, request);
  }

  @PostMapping("/PopulateDatabaseTestData")
  public ResponseEntity<String> populateDatabase(HttpServletRequest request) {
    if (App.isDebugModeEnabled()) {
      String description = "Populate database with test data in debug mode";
      Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, AuditEventOutcome.SUCCESS, null, request, description);
      return populateDB();
    } else {
      logger.warning("DebugEndpoint::populate datatbase with test data disabeled");
      String description = "Attempted to populate database with test data but app not in debug mode";
      Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, AuditEventOutcome.MINOR_FAILURE, null, request, description);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/PopulateRules")
  public ResponseEntity<String> populateRules(HttpServletRequest request) {
    if (App.isDebugModeEnabled()) {
      PriorAuthRule.populateRulesTable();
      String description = "Populate database with sample rules in debug mode";
      Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, AuditEventOutcome.SUCCESS, null, request, description);
      return new ResponseEntity<>(HttpStatus.OK);
    } else {
      logger.warning("DebugEndpoint::populate datatbase disabeled");
      String description = "Attempted to populate database with sample rules but app not in debug mode";
      Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, AuditEventOutcome.MINOR_FAILURE, null, request, description);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/Convert")
  public ResponseEntity<String> convertCqlToElm(HttpServletRequest request, HttpEntity<String> entity) {
    if (App.isDebugModeEnabled()) {
      String elm = CqlUtils.cqlToElm(entity.getBody(), CqlUtils.RequestType.XML);
      String description = "Convert cql into elm in debug mode";
      Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, AuditEventOutcome.SUCCESS, null, request, description);
      return ResponseEntity.status(HttpStatus.OK).body(elm);
    } else {
      logger.warning("DebugEndpoint::convert elm disabled");
      String description = "Attempted to convert cql into elm but app not in debug mode";
      Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, AuditEventOutcome.MINOR_FAILURE, null, request, description);
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }

  @PostMapping("/ConvertAll")
  public ResponseEntity<String> convertAllCqlToElm(HttpServletRequest request) {
    if (App.isDebugModeEnabled()) {
      String cdsLibraryPath = PropertyProvider.getProperty("CDS_library");
      File filePath = new File(cdsLibraryPath);

      File[] topics = filePath.listFiles();
      for (File topic : topics) {
        if (topic.isDirectory()) {
          String topicName = topic.getName();

          // Ignore shared folder and hidden folder
          if (!topicName.startsWith(".") && !topicName.equalsIgnoreCase("Shared")) {
            // Get the cql file(s)
            for (File file : topic.listFiles()) {
              // Consume the cql file and convert to elm
              if (file.getName().endsWith(".cql")) {
                try {
                  // Read the file
                  String cql = CqlUtils.readFile(topicName + "/" + file.getName());
                  String elm = CqlUtils.cqlToElm(cql, CqlUtils.RequestType.XML);

                  String elmFileName = file.toPath().toString().replaceAll(".cql", ".elm.xml");
                  FileWriter writer = new FileWriter(elmFileName);
                  writer.write(elm);
                  writer.close();
                  logger.info("DebugEndpoing::Converted elm:" + elmFileName);
                } catch (Exception e) {
                  logger.log(Level.SEVERE, "DebugEndpoint::convertAllCqlToElm", e);
                  String description = "Unable to convert " + file.getName() + " to elm";
                  Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, AuditEventOutcome.SERIOUS_FAILURE, null, request,
                      description);
                  return new ResponseEntity<>(HttpStatus.INTERNAL_SERVER_ERROR);
                }
              }
            }
          }
        }
      }

      String description = "Convert all CDS cql rule files to elm in debug mode";
      Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, AuditEventOutcome.SUCCESS, null, request, description);
      return new ResponseEntity<>(HttpStatus.OK);
    } else {
      logger.warning("DebugEndpoint::convert elm disabled");
      String description = "Attempted to convert all CDS cql rule files to elm but app not in debug mode";
      Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.E, AuditEventOutcome.MINOR_FAILURE, null, request, description);
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

      Map<String, Object> dataMap = new HashMap<>();
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
    return (Bundle) App.getFhirContext().newJsonParser().parseResource(inputStream);
  }

  private static boolean writeClaim(Bundle claimBundle, String related, String timestamp) {
    Claim claim = (Claim) claimBundle.getEntry().get(0).getResource();
    String id = FhirUtils.getIdFromResource(claim);
    String patient = FhirUtils.getPatientIdentifierFromBundle(claimBundle);
    String status = FhirUtils.getStatusFromResource(claim);
    App.getDB().delete(Table.CLAIM, id, patient);
    App.getDB().delete(Table.BUNDLE, id, patient);
    Map<String, Object> dataMap = new HashMap<>();
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

    Map<String, Object> dataMap = new HashMap<>();
    dataMap.put("id", id);
    dataMap.put("patient", patient);
    dataMap.put("claimId", claimId);
    dataMap.put("status", status);
    dataMap.put("outcome", outcome);
    dataMap.put("timestamp", timestamp);
    dataMap.put("resource", claimResponseBundle);

    return App.getDB().write(Table.CLAIM_RESPONSE, dataMap);
  }

  private ResponseEntity<String> query(Table table, HttpServletRequest request) {
    logger.info("GET /debug/" + table.value());
    if (App.isDebugModeEnabled()) {
      String description = "Read " + table.value() + " table in debug mode.";
      Audit.createAuditEvent(AuditEventType.QUERY, AuditEventAction.R, AuditEventOutcome.SUCCESS, null, request, description);
      return new ResponseEntity<>(App.getDB().generateAndRunQuery(table), HttpStatus.OK);
    } else {
      String description = "Attempted to read " + table.value() + " table but app is not in debug mode.";
      Audit.createAuditEvent(AuditEventType.QUERY, AuditEventAction.R, AuditEventOutcome.MINOR_FAILURE, null, request, description);
      logger.warning("DebugEndpoint::query disabled");
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
    }
  }
}