package org.hl7.davinci.priorauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;
import java.lang.invoke.MethodHandles;

@RequestScoped
@Path("$expunge")
public class ExpungeOperation {

  static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @GET
  public Response getExpunge() {
    return expungeDatabase();
  }

  @POST
  public Response postExpunge() {
    return expungeDatabase();
  }

  /**
   * Delete all the data in the database. Useful if the
   * demonstration database becomes too large and unwieldy.
   * @return - HTTP 200
   */
  private Response expungeDatabase() {
    logger.info("GET/POST /$expunge");
    if (App.debugMode) {
      // Cascading delete of everything...
      App.DB.delete(Database.BUNDLE);
      App.DB.delete(Database.CLAIM);
      App.DB.delete(Database.CLAIM_RESPONSE);
      return Response.ok().build();
    } else {
      logger.warn("query disabled");
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
  }
}
