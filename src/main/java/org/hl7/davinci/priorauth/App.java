package org.hl7.davinci.priorauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import ca.uhn.fhir.context.FhirContext;

import java.util.HashMap;
import java.util.Map;
import java.util.Timer;

/**
 * The Da Vinci Prior Authorization Reference Implementation microservice is
 * launched with this App.
 */
@SpringBootApplication
public class App {

  /**
   * HAPI FHIR Context. HAPI FHIR warns that the context creation is expensive,
   * and should be performed per-application, not per-record.
   */
  public static final FhirContext FHIR_CTX = FhirContext.forR4();

  /**
   * Local database for FHIR resources.
   */
  private static Database DB;

  /**
   * Timer for scheduling tasks to be completed at a later time.
   */
  public static final Timer timer = new Timer();

  public static boolean debugMode = false;

  /**
   * Launch the Prior Authorization microservice.
   * 
   * @param args - ignored.
   */
  public static void main(String[] args) {
    if (args.length > 0) {
      if (args[0].equalsIgnoreCase("debug")) {
        debugMode = true;
      }
    }

    if (System.getenv("debug") != null) {
      if (System.getenv("debug").equalsIgnoreCase("true")) {
        debugMode = true;
      }
    }

    // Set the DB
    initializeAppDB();

    // Assemble the microservice
    SpringApplication server = new SpringApplication(App.class);
    Map<String, Object> defaultProperties = new HashMap<String, Object>();
    defaultProperties.put("server.port", "9000");
    defaultProperties.put("server.servlet.contextPath", "/fhir");
    server.setDefaultProperties(defaultProperties);
    server.run();
  }

  public static void initializeAppDB() {
    if (DB == null)
      DB = new Database();
  }

  public static Database getDB() {
    return DB;
  }
}
