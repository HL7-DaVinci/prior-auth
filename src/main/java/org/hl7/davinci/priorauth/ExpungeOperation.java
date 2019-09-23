package org.hl7.davinci.priorauth;

import java.util.logging.Logger;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fhir/$expunge")
public class ExpungeOperation {

  static final Logger logger = PALogger.getLogger();

  @GetMapping("")
  public ResponseEntity<String> getExpunge() {
    return expungeDatabase();
  }

  @PostMapping("")
  public ResponseEntity<String> postExpunge() {
    return expungeDatabase();
  }

  /**
   * Delete all the data in the database. Useful if the demonstration database
   * becomes too large and unwieldy.
   * 
   * @return - HTTP 200
   */
  private ResponseEntity<String> expungeDatabase() {
    logger.info("GET/POST /$expunge");
    if (App.debugMode) {
      // Cascading delete of everything...
      App.getDB().delete(Database.BUNDLE);
      App.getDB().delete(Database.CLAIM);
      App.getDB().delete(Database.CLAIM_ITEM);
      App.getDB().delete(Database.CLAIM_RESPONSE);
      App.getDB().delete(Database.SUBSCRIPTION);
      return ResponseEntity.ok().body("Expunge success!");
    } else {
      logger.warning("ExpungeOperation::expungeDatabase:query disabled");
      return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Expunge operation disabled");
    }
  }
}
