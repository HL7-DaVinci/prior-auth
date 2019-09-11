package org.hl7.davinci.priorauth;

import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/query")
public class DebugEndpoint {

  static final Logger logger = PALogger.getLogger();

  @GetMapping("/Bundle")
  public ResponseEntity<String> getBundles() {
    return query(Database.BUNDLE);
  }

  @GetMapping("/Claim")
  public ResponseEntity<String> getClaims() {
    return query(Database.CLAIM);
  }

  @GetMapping("/ClaimResponse")
  public ResponseEntity<String> getClaimResponses() {
    return query(Database.CLAIM_RESPONSE);
  }

  @GetMapping("/ClaimItem")
  public ResponseEntity<String> getClaimItems() {
    return query(Database.CLAIM_ITEM);
  }

  private ResponseEntity<String> query(String resource) {
    logger.info("GET /query/" + resource);
    if (App.debugMode) {
      return new ResponseEntity<>(App.getDB().generateAndRunQuery(resource), HttpStatus.OK);
      // return Response.ok(App.getDB().generateAndRunQuery(resource)).build();
    } else {
      logger.warning("DebugEndpoint::query disabled");
      return new ResponseEntity<>(HttpStatus.BAD_REQUEST);
      // return Response.status(Response.Status.BAD_REQUEST).build();
    }
  }
}
