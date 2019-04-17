package org.hl7.davinci.priorauth;

import org.hl7.fhir.instance.model.api.IBaseResource;

import ca.uhn.fhir.parser.DataFormatException;
import ca.uhn.fhir.validation.FhirValidator;
import ca.uhn.fhir.validation.SingleValidationMessage;
import ca.uhn.fhir.validation.ValidationResult;

/**
 * Helper class for validation of FHIR resources.
 */
public class ValidationHelper {
  /**
   * Validate the FHIR resource and print (STDOUT) error messages.
   * @param resource - the FHIR resource to validate.
   * @return ValidationResult - the validation results.
   */
  public static ValidationResult validate(IBaseResource resource) {
    // Test the response is VALID
    FhirValidator validator = App.FHIR_CTX.newValidator();
    validator.setValidateAgainstStandardSchema(true);
    validator.setValidateAgainstStandardSchematron(true);
    ValidationResult result = validator.validateWithResult(resource);

    // If the validation failed, print out the errors before we fail the test.
    if (!result.isSuccessful()) {
      try {
        String body = App.FHIR_CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
        System.out.println(body);        
      } catch (DataFormatException e) {
        System.out.println("ERROR Outputting JSON");
      }
      for (SingleValidationMessage message : result.getMessages()) {
        System.out.println(message.getSeverity() + ": " + message.getMessage());
      }
    }
    return result;
  }
}
