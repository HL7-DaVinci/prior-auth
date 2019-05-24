package org.hl7.davinci.priorauth;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.core.Response;

@RequestScoped
@Path("$expunge")
public class ExpungeOperation {

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
    // Cascading delete of everything...
    App.DB.delete(Database.BUNDLE);
    App.DB.delete(Database.CLAIM);
    App.DB.delete(Database.CLAIM_RESPONSE);
    return Response.ok().build();
  }
}
