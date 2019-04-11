package org.hl7.davinci.priorauth;

import org.apache.meecrowave.Meecrowave;
import ca.uhn.fhir.context.FhirContext;

/**
 * The Da Vinci Prior Authorization Reference Implementation
 * microservice is launched with this App.
 */
public class App {

  /**
   * HAPI FHIR Context.
   * HAPI FHIR warns that the context creation is expensive, and should be performed
   * per-application, not per-record.
   */
  public static final FhirContext FHIR_CTX = FhirContext.forR4();

  /**
   * Launch the Prior Authorization microservice.
   * @param args - ignored.
   */
  public static void main(String[] args) {
    // Assemble the microservice
    Meecrowave.Builder builder = new Meecrowave.Builder();
    builder.setHttpPort(9000);
    builder.setScanningPackageIncludes("org.hl7.davinci.priorauth");
    builder.setJaxrsMapping("/fhir/*");
    builder.setJsonpPrettify(true);

    // Launch the microservice
    try (Meecrowave meecrowave = new Meecrowave(builder)) {
      meecrowave.bake().await();
    }
  }
}
