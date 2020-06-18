package org.hl7.davinci.priorauth;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import ca.uhn.fhir.validation.ValidationResult;

public class DatabaseTest {

  @BeforeClass
  public static void setupClass() {
    App.initializeAppDB();
  }

  @Before
  public void setup() throws FileNotFoundException {
    // Create a single test Claim Bundle
    Path modulesFolder = Paths.get("src/test/resources");
    Path fixture = modulesFolder.resolve("bundle-prior-auth.json");
    FileInputStream inputStream = new FileInputStream(fixture.toString());
    Bundle bundle = (Bundle) App.getFhirContext().newJsonParser().parseResource(inputStream);
    Map<String, Object> bundleMap = new HashMap<String, Object>();
    bundleMap.put("id", "minimal");
    bundleMap.put("patient", "pat013");
    bundleMap.put("resource", bundle);
    App.getDB().write(Table.BUNDLE, bundleMap);

    // Add a second Bundle
    bundleMap.replace("id", "minimal-1");
    App.getDB().write(Table.BUNDLE, bundleMap);

    // Add a Claim
    Claim claim = (Claim) bundle.getEntry().get(0).getResource();
    bundleMap.replace("id", "minimal");
    bundleMap.replace("resource", claim);
    App.getDB().write(Table.CLAIM, bundleMap);

    // Add a related Claim
    claim.setId("related-minimal");
    bundleMap.replace("id", "related-minimal");
    bundleMap.replace("resource", claim);
    bundleMap.put("related", "minimal");
    App.getDB().write(Table.CLAIM, bundleMap);
  }

  @After
  public void cleanup() {
    App.getDB().delete(Table.BUNDLE);
    App.getDB().delete(Table.CLAIM);
  }

  @Test
  public void testSearch() {
    Bundle results = App.getDB().search(Table.BUNDLE, Collections.singletonMap("patient", "pat013"));
    Assert.assertNotNull(results);
    Assert.assertEquals(BundleType.SEARCHSET, results.getType());

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(results);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void testRead() {
    Bundle results = (Bundle) App.getDB().read(Table.BUNDLE, Collections.singletonMap("patient", "pat013"));
    Assert.assertNotNull(results);

    // Validate the response.
    ValidationResult result = ValidationHelper.validate(results);
    Assert.assertTrue(result.isSuccessful());
  }

  @Test
  public void testReadAll() {
    List<IBaseResource> results = App.getDB().readAll(Table.BUNDLE, Collections.singletonMap("patient", "pat013"));
    Assert.assertNotNull(results);
    Assert.assertEquals(2, results.size());

    for (IBaseResource r : results) {
      Bundle bundle = (Bundle) r;
      Assert.assertNotNull(bundle);

      // Validate the response.
      ValidationResult result = ValidationHelper.validate(bundle);
      Assert.assertTrue(result.isSuccessful());
    }
  }

  @Test
  public void testReadString() {
    // Read string from multiple hits -- validate first is returned
    String id = App.getDB().readString(Table.BUNDLE, Collections.singletonMap("patient", "pat013"), "id");
    Assert.assertEquals("minimal-1", id);

    // Read string from a unique query
    String patient = App.getDB().readString(Table.BUNDLE, Collections.singletonMap("id", "minimal"), "patient");
    Assert.assertEquals("pat013", patient);

    // Read string from no matches
    id = App.getDB().readString(Table.BUNDLE, Collections.singletonMap("id", "does-not-exist"), "patient");
    Assert.assertNull(id);

    // Read column does not exist
    String colDNE = App.getDB().readString(Table.BUNDLE, Collections.singletonMap("id", "minimal"),
        "col-does-not-exist");
    Assert.assertNull(colDNE);
  }

  @Test
  public void testWrite() throws FileNotFoundException {
    // Insert test data
    Path modulesFolder = Paths.get("src/test/resources");
    Path fixture = modulesFolder.resolve("bundle-prior-auth.json");
    FileInputStream inputStream = new FileInputStream(fixture.toString());
    Bundle bundle = (Bundle) App.getFhirContext().newJsonParser().parseResource(inputStream);
    Map<String, Object> bundleMap = new HashMap<String, Object>();
    bundleMap.put("id", "testWrite");
    bundleMap.put("patient", "pat013");
    bundleMap.put("resource", bundle);
    boolean outcome = App.getDB().write(Table.BUNDLE, bundleMap);
    Assert.assertTrue(outcome);

    // Validate it was written
    IBaseResource data = App.getDB().read(Table.BUNDLE, Collections.singletonMap("id", "testWrite"));
    Assert.assertNotNull(data);
  }

  @Test
  public void testUpdate() {
    boolean outcome = App.getDB().update(Table.BUNDLE, Collections.singletonMap("id", "minimal"),
        Collections.singletonMap("id", "updated-minimal"));
    Assert.assertTrue(outcome);

    // Validate it was updated
    IBaseResource data = App.getDB().read(Table.BUNDLE, Collections.singletonMap("id", "minimal"));
    Assert.assertNull(data);
    data = App.getDB().read(Table.BUNDLE, Collections.singletonMap("id", "updated-minimal"));
    Assert.assertNotNull(data);
  }

  @Test
  public void testDelete() {
    boolean outcome = App.getDB().delete(Table.BUNDLE, "minimal", "pat013");
    Assert.assertTrue(outcome);

    // Validate item deleted
    IBaseResource data = App.getDB().read(Table.BUNDLE, Collections.singletonMap("id", "minimal"));
    Assert.assertNull(data);
  }

  @Test
  public void testDeleteTable() {
    boolean outcome = App.getDB().delete(Table.BUNDLE);
    Assert.assertTrue(outcome);

    // Validate all items deleted
    List<IBaseResource> bundleTable = App.getDB().readAll(Table.BUNDLE, Collections.singletonMap("patient", "pat013"));
    Assert.assertEquals(0, bundleTable.size());
  }

  @Test
  public void testGetMostRecentId() {
    String mostRecentId = App.getDB().getMostRecentId("minimal");
    Assert.assertEquals("related-minimal", mostRecentId);
  }

}
