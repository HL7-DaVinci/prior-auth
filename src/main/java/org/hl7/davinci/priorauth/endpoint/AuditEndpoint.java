package org.hl7.davinci.priorauth.endpoint;

import org.hl7.davinci.priorauth.Database;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * The AuditEvent endpoint to READ for audit events.
 */
@CrossOrigin
@RestController
@RequestMapping("/AuditEvent")
public class AuditEndpoint {

  @GetMapping(value = "", produces = {MediaType.APPLICATION_JSON_VALUE, "application/fhir+json"})
  public ResponseEntity<String> readAuditJson(HttpServletRequest request,
                                              @RequestParam(name = "patient", required = false) String patient) {
    Map<String, Object> constraintMap = new HashMap<>();
    constraintMap.put("patient", patient);
    if (patient != null)
      constraintMap.put("patient", patient);
    return Endpoint.read(Database.Table.AUDIT, constraintMap, request, Endpoint.RequestType.JSON);
  }

  @GetMapping(value = "", produces = {MediaType.APPLICATION_XML_VALUE, "application/fhir+xml"})
  public ResponseEntity<String> readAuditXml(HttpServletRequest request,
                                             @RequestParam(name = "patient", required = false) String patient) {
    Map<String, Object> constraintMap = new HashMap<>();
    if (patient != null)
      constraintMap.put("patient", patient);
    return Endpoint.read(Database.Table.AUDIT, constraintMap, request, Endpoint.RequestType.XML);
  }
}
