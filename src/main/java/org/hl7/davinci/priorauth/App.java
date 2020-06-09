package org.hl7.davinci.priorauth;

import org.hl7.davinci.rules.PriorAuthRule;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import ca.uhn.fhir.context.FhirContext;

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

  public static boolean debugMode = false;

  private static String baseUrl;

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
    server.run();
  }

  public static void initializeAppDB() {
    if (DB == null) {
      DB = new Database();
      PriorAuthRule.populateRulesTable();
    }
  }

  public static Database getDB() {
    return DB;
  }

  /**
   * Set the base URI for the microservice. This is necessary so
   * Bundle.entry.fullUrl data is accurately populated.
   * 
   * @param base - from FhirUtils.setServiceBaseUrl(HttpServletRequest)
   */
  public static void setBaseUrl(String base) {
    baseUrl = base;
  }

  /**
   * Get the base URL for the microservice
   * 
   * @return the the base url
   */
  public static String getBaseUrl() {
    return baseUrl;
  }
}
