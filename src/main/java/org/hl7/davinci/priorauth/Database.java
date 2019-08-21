package org.hl7.davinci.priorauth;

import java.util.*;

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
  /** Bundle Table Keys */
  public static final String[] BUNDLE_KEYS = { "id", "patient", "status", "resource" };
  /** Claim Table Keys */
  public static final String[] CLAIM_KEYS = { "id", "patient", "status", "resource" };
  /** ClaimResponse Table Keys */
  public static final String[] CLAIM_RESPONSE_KEYS = { "id", "claimId", "patient", "status", "resource" };

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
    try (Connection connection = getConnection()) {
      // TODO: make these use the {RESOURCE}_KEYS array
      String bundleTableStmt = "CREATE TABLE IF NOT EXISTS " + BUNDLE
          + "(id varchar, patient varchar, status varchar, resource clob);";
      String claimTableStmt = "CREATE TABLE IF NOT EXISTS " + CLAIM
          + "(id varchar, patient varchar, status varchar, resource clob);";
      String claimResponseTableStmt = "CREATE TABLE IF NOT EXISTS " + CLAIM_RESPONSE
          + "(id varchar, claimId varchar, patient varchar, status varchar, resource clob);";

      connection.prepareStatement(bundleTableStmt).execute();
      connection.prepareStatement(claimTableStmt).execute();
      connection.prepareStatement(claimResponseTableStmt).execute();
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
    return search(resourceType, patient, null);
  }

  /**
   * Search the database for the given resourceType.
   * 
   * @param resourceType - the FHIR resourceType to search.
   * @param status       - the status to search.
   * @return Bundle - the search result Bundle.
   */
  public Bundle search(String resourceType, String patient, String status) {
    logger.info("Database::search(" + resourceType + ", " + patient + "," + status + ")");
    Bundle results = new Bundle();
    results.setType(BundleType.SEARCHSET);
    results.setTimestamp(new Date());
    try (Connection connection = getConnection()) {
      String statusQuery = status == null ? "" : " AND status = ?";
      PreparedStatement stmt = connection
          .prepareStatement("SELECT id, patient, resource FROM " + resourceType + " WHERE patient = ?" + statusQuery);
      stmt.setString(1, patient);
      if (status != null)
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
    return read(resourceType, id, patient, null);
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
        String statusQuery = status == null ? "" : " AND status = ?";
        PreparedStatement stmt = connection.prepareStatement(
            "SELECT id, patient, resource FROM " + resourceType + " WHERE id = ? AND patient = ?" + statusQuery);
        stmt.setString(1, id);
        stmt.setString(2, patient);
        if (status != null)
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
   * Insert a resource into database.
   * 
   * @param resourceType - the tpye of the resource.
   * @param keys         - string array of key (table column) names.
   * @param values       - string array of values to add into the columns.
   * @param resource     - the resource itself.
   * @return boolean - whether or not the resource was written.
   */
  public boolean write(String resourceType, String[] keys, String[] values, IBaseResource resource) {
    final String commaSeperator = ", ";
    final String quoteSeperator = "', '";
    logger.info("Database::write(" + resourceType + ", (" + reduceArray(keys, commaSeperator) + "), ("
        + reduceArray(values, commaSeperator) + "))");
    boolean result = false;
    if (keys != null && values != null && resource != null && keys.length == (values.length + 1)) {
      try (Connection connection = getConnection()) {
        String sql = "INSERT INTO " + resourceType + " (" + reduceArray(keys, commaSeperator) + ") VALUES ('"
            + reduceArray(values, quoteSeperator) + "',?);";
        PreparedStatement stmt = connection.prepareStatement(sql);
        stmt.setString(1, json(resource));
        result = stmt.execute();
        logger.info(sql);
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  /**
   * Update a single column in a row to a new value
   * 
   * @param resourceType - the resource type.
   * @param id           - the resource id.
   * @param column       - the name of the column to update.
   * @param value        - the new value.
   * @return boolean - whether or not the update was successful
   */
  public boolean update(String resourceType, String id, String column, String value) {
    logger.info("Database::update(" + resourceType + ", " + id + ", " + column + ", " + value + ")");
    boolean result = false;
    if (resourceType != null && id != null && column != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection
            .prepareStatement("UPDATE " + resourceType + " SET " + column + " = ? WHERE id = ?;");
        stmt.setString(1, value);
        stmt.setString(2, id);
        logger.info(stmt.toString());
        result = stmt.execute();
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
   * Internal function to get the correct status from a resource depending on the
   * type
   * 
   * @param resource - the resource.
   * @return - the status of the resource.
   */
  public static String getStatusFromResource(IBaseResource resource) {
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
    return status;
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

  /**
   * Internal method for reducing an array into a string
   * 
   * @param arr       - the string array to reduce.
   * @param separator - the string to connect elements together.
   * @return a single string representation of the array
   */
  private String reduceArray(String[] arr, String separator) {
    Optional<String> reducedArr = Arrays.stream(arr).reduce((str1, str2) -> str1 + separator + str2);
    return reducedArr.get();
  }
}
