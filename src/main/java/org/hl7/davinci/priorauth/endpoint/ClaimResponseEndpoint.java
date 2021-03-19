package org.hl7.davinci.priorauth.endpoint;

import java.util.HashMap;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.endpoint.Endpoint.RequestType;

/**
 * The ClaimResponse endpoint to READ, SEARCH for, and DELETE ClaimResponses to
 * submitted claims.
 */
@CrossOrigin
@RestController
@RequestMapping("/ClaimResponse")
public class ClaimResponseEndpoint {

  @GetMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> readClaimResponseJson(HttpServletRequest request,
      @RequestParam(name = "identifier", required = false) String id,
      @RequestParam(name = "patient.identifier") String patient,
      @RequestParam(name = "status", required = false) String status) {
    return readClaimResponse(id, patient, status, request, RequestType.JSON);
  }

  @GetMapping(value = "", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> readClaimResponseXml(HttpServletRequest request,
      @RequestParam(name = "identifier", required = false) String id,
      @RequestParam(name = "patient.identifier") String patient,
      @RequestParam(name = "status", required = false) String status) {
    return readClaimResponse(id, patient, status, request, RequestType.XML);
  }

  public ResponseEntity<String> readClaimResponse(String id, String patient, String status, HttpServletRequest request,
      RequestType requestType) {
    Map<String, Object> constraintMap = new HashMap<>();

    // get the claim id from the claim response id
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    String claimId = App.getDB().readString(Table.CLAIM_RESPONSE, constraintMap, "claimId");

    // get the most recent claim id
    claimId = App.getDB().getMostRecentId(claimId);

    // get the most recent claim response
    constraintMap.clear();
    if (claimId == null) {
      // no claim was found, call on the Endpoint.read to return the proper error
      constraintMap.put("id", id);
    } else {
      constraintMap.put("claimId", claimId);
    }
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Table.CLAIM_RESPONSE, constraintMap, request, requestType);
  }

  @CrossOrigin
  @DeleteMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> deleteClaimResponse(HttpServletRequest request,
      @RequestParam(name = "identifier") String id, @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Table.CLAIM_RESPONSE, request, RequestType.JSON);
  }

  @CrossOrigin
  @DeleteMapping(value = "", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> deleteClaimResponseXml(HttpServletRequest request,
      @RequestParam(name = "identifier") String id, @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Table.CLAIM_RESPONSE, request, RequestType.XML);
  }

}
