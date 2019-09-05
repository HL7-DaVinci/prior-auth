package org.hl7.davinci.priorauth;

import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@RequestScoped
@Path("query")
public class DebugEndpoint {

  static final Logger logger = PALogger.getLogger();

  @Context
  private UriInfo uri;

  @GET
  public Response runQuery(@QueryParam("query") String query, @QueryParam("all") String printAll) {
    logger.info("GET /query?query=" + query);
    if (App.debugMode && query != null) {
      boolean printClobs = false;
      if (printAll != null) {
        if (printAll.equalsIgnoreCase("true")) {
          printClobs = true;
        }
      }
      String lcQuery = query.toLowerCase();
      if (lcQuery.contains("delete") || lcQuery.contains("insert") || lcQuery.contains("update")
          || lcQuery.contains("create") || lcQuery.contains("drop") || lcQuery.contains("alter")) {
        logger.warn("unable to perform potentially destructive operations");
        return Response.status(Response.Status.FORBIDDEN).build();
      } else {
        return Response.ok(App.DB.runQuery(query, printClobs, true)).build();
      }
    } else {
      logger.warn("query disabled");
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
  }
}
