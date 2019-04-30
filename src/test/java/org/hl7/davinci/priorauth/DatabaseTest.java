package org.hl7.davinci.priorauth;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.junit.Assert;
import org.junit.Test;

import ca.uhn.fhir.validation.ValidationResult;

public class DatabaseTest {

  @Test
  public void testSearch() {
    Bundle results = App.DB.search(Database.BUNDLE);
    Assert.assertNotNull(results);
    Assert.assertEquals(BundleType.SEARCHSET, results.getType());

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(results);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void testJson() {
    Bundle bundle = new Bundle();
    bundle.setType(BundleType.SEARCHSET);
    bundle.setTotal(0);
    
    String json = App.DB.json(bundle);
    Assert.assertNotNull(json);
  }

  @Test
  public void testXml() {
    Bundle bundle = new Bundle();
    bundle.setType(BundleType.SEARCHSET);
    bundle.setTotal(0);

    String xml = App.DB.xml(bundle);
    Assert.assertNotNull(xml);
  }
}
