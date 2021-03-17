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
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.endpoint.Endpoint.RequestType;

/**
 * The Bundle endpoint to READ, SEARCH for, and DELETE submitted Bundles.
 */
@CrossOrigin
@RestController
@RequestMapping("/Bundle")
public class BundleEndpoint {

  @GetMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> readBundle(HttpServletRequest request,
      @RequestParam(name = "identifier", required = false) String id,
      @RequestParam(name = "patient.identifier") String patient) {
    Map<String, Object> constraintMap = new HashMap<>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    return Endpoint.read(Table.BUNDLE, constraintMap, request, RequestType.JSON);
  }

  @GetMapping(value = "", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> readBundleXml(HttpServletRequest request,
      @RequestParam(name = "identifier", required = false) String id,
      @RequestParam(name = "patient.identifier") String patient) {
    Map<String, Object> constraintMap = new HashMap<>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    return Endpoint.read(Table.BUNDLE, constraintMap, request, RequestType.XML);
  }

  @CrossOrigin
  @DeleteMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> deleteBundle(HttpServletRequest request, @RequestParam(name = "identifier") String id,
      @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Table.BUNDLE, request, RequestType.JSON);
  }

  @CrossOrigin
  @DeleteMapping(value = "", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> deleteBundleXml(HttpServletRequest request,
      @RequestParam(name = "identifier") String id, @RequestParam(name = "patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Table.BUNDLE, request, RequestType.XML);
  }
}
