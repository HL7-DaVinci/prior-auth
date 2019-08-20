package org.hl7.davinci.priorauth;

import java.net.URI;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Date;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Database is responsible for storing and retrieving FHIR resources.
 */
public class Database {

  static final Logger logger = LoggerFactory.getLogger(Database.class);

  /** Bundle Resource */
  public static final String BUNDLE = "Bundle";
  /** Claim Resource */
  public static final String CLAIM = "Claim";
  /** ClaimResponse Resource */
  public static final String CLAIM_RESPONSE = "ClaimResponse";

  // DB_CLOSE_DELAY=-1 maintains the DB in memory after all connections closed
  // (so that we don't lose everything between a connection closing and the next
  // being opened)
  private static final String JDBC_STRING = "jdbc:h2:./database;DB_CLOSE_DELAY=-1";

  static {
    try {
      Class.forName("org.h2.Driver");
    } catch (ClassNotFoundException e) {
      throw new Error(e);
    }
  }

  private Connection getConnection() throws SQLException {
    Connection connection = DriverManager.getConnection(JDBC_STRING);
    connection.setAutoCommit(true);
    return connection;
  }

  /** The base URL of the microservice, for population Bundle.entry.fullUrl. */
  private String baseUrl;

  public Database() {
    String[] tables = { BUNDLE, CLAIM, CLAIM_RESPONSE };
    try (Connection connection = getConnection()) {
      for (String table : tables) {
        connection
            .prepareStatement(
                "CREATE TABLE IF NOT EXISTS " + table + " (id varchar, patient varchar, status varchar, resource clob)")
            .execute();
      }
    } catch (SQLException e) {
      e.printStackTrace();
    }
  }

  /**
   * Search the database for the given resourceType.
   * 
   * @param resourceType - the FHIR resourceType to search.
   * @return Bundle - the search result Bundle.
   */
  public Bundle search(String resourceType, String patient) {
    logger.info("Database::search(" + resourceType + ", " + patient + ")");
    Bundle results = new Bundle();
    results.setType(BundleType.SEARCHSET);
    results.setTimestamp(new Date());
    try (Connection connection = getConnection()) {
      PreparedStatement stmt = connection
          .prepareStatement("SELECT id, patient, resource FROM " + resourceType + " WHERE patient = ?");
      stmt.setString(1, patient);
      logger.info("search query: " + stmt.toString());
      ResultSet rs = stmt.executeQuery();
      int total = 0;
      while (rs.next()) {
        String id = rs.getString("id");
        String patientOut = rs.getString("patient");
        String json = rs.getString("resource");
        logger.info("search: " + id + "/" + patientOut);
        Resource resource = (Resource) App.FHIR_CTX.newJsonParser().parseResource(json);
        resource.setId(id);
        BundleEntryComponent entry = new BundleEntryComponent();
        entry.setFullUrl(baseUrl + resourceType + "/" + id);
        entry.setResource(resource);
        results.addEntry(entry);
        total += 1;
      }
      results.setTotal(total);
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return results;
  }

  /**
   * Search the database for the given resourceType.
   * 
   * @param resourceType - the FHIR resourceType to search.
   * @param status       - the status to search.
   * @return Bundle - the search result Bundle.
   */
  public Bundle search(String resourceType, String patient, String status) {
    logger.info("Database::search(" + resourceType + ", " + patient + ")");
    Bundle results = new Bundle();
    results.setType(BundleType.SEARCHSET);
    results.setTimestamp(new Date());
    try (Connection connection = getConnection()) {
      PreparedStatement stmt = connection
          .prepareStatement("SELECT id, patient, resource FROM " + resourceType + " WHERE patient = ? AND status = ?");
      stmt.setString(1, patient);
      stmt.setString(2, status.toLowerCase());
      logger.info("search query: " + stmt.toString());
      ResultSet rs = stmt.executeQuery();
      int total = 0;
      while (rs.next()) {
        String id = rs.getString("id");
        String patientOut = rs.getString("patient");
        String json = rs.getString("resource");
        logger.info("search: " + id + "/" + patientOut);
        Resource resource = (Resource) App.FHIR_CTX.newJsonParser().parseResource(json);
        resource.setId(id);
        BundleEntryComponent entry = new BundleEntryComponent();
        entry.setFullUrl(baseUrl + resourceType + "/" + id);
        entry.setResource(resource);
        results.addEntry(entry);
        total += 1;
      }
      results.setTotal(total);
    } catch (SQLException e) {
      e.printStackTrace();
    }
    return results;
  }

  /**
   * Read a specific resource from the database.
   * 
   * @param resourceType - the FHIR resourceType to read.
   * @param id           - the ID of the resource.
   * @return IBaseResource - if the resource exists, otherwise null.
   */
  public IBaseResource read(String resourceType, String id, String patient) {
    logger.info("Database::read(" + resourceType + ", " + id + ", " + patient + ")");
    IBaseResource result = null;
    if (resourceType != null && id != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection
            .prepareStatement("SELECT id, patient, resource FROM " + resourceType + " WHERE id = ? AND patient = ?");
        stmt.setString(1, id);
        stmt.setString(2, patient);
        logger.info("read query: " + stmt.toString());
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
          String json = rs.getString("resource");
          String patientOut = rs.getString("patient");
          logger.info("read: " + id + "/" + patientOut);
          Resource resource = (Resource) App.FHIR_CTX.newJsonParser().parseResource(json);
          resource.setId(id);
          result = resource;
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  /**
   * Read a specific resource from the database.
   * 
   * @param resourceType - the FHIR resourceType to read.
   * @param id           - the ID of the resource.
   * @param status       - the status of the resource.
   * @return IBaseResource - if the resource exists, otherwise null.
   */
  public IBaseResource read(String resourceType, String id, String patient, String status) {
    logger.info("Database::read(" + resourceType + ", " + id + ", " + patient + ", " + status + ")");
    IBaseResource result = null;
    if (resourceType != null && id != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection.prepareStatement(
            "SELECT id, patient, resource FROM " + resourceType + " WHERE id = ? AND patient = ? AND status = ?");
        stmt.setString(1, id);
        stmt.setString(2, patient);
        stmt.setString(3, status.toLowerCase());
        logger.info("read query: " + stmt.toString());
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
          String json = rs.getString("resource");
          String patientOut = rs.getString("patient");
          logger.info("read: " + id + "/" + patientOut);
          Resource resource = (Resource) App.FHIR_CTX.newJsonParser().parseResource(json);
          resource.setId(id);
          result = resource;
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  /**
   * Insert a resource into the database.
   * 
   * @param resourceType - the resource type.
   * @param id           - the resource id.
   * @param resource     - the resource itself.
   * @return boolean - whether or not the resource was written.
   */
  public boolean write(String resourceType, String id, String patient, IBaseResource resource) {
    boolean result = false;
    if (resourceType != null && id != null && resource != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection
            .prepareStatement("INSERT INTO " + resourceType + " (id, patient, status, resource) VALUES (?,?,?,?);");
        String queryStmt = stmt.toString();
        String status;
        if (resource instanceof Claim) {
          Claim claim = (Claim) resource;
          status = claim.getStatus().getDisplay();
        } else if (resource instanceof ClaimResponse) {
          ClaimResponse claimResponse = (ClaimResponse) resource;
          status = claimResponse.getStatus().getDisplay();
        } else if (resource instanceof Bundle) {
          status = "valid";
        } else {
          status = "unkown";
        }
        status = status.toLowerCase();
        stmt.setString(1, id);
        stmt.setString(2, patient);
        stmt.setString(3, status);
        stmt.setString(4, json(resource));
        result = stmt.execute();
        logger.info("write: " + queryStmt + "{1: " + id + ", 2: " + patient + ", 3: " + status + ", 4: Resource}");
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  /**
   * Delete a particular resource with a given id.
   * 
   * @param resourceType - the resource type to delete.
   * @param id           - the id of the resource to delete.
   * @return boolean - whether or not the resource was deleted.
   */
  public boolean delete(String resourceType, String id, String patient) {
    boolean result = false;
    if (resourceType != null && id != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection
            .prepareStatement("DELETE FROM " + resourceType + " WHERE id = ? AND patient = ?;");
        stmt.setString(1, id);
        stmt.setString(2, patient);
        result = stmt.execute();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  /**
   * Delete all resources of a particular type.
   * 
   * @param resourceType - the resource type to delete.
   * @return boolean - whether or not the resources were deleted.
   */
  public boolean delete(String resourceType) {
    boolean result = false;
    if (resourceType != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection.prepareStatement("DELETE FROM " + resourceType + ";");
        result = stmt.execute();
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  /**
   * Set the base URI for the microservice. This is necessary so
   * Bundle.entry.fullUrl data is accurately populated.
   * 
   * @param base - from @Context UriInfo uri.getBaseUri()
   */
  public void setBaseUrl(URI base) {
    this.baseUrl = base.toString();
  }

  /**
   * Convert a FHIR resource into JSON.
   * 
   * @param resource - the resource to convert to JSON.
   * @return String - the JSON.
   */
  public String json(IBaseResource resource) {
    String json = App.FHIR_CTX.newJsonParser().setPrettyPrint(true).encodeResourceToString(resource);
    return json;
  }

  /**
   * Convert a FHIR resource into XML.
   * 
   * @param resource - the resource to convert to XML.
   * @return String - the XML.
   */
  public String xml(IBaseResource resource) {
    String xml = App.FHIR_CTX.newXmlParser().setPrettyPrint(true).encodeResourceToString(resource);
    return xml;
  }

  /**
   * Create a FHIR OperationOutcome.
   * 
   * @param severity The severity of the result.
   * @param type     The issue type.
   * @param message  The message to return.
   * @return OperationOutcome - the FHIR resource.
   */
  public OperationOutcome outcome(IssueSeverity severity, IssueType type, String message) {
    OperationOutcome error = new OperationOutcome();
    OperationOutcomeIssueComponent issue = error.addIssue();
    issue.setSeverity(severity);
    issue.setCode(type);
    issue.setDiagnostics(message);
    return error;
  }
}
