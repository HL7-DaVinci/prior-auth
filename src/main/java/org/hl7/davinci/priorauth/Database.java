package org.hl7.davinci.priorauth;

import java.lang.invoke.MethodHandles;
import java.util.*;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.ResultSetMetaData;
import java.util.Date;

import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Database is responsible for storing and retrieving FHIR resources.
 */
public class Database {

  static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  /** Bundle Resource */
  public static final String BUNDLE = "Bundle";
  /** Claim Resource */
  public static final String CLAIM = "Claim";
  /** Claim Item */
  public static final String CLAIM_ITEM = "ClaimItem";
  /** ClaimResponse Resource */
  public static final String CLAIM_RESPONSE = "ClaimResponse";

  private static final String createSqlFile = "src/main/java/org/hl7/davinci/priorauth/CreateDatabase.sql";

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
      String sql = new String(Files.readAllBytes(Paths.get(createSqlFile).toAbsolutePath()));
      connection.prepareStatement(sql.replace("\"", "")).execute();
      logger.info(sql);
    } catch (SQLException e) {
      e.printStackTrace();
    } catch (IOException e) {
      logger.info("IOException");
      e.printStackTrace();
    }
  }

  public String runQuery(String sqlQuery, boolean printClobs, boolean outputHtml) {
    String ret = "";
    try (Connection connection = getConnection()) {
      // build and execute the query
      PreparedStatement stmt = connection.prepareStatement(sqlQuery);
      ResultSet rs = stmt.executeQuery();

      // get the number of columns
      ResultSetMetaData metaData = rs.getMetaData();
      int columnCount = metaData.getColumnCount();

      if (outputHtml) { ret += "<table id='results'>\n<tr>"; }
      // print the column names
      for (int i = 1; i <= columnCount; i++) {
        if (i != 1 && !outputHtml) {
          ret += " / ";
        }
        if (outputHtml) {
          if (metaData.getColumnName(i).contains("ID")) {
            ret += "<th><div style='width: 300px;'>" + metaData.getColumnName(i) + "</div></th>";
          } else {
            ret += "<th>" + metaData.getColumnName(i) + "</th>";
          }
        }
        else { ret += metaData.getColumnName(i); }
      }
      if (outputHtml) { ret += "</tr>"; }
      ret += "\n";

      // print all of the data
      while(rs.next()) {
        if (outputHtml) { ret += "<tr>"; }
        for(int i = 1; i <= columnCount; i++) {
          if (outputHtml) { ret += "<td>"; }
          if (i != 1 && !outputHtml) {
            ret += " / ";
          }
          Object object = rs.getObject(i);
          if (object instanceof org.h2.jdbc.JdbcClob && printClobs) {
            ret += object == null ? "NULL" : rs.getString(i);
          } else {
            ret += object == null ? "NULL" : object.toString();
          }
          if (outputHtml) { ret += "</td>\n"; }
        }
        if (outputHtml) { ret += "</tr>"; }
        ret += "\n";
      }

      if (outputHtml) { ret += "</table>\n"; }

    } catch (SQLException e) {
      e.printStackTrace();
    }

    String style = "<style>\n" +
        "#results {\n" +
        "  font-family: \"Trebuchet MS\", Arial, Helvetica, sans-serif;\n" +
        "  border-collapse: collapse;\n" +
        "  width: 100%;\n" +
        "}\n" +
        "\n" +
        "#results td, #results th {\n" +
        "  border: 1px solid #ddd;\n" +
        "  padding: 8px;\n" +
        "}\n" +
        "\n" +
        "#results tr:nth-child(even){background-color: #f2f2f2;}\n" +
        "\n" +
        "#results tr:hover {background-color: #ddd;}\n" +
        "\n" +
        "#results th {\n" +
        "  padding-top: 12px;\n" +
        "  padding-bottom: 12px;\n" +
        "  text-align: left;\n" +
        "  background-color: #4CAF50;\n" +
        "  color: white;\n" +
        "}\n" +
        "</style>";
    if (outputHtml) { ret = "<html><head>" + style + "</head><body>" + ret + "</body></html>"; }
    return ret;
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
        entry.setFullUrl(baseUrl + resourceType + "?identifier=" + id + "&patient.identifier=" + patientOut);
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
   * @param status       - the status of the resource.
   * @return IBaseResource - if the resource exists, otherwise null.
   */
  public IBaseResource read(String resourceType, String id, String patient, String status) {
    if (resourceType == CLAIM_RESPONSE) {
      return read(resourceType, id, patient, status, true);
    } else {
      return read(resourceType, id, patient, status, false);
    }
  }
  /**
   * Read a specific resource from the database.
   * 
   * @param resourceType - the FHIR resourceType to read.
   * @param id           - the ID of the resource.
   * @param status       - the status of the resource.
   * @param useClaimId   - flag indicating if query should be based on claimId or id.
   * @return IBaseResource - if the resource exists, otherwise null.
   */
  public IBaseResource read(String resourceType, String id, String patient, String status, boolean useClaimId) {
    logger.info("Database::read(" + resourceType + ", " + id + ", " + patient + ", " + status + ", " + String.valueOf(useClaimId) + ")");
    IBaseResource result = null;
    if (resourceType != null && id != null) {
      try (Connection connection = getConnection()) {
        String statusQuery = status == null ? "" : " AND status = ?";
        String idString = useClaimId ? "claimId" : "id";
        String sqlQuery = "SELECT TOP 1 id, patient, resource FROM " + resourceType + " WHERE " + idString + " = ? AND patient = ?" + statusQuery + " ORDER BY timestamp DESC";
        PreparedStatement stmt = connection.prepareStatement(sqlQuery);
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
        } else {
          return read(resourceType, id, patient, status, false);
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  public IBaseResource readNewestClaimResponse(String resourceType, String id, String patient, String status) {
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
   * @param data         - map of columns (keys) and values.
   * @return boolean - whether or not the resource was written.
   */
  public boolean write(String resourceType, Map<String, Object> data) {
    logger.info("Database::write(" + resourceType + ", " + data.toString() + ")");
    boolean result = false;
    if (data != null) {
      try (Connection connection = getConnection()) {
        String sql = "INSERT INTO " + resourceType + " (" + setColumns(data.keySet()) + ") VALUES "
            + setValues(data.values()) + ";";
        PreparedStatement stmt = connection.prepareStatement(sql);
        result = stmt.execute();
        // logger.info(sql);
        result = true;
      } catch (JdbcSQLIntegrityConstraintViolationException e) {
        logger.info("ERROR: Attempting to insert foreign key which does not exist");
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
  public boolean update(String resourceType, Map<String, Object> constraintParams, Map<String, Object> data) {
    logger.info("Database::update(" + resourceType + ", WHERE " + constraintParams.toString() + ", SET"
        + data.toString() + ")");
    boolean result = false;
    if (resourceType != null && constraintParams != null && data != null) {
      try (Connection connection = getConnection()) {
        String sql = "UPDATE " + resourceType + " SET " + reduceMap(data, ", ")
            + ", timestamp = CURRENT_TIMESTAMP WHERE " + reduceMap(constraintParams, " AND ") + ";";
        PreparedStatement stmt = connection.prepareStatement(sql);
        result = stmt.execute();
        logger.info(sql);
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
   * Reduce a Map to a single string in the form "{key} = '{value}'" +
   * concatonator
   * 
   * @param map          - key value pair of columns and values.
   * @param concatonator - the string to connect a set of key value with another
   *                     set.
   * @return string in the form "{key} = '{value}'" + concatonator...
   */
  private String reduceMap(Map<String, Object> map, String concatonator) {
    String sqlStr = "";
    String column;
    Object value;
    for (Iterator<String> iterator = map.keySet().iterator(); iterator.hasNext();) {
      column = iterator.next();
      sqlStr += column + " = '";
      value = map.get(column);
      if (value instanceof String)
        sqlStr += (String) value;
      else if (value instanceof IBaseResource)
        sqlStr += json((IBaseResource) value);
      else
        sqlStr += value.toString();

      sqlStr += "'";
      if (iterator.hasNext())
        sqlStr += concatonator;
    }

    return sqlStr;
  }

  /**
   * Internal function to map the keys to a string
   * 
   * @param keys - the set of keys to be reduced.
   * @return a string of each key concatenated by ", "
   */
  private String setColumns(Set<String> keys) {
    Optional<String> reducedArr = Arrays.stream(keys.toArray(new String[0])).reduce((str1, str2) -> str1 + ", " + str2);
    return reducedArr.get();
  }

  /**
   * Internal function to map the values to a string
   * 
   * @param values - the collection of values to be reduced.
   * @return a single string of each value represented as a string concatenated by
   *         ", " and wrapped in ' '
   */
  private String setValues(Collection<Object> values) {
    String sqlStr = "(";
    for (Iterator<Object> iterator = values.iterator(); iterator.hasNext();) {
      Object value = iterator.next();
      sqlStr += "'";
      if (value instanceof String)
        sqlStr += (String) value;
      else if (value instanceof IBaseResource)
        sqlStr += json((IBaseResource) value);
      else
        sqlStr += value.toString();

      if (iterator.hasNext())
        sqlStr += "', ";
      else
        sqlStr += "')";
    }
    return sqlStr;
  }
}
