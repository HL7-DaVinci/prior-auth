package org.hl7.davinci.priorauth;

import java.util.*;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.ResultSetMetaData;
import java.util.Date;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.h2.jdbc.JdbcSQLIntegrityConstraintViolationException;
import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.*;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;

/**
 * The Database is responsible for storing and retrieving FHIR resources.
 */
public class Database {

  static final Logger logger = PALogger.getLogger();

  public enum Table {
    BUNDLE("Bundle"), CLAIM("Claim"), CLAIM_ITEM("ClaimItem"), CLAIM_RESPONSE("ClaimResponse"),
    SUBSCRIPTION("Subscription"), RULES("Rules"), AUDIT("Audit"), CLIENT("Client");

    private final String value;

    Table(String value) {
      this.value = value;
    }

    public String value() {
      return this.value;
    }
  }

  private String SQL_FILE;

  private static final String styleFile = "src/main/resources/style.html";
  private static final String scriptFile = "src/main/resources/script.html";

  private static String style = "";
  private static String script = "";

  private static final String SET_CONCAT = ", ";
  private static final String WHERE_CONCAT = " AND ";

  // DB_CLOSE_DELAY=-1 maintains the DB in memory after all connections closed
  // (so that we don't lose everything between a connection closing and the next
  // being opened)
  private static final String JDBC_TYPE = "jdbc:h2:";
  private static final String JDBC_FILE = "databaseData/database";
  private static final String JDBC_OPTIONS = ";DB_CLOSE_DELAY=-1";
  private String JDBC_STRING;

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

  public Database() {
    this("./");

  }

  public Database(String relativePath) {
    JDBC_STRING = JDBC_TYPE + relativePath + JDBC_FILE + JDBC_OPTIONS;
    logger.info("JDBC: " + JDBC_STRING);
    SQL_FILE = relativePath + PropertyProvider.getProperty("database_sql");
    try (Connection connection = getConnection()) {
      String sql = new String(Files.readAllBytes(Paths.get(SQL_FILE).toAbsolutePath()));
      connection.prepareStatement(sql.replace("\"", "")).execute();
      logger.fine(sql);

      style = new String(Files.readAllBytes(Paths.get(relativePath + styleFile).toAbsolutePath()));
      script = new String(Files.readAllBytes(Paths.get(relativePath + scriptFile).toAbsolutePath()));
    } catch (SQLException e) {
      logger.log(Level.SEVERE, "Database::Database:SQLException", e);
    } catch (IOException e) {
      logger.log(Level.SEVERE, "Database::Database:IOException", e);
    }
  }

  public String generateAndRunQuery(Table table) {
    String sql = "SELECT * FROM " + table.value() + " ORDER BY TIMESTAMP DESC";
    return runQuery(sql, true, true);
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
          String columnName = metaData.getColumnName(i);
          if (columnName.contains("ID") || columnName.contains("RELATED")) {
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
      logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
    }

    if (outputHtml) {
      ret = "<html><head>" + style + "</head><body>" + ret + script + "</body></html>";
    }

    return ret;
  }

  /**
   * Search the database for the given resourceType.
   * 
   * @param table  - the Table to search in.
   * @param status - the status to search.
   * @return Bundle - the search result Bundle.
   */
  public Bundle search(Table table, Map<String, Object> constraintMap) {
    logger.info("Database::search(" + table.value() + ", " + constraintMap.toString() + ")");
    AuditEventOutcome auditOutcome = AuditEventOutcome.SUCCESS;
    Bundle results = new Bundle();
    results.setType(BundleType.SEARCHSET);
    results.setTimestamp(new Date());
    try (Connection connection = getConnection()) {
      String sql = "SELECT id, patient, resource FROM " + table.value() + " WHERE "
          + generateClause(constraintMap, WHERE_CONCAT) + ";";
      Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
      maps.add(constraintMap);
      PreparedStatement stmt = generateStatement(sql, maps, connection);
      logger.fine("search query: " + stmt.toString());
      ResultSet rs = stmt.executeQuery();
      int total = 0;
      while (rs.next()) {
        String id = rs.getString("id");
        String patientOut = rs.getString("patient");
        String json = rs.getString("resource");
        logger.info("search: " + id + "/" + patientOut);
        Resource resource = (Resource) App.getFhirContext().newJsonParser().parseResource(json);
        resource.setId(id);
        BundleEntryComponent entry = new BundleEntryComponent();
        entry.setFullUrl(App.getBaseUrl() + "/" + table.value() + "/" + id);
        entry.setResource(resource);
        results.addEntry(entry);
        total += 1;
      }
      results.setTotal(total);
    } catch (SQLException e) {
      auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
      logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
    }
    Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.R, auditOutcome, null, null,
        "Search " + table.value());
    return results;
  }

  /**
   * Read a specific resource from the database.
   * 
   * @param table            - the Table to read from.
   * @param constraintParams - the search constraints for the SQL query.
   * @return IBaseResource - if the resource exists, otherwise null.
   */
  public IBaseResource read(Table table, Map<String, Object> constraintParams) {
    logger.info("Database::read(" + table.value() + ", " + constraintParams.toString() + ")");
    AuditEventOutcome auditOutcome = AuditEventOutcome.SUCCESS;
    IBaseResource result = null;
    if (table != null && constraintParams != null) {
      try (Connection connection = getConnection()) {
        String sql = "SELECT TOP 1 id, patient, resource FROM " + table.value() + " WHERE "
            + generateClause(constraintParams, WHERE_CONCAT) + " ORDER BY timestamp DESC;";
        Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
        maps.add(constraintParams);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        logger.fine("read query: " + stmt.toString());
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
          String id = rs.getString("id");
          String json = rs.getString("resource");
          String patientOut = rs.getString("patient");
          logger.info("read: " + id + "/" + patientOut);
          result = (Resource) App.getFhirContext().newJsonParser().parseResource(json);
        }
      } catch (SQLException e) {
        auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
        logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
      }
    }
    Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.R, auditOutcome, null, null,
        "Read from " + table.value());
    return result;
  }

  /**
   * Read a specific resource from the database.
   * 
   * @param table            - the Table to read from.
   * @param constraintParams - the search constraints for the SQL query.
   * @return List of IBaseResource for all resources matching the constraints.
   *         Empty list if none
   */
  public List<IBaseResource> readAll(Table table, Map<String, Object> constraintParams) {
    logger.info("Database::readAll(" + table.value() + ", " + constraintParams.toString() + ")");
    AuditEventOutcome auditOutcome = AuditEventOutcome.SUCCESS;
    List<IBaseResource> results = new ArrayList<IBaseResource>();
    if (table != null && constraintParams != null) {
      try (Connection connection = getConnection()) {
        String sql = "SELECT id, patient, resource FROM " + table.value() + " WHERE "
            + generateClause(constraintParams, WHERE_CONCAT) + " ORDER BY timestamp DESC;";
        Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
        maps.add(constraintParams);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        logger.fine("read query: " + stmt.toString());
        ResultSet rs = stmt.executeQuery();

        while (rs.next()) {
          String id = rs.getString("id");
          String json = rs.getString("resource");
          String patientOut = rs.getString("patient");
          logger.info("read: " + id + "/" + patientOut);
          results.add((Resource) App.getFhirContext().newJsonParser().parseResource(json));
        }
      } catch (SQLException e) {
        auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
        logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
      }
    }
    Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.R, auditOutcome, null, null,
        "Read all of " + table.value());
    return results;
  }

  /**
   * Read the related field from the database
   * 
   * @param table            - the Table to read from.
   * @param constraintParams - the search constraints for the SQL query.
   * @return the related field of the database
   */
  public String readRelated(Table table, Map<String, Object> constraintParams) {
    return readString(table, constraintParams, "related");
  }

  /**
   * Get the status of an item in the database
   *
   * @param table - the Table to read from.
   * @param id    - the id of the resource.
   * @return - the string value of the status in the database of the first
   *         matching entry with the provided id.
   */
  public String readStatus(Table table, Map<String, Object> constraintParams) {
    return readString(table, constraintParams, "status");
  }

  /**
   * Read the most recent data for specified column from the database
   *
   * @param table            - the Table to read from.
   * @param constraintParams - the search constraints for the SQL query.
   * @param column           - the column to read.
   * @return the specified column of the database
   */
  public String readString(Table table, Map<String, Object> constraintParams, String column) {
    logger.info("Database::read(" + table.value() + ", " + constraintParams.toString() + ", " + column + ")");
    if (table != null && constraintParams != null && column != null) {
      try (Connection connection = getConnection()) {
        // TODO: fix this so it does not insert a string (column) into the SQL
        String sql = "SELECT TOP 1 " + column + " FROM " + table.value() + " WHERE "
            + generateClause(constraintParams, WHERE_CONCAT) + " ORDER BY timestamp DESC;";
        Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
        maps.add(constraintParams);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        logger.fine("read query: " + stmt.toString());
        ResultSet rs = stmt.executeQuery();

        if (rs.next()) {
          Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.R, AuditEventOutcome.SUCCESS, null, null,
              "Read " + column + " from " + table.value());
          return rs.getString(column);
        }
      } catch (SQLException e) {
        logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
      }
    }
    Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.R, AuditEventOutcome.SERIOUS_FAILURE, null, null,
        "Read " + column + " from " + table.value());
    return null;
  }

  /**
   * Insert a resource into database.
   * 
   * @param table - the Table to write to.
   * @param data  - map of columns (keys) and values.
   * @return boolean - whether or not the resource was written.
   */
  public boolean write(Table table, Map<String, Object> data) {
    logger.info("Database::write(" + table.value() + ", " + printMap(data) + ")");
    AuditEventOutcome auditOutcome = AuditEventOutcome.SUCCESS;
    boolean result = false;
    if (data != null) {
      try (Connection connection = getConnection()) {
        String valueClause = "";
        for (int i = 0; i < data.values().size() - 1; i++)
          valueClause += "?,";
        valueClause += "?";

        String sql = "INSERT INTO " + table.value() + " (" + setColumns(data.keySet()) + ") VALUES (" + valueClause
            + ");";
        Collection<Map<String, Object>> maps = new HashSet<Map<String, Object>>();
        maps.add(data);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        result = stmt.execute();
        logger.fine(stmt.toString());
        result = true;
      } catch (JdbcSQLIntegrityConstraintViolationException e) {
        auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
        logger.log(Level.SEVERE,
            "Database::runQuery:JdbcSQLIntegrityConstraintViolationException(attempting to insert foreign key which does not exist)",
            e);
      } catch (SQLException e) {
        auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
        logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
      }
    }
    if (table != Table.AUDIT)
      Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.C, auditOutcome, null, null,
          "Write to " + table.value() + "\n" + data.toString());
    return result;
  }

  /**
   * Update a single column in a row to a new value
   * 
   * @param table            - the Table to update.
   * @param constraintParams - map of column to value for the SQL WHERE clause
   * @param data             - map of column to value for the SQL SET clause
   * @return boolean - whether or not the update was successful
   */
  public boolean update(Table table, Map<String, Object> constraintParams, Map<String, Object> data) {
    logger.info("Database::update(" + table.value() + ", WHERE " + constraintParams.toString() + ", SET"
        + data.toString() + ")");
    AuditEventOutcome auditOutcome = AuditEventOutcome.SUCCESS;
    boolean result = false;
    String sql = "UPDATE " + table.value() + " SET " + generateClause(data, SET_CONCAT)
        + ", timestamp = CURRENT_TIMESTAMP WHERE " + generateClause(constraintParams, WHERE_CONCAT) + ";";
    if (table != null && constraintParams != null && data != null) {
      try (Connection connection = getConnection()) {
        Collection<Map<String, Object>> maps = new ArrayList<Map<String, Object>>();
        maps.add(data);
        maps.add(constraintParams);
        PreparedStatement stmt = generateStatement(sql, maps, connection);
        stmt.execute();
        result = stmt.getUpdateCount() > 0 ? true : false;
        logger.fine(stmt.toString());
      } catch (SQLException e) {
        auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
        logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
      }
    }
    Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.U, auditOutcome, null, null, sql);
    return result;
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
    Claim referencingClaim = (Claim) App.getDB().read(Table.CLAIM, readConstraintMap);
    String referencingId = id;

    while (referencingClaim != null) {
      // Update the referencingId to be the most recent referencingClaim
      referencingId = FhirUtils.getIdFromResource(referencingClaim);

      // Get the new referencing claim
      readConstraintMap.replace("related", referencingId);
      referencingClaim = (Claim) App.getDB().read(Table.CLAIM, readConstraintMap);
    }

    return referencingId;
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
      logger.warning(
          "Database::generateStatement:Value mismatch. Need " + numValuesNeeded + " values but received " + numValues);
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
          valueStr = FhirUtils.json((IBaseResource) value);
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
   * @param table - the Table to delete from.
   * @param id    - the id of the resource to delete.
   * @return boolean - whether or not the resource was deleted.
   */
  public boolean delete(Table table, String id, String patient) {
    logger.info("Database::delete(" + table.value() + ", " + id + ", " + patient + ")");
    AuditEventOutcome auditOutcome = AuditEventOutcome.SUCCESS;
    boolean result = false;
    if (table != null && id != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection
            .prepareStatement("DELETE FROM " + table.value() + " WHERE id = ? AND patient = ?;");
        stmt.setString(1, id);
        stmt.setString(2, patient);

        stmt.execute();
        result = stmt.getUpdateCount() > 0 ? true : false;
        logger.info("Delete Statement: " + stmt.toString());
        logger.info("Update Count: " + stmt.getUpdateCount());
      } catch (SQLException e) {
        auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
        logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
      }
    }
    Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.D, auditOutcome, null, null,
        "Delete " + table.value() + "/" + id + " for patient " + patient);
    return result;
  }

  /**
   * Delete method for the expunge operation to delete by id.
   * 
   * @param table - the table to delete from.
   * @param id    - the id of the resource to delete.
   * @return true if the resource was deleted and false otherwise.
   */
  public boolean delete(Table table, String id) {
    logger.info("Database::delete(" + table.value() + ", " + id + ")");
    AuditEventOutcome auditOutcome = AuditEventOutcome.SUCCESS;
    boolean result = false;
    if (table != null && id != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection.prepareStatement("DELETE FROM " + table.value() + " WHERE id = ?;");
        stmt.setString(1, id);
        stmt.execute();
        result = stmt.getUpdateCount() > 0 ? true : false;
      } catch (SQLException e) {
        auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
        logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
      }
    }
    Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.D, auditOutcome, null, null,
        "Delete " + id + "from " + table.value());
    return result;
  }

  /**
   * Delete all resources of a particular type.
   * 
   * @param table - the Table to delete from.
   * @return boolean - whether or not the resources were deleted.
   */
  public boolean delete(Table table) {
    logger.info("Database::delete(" + table.value() + ")");
    AuditEventOutcome auditOutcome = AuditEventOutcome.SUCCESS;
    boolean result = false;
    if (table != null) {
      try (Connection connection = getConnection()) {
        PreparedStatement stmt = connection.prepareStatement("DELETE FROM " + table.value() + ";");
        stmt.execute();
        result = stmt.getUpdateCount() > 0 ? true : false;
      } catch (SQLException e) {
        auditOutcome = AuditEventOutcome.SERIOUS_FAILURE;
        logger.log(Level.SEVERE, "Database::runQuery:SQLException", e);
      }
    }
    Audit.createAuditEvent(AuditEventType.ACTIVITY, AuditEventAction.D, auditOutcome, null, null,
        "Delete " + table.value());
    return result;
  }

  /**
   * Reduce a Map to a single string in the form "{key} = '{value}'" +
   * separator
   * 
   * @param map       - key value pair of columns and values.
   * @param separator - the string to connect a set of key value with another
   *                  set.
   * @return string in the form "{key} = '{value}'" + separator...
   */
  private String generateClause(Map<String, Object> map, String separator) {
    String column;
    String sqlStr = "";
    for (Iterator<String> iterator = map.keySet().iterator(); iterator.hasNext();) {
      column = iterator.next();
      sqlStr += column + " = ?";

      if (iterator.hasNext())
        sqlStr += separator;
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

  private String printMap(Map<String, Object> data) {
    StringBuilder string = new StringBuilder("{ ");

    for (Map.Entry<String, Object> entry : data.entrySet()) {
      if (entry.getKey().equals("resource")) {
        string.append("resource, ");
      } else if (entry.getKey().equals("organization")) {
        string.append("organization, ");
      } else {
        string.append(entry.getValue() != null ? entry.getValue().toString() : "null, ");
      }
    }

    string.append(" }");
    return string.toString();
  }

}
