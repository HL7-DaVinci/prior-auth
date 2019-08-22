package org.hl7.davinci.priorauth;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.junit.AfterClass;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.validation.ValidationResult;

public class DatabaseTest {

  @BeforeClass
  public static void setup() throws FileNotFoundException {
    // Create a single test Claim
    Path modulesFolder = Paths.get("src/test/resources");
    Path fixture = modulesFolder.resolve("bundle-minimal.json");
    FileInputStream inputStream = new FileInputStream(fixture.toString());
    Bundle bundle = (Bundle) App.FHIR_CTX.newJsonParser().parseResource(inputStream);
    Map<String, Object> bundleMap = new HashMap<String, Object>();
    bundleMap.put("id", "minimal");
    bundleMap.put("patient", "1");
    bundleMap.put("status", Database.getStatusFromResource(bundle));
    bundleMap.put("resource", bundle);
    App.DB.write(Database.BUNDLE, bundleMap);
  }

  @AfterClass
  public static void cleanup() {
    App.DB.delete(Database.BUNDLE, "minimal", "1");
  }

  @Test
  public void testSearch() {
    Bundle results = App.DB.search(Database.BUNDLE, "pat013");
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
