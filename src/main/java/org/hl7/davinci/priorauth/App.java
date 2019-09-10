package org.hl7.davinci.priorauth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import ca.uhn.fhir.context.FhirContext;

import java.util.Collections;
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
    server.setDefaultProperties(Collections.singletonMap("server.port", "9000"));
    server.run();

    // Assemble the microservice
    // Meecrowave.Builder builder = new Meecrowave.Builder();
    // builder.setHttpPort(9000);
    // builder.setScanningPackageIncludes("org.hl7.davinci.priorauth");
    // builder.setJaxrsMapping("/fhir/*");
    // builder.setJsonpPrettify(true);

    // // Launch the microservice
    // try (Meecrowave meecrowave = new Meecrowave(builder)) {
    // meecrowave.bake().await();
    // }
  }

  public static void initializeAppDB() {
    if (DB == null)
      DB = new Database();
  }

  public static Database getDB() {
    return DB;
  }
}
