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

  private static final String styleFile = "src/main/resources/style.html";
  private static final String scriptFile = "src/main/resources/script.html";

  private static String style = "";
  private static String script = "";

  private static final String SET_CONCAT = ", ";
  private static final String WHERE_CONCAT = " AND ";

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

      style = new String(Files.readAllBytes(Paths.get(styleFile).toAbsolutePath()));
      script = new String(Files.readAllBytes(Paths.get(scriptFile).toAbsolutePath()));
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

      if (outputHtml) {
        ret += "<table id='results'>\n<tr>";
      }
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
        } else {
          ret += metaData.getColumnName(i);
        }
      }
      if (outputHtml) {
        ret += "</tr>";
      }
      ret += "\n";

      // print all of the data
      while (rs.next()) {
        if (outputHtml) {
          ret += "<tr>";
        }
        for (int i = 1; i <= columnCount; i++) {
          if (outputHtml) {
            ret += "<td>";
          }
          if (i != 1 && !outputHtml) {
            ret += " / ";
          }
          Object object = rs.getObject(i);
          if (object instanceof org.h2.jdbc.JdbcClob && printClobs) {
            ret += "<button class=\"collapsible\">+</button>\n" + "<div class=\"content\"><xmp>";
            ret += object == null ? "NULL" : rs.getString(i);
            ret += "</xmp>\n</div>\n";
          } else {
            ret += object == null ? "NULL" : object.toString();
          }
          if (outputHtml) {
            ret += "</td>\n";
          }
        }
        if (outputHtml) {
          ret += "</tr>";
        }
        ret += "\n";
      }

      if (outputHtml) {
        ret += "</table>\n";
      }

    } catch (SQLException e) {
      e.printStackTrace();
    }

    if (outputHtml) {
      ret = "<html><head>" + style + "</head><body>" + ret + script + "</body></html>";
    }

    return ret;
  }

  /**
   * Search the database for the given resourceType.
   * 
   * @param resourceType - the FHIR resourceType to search.
   * @param status       - the status to search.
   * @return Bundle - the search result Bundle.
   */
  public Bundle search(String resourceType, Map<String, Object> constraintMap) {
    logger.info("Database::search(" + resourceType + ", " + constraintMap.toString() + ")");
    Bundle results = new Bundle();
    results.setType(BundleType.SEARCHSET);
    results.setTimestamp(new Date());
    try (Connection connection = getConnection()) {
      String sql = "SELECT id, patient, resource FROM " + resourceType + " WHERE "
          + generateClause(constraintMap, WHERE_CONCAT) + ";";
      Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
      maps.add(constraintMap);
      PreparedStatement stmt = generateStatement(sql, maps, connection);
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
   * @param resourceType     - the FHIR resourceType to read.
   * @param constraintParams - the search constraints for the SQL query.
   * @return IBaseResource - if the resource exists, otherwise null.
   */
  public IBaseResource read(String resourceType, Map<String, Object> constraintParams) {
    logger.info("Database::read(" + resourceType + ", " + constraintParams.toString() + ")");
    IBaseResource result = null;
    if (resourceType != null && constraintParams != null) {
      try (Connection connection = getConnection()) {
        String sql = "SELECT TOP 1 id, patient, resource FROM " + resourceType + " WHERE "
            + generateClause(constraintParams, WHERE_CONCAT) + " ORDER BY timestamp DESC;";
        Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
        maps.add(constraintParams);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        logger.info("read query: " + stmt.toString());
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
          String id = rs.getString("id");
          String json = rs.getString("resource");
          String patientOut = rs.getString("patient");
          logger.info("read: " + id + "/" + patientOut);
          result = (Resource) App.FHIR_CTX.newJsonParser().parseResource(json);
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  /**
   * Read the related field from the database
   * 
   * @param resourceType     - the FHIR resourceType to read.
   * @param constraintParams - the search constraints for the SQL query.
   * @return the related field of the database
   */
  public String readRelated(String resourceType, Map<String, Object> constraintParams) {
    logger.info("Database::read(" + resourceType + ", " + constraintParams.toString() + ")");
    if (resourceType != null && constraintParams != null) {
      try (Connection connection = getConnection()) {
        String sql = "SELECT TOP 1 related FROM " + resourceType + " WHERE "
            + generateClause(constraintParams, WHERE_CONCAT) + " ORDER BY timestamp DESC;";
        Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
        maps.add(constraintParams);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        logger.info("read query: " + stmt.toString());
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
          return rs.getString("related");
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  /**
   * Get the status of an item in the database
   * 
   * @param resourceType - the FHIR resource type to read.
   * @param id           - the id of the resource.
   * @return - the string value of the status in the database of the first
   *         matching entry with the provided id.
   */
  public String readStatus(String resourceType, Map<String, Object> constraintParams) {
    logger.info("Database::readStatus(" + resourceType + ", " + constraintParams.toString() + ")");
    if (resourceType != null && constraintParams != null) {
      try (Connection connection = getConnection()) {
        String sql = "SELECT status FROM " + resourceType + " WHERE " + generateClause(constraintParams, WHERE_CONCAT)
            + ";";
        Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
        maps.add(constraintParams);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        ResultSet rs = stmt.executeQuery();
        logger.info("read status query: " + stmt.toString());
        if (rs.next()) {
          return rs.getString("status");
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  /**
   * Take in a Claim id and get the most recent id if it has been replaced by a
   * more updated request.
   * 
   * @param id - Claim id
   * @return the most recent Claim id for the Claim by following all updates
   */
  public String getMostRecentId(String id) {
    Map<String, Object> readConstraintMap = new HashMap<String, Object>();
    readConstraintMap.put("related", id);
    Claim referencingClaim = (Claim) App.DB.read(Database.CLAIM, readConstraintMap);
    String referecingId = id;

    while (referencingClaim != null) {
      // Update referincing claim to cancelled
      referecingId = referencingClaim.getIdElement().getIdPart();

      // Get the new referencing claim
      readConstraintMap.replace("related", referecingId);
      referencingClaim = (Claim) App.DB.read(Database.CLAIM, readConstraintMap);
    }

    return referecingId;
  }

  /**
   * Get the status of an item in the database
   * 
   * @param resourceType - the FHIR resource type to read.
   * @param id           - the id of the resource.
   * @return - the string value of the status in the database of the first
   *         matching entry with the provided id.
   */
  public String readStatus(String resourceType, String id) {
    logger.info("Database::readStatus(" + resourceType + ", " + id);
    if (resourceType != null && id != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection.prepareStatement("SELECT status FROM " + resourceType + " WHERE id = ?");
        stmt.setString(1, id);
        logger.info("read status query: " + stmt.toString());
        ResultSet rs = stmt.executeQuery();
        if (rs.next()) {
          return rs.getString("status");
        }
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return null;
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
        String valueClause = "";
        for (int i = 0; i < data.values().size() - 1; i++)
          valueClause += "?,";
        valueClause += "?";

        String sql = "INSERT INTO " + resourceType + " (" + setColumns(data.keySet()) + ") VALUES (" + valueClause
            + ");";
        Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
        maps.add(data);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        result = stmt.execute();
        logger.info(stmt.toString());
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
        String sql = "UPDATE " + resourceType + " SET " + generateClause(data, SET_CONCAT)
            + ", timestamp = CURRENT_TIMESTAMP WHERE " + generateClause(constraintParams, WHERE_CONCAT) + ";";
        Collection<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
        maps.add(data);
        maps.add(constraintParams);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        result = stmt.execute();
        logger.info(stmt.toString());
      } catch (SQLException e) {
        e.printStackTrace();
      }
    }
    return result;
  }

  /**
   * Create a SQL PreparedStatement from an SQL string and setting the strings
   * based on the maps provided.
   * 
   * @param sql        - query string with '?' denoting values to be set by the
   *                   maps.
   * @param maps       - Collection of Maps used to set the values.
   * @param connection - the connection to the database.
   * @return PreparedStatement with all values set or null if the number of values
   *         provided is incorrect.
   * @throws SQLException
   */
  private PreparedStatement generateStatement(String sql, Collection<Map<String, Object>> maps, Connection connection)
      throws SQLException {
    int numValuesNeeded = (int) sql.chars().filter(ch -> ch == '?').count();
    int numValues = maps.stream().reduce(0, (subtotal, element) -> subtotal + element.size(), Integer::sum);
    if (numValues != numValuesNeeded) {
      logger.info("Value mismatch. Need " + numValuesNeeded + " values but received " + numValues);
      return null;
    }

    PreparedStatement stmt = connection.prepareStatement(sql);
    int valueIndex = 1;
    for (Map<String, Object> map : maps) {
      for (Object value : map.values()) {
        String valueStr;
        if (value instanceof String)
          valueStr = (String) value;
        else if (value instanceof IBaseResource)
          valueStr = json((IBaseResource) value);
        else if (value == null)
          valueStr = "null";
        else
          valueStr = value.toString();
        stmt.setString(valueIndex, valueStr);
        valueIndex++;
      }
    }

    return stmt;
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
  private String generateClause(Map<String, Object> map, String concatonator) {
    String column;
    String sqlStr = "";
    for (Iterator<String> iterator = map.keySet().iterator(); iterator.hasNext();) {
      column = iterator.next();
      sqlStr += column + " = ?";

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

}
