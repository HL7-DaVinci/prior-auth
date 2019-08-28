package org.hl7.davinci.priorauth.tools;

import org.hl7.davinci.priorauth.Database;

public class DatabaseTool {

  public static final Database DB = new Database();

  public static void log(String log) {
    System.out.println(log);
  }

  public static void main(String[] args) {
    boolean printClobs = false;
    String sqlQuery = "";
    if (args.length > 0) {
      sqlQuery = args[0];
    }
    if (args.length > 1) {
      printClobs = true;
    }

    log("query: " + sqlQuery);
    log("options: " + (printClobs ? "" : "do NOT ") + "print clobs");
    log(DB.runQuery(sqlQuery, printClobs, false));
  }
}
