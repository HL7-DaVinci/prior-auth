package org.hl7.davinci.priorauth;

import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
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
  @Path("/Bundle")
  public Response getBundles() {
    return query(Database.BUNDLE);
  }

  @GET
  @Path("/Claim")
  public Response getClaims() {
    return query(Database.CLAIM);
  }

  @GET
  @Path("/ClaimResponse")
  public Response getClaimResponses() {
    return query(Database.CLAIM_RESPONSE);
  }

  @GET
  @Path("/ClaimItem")
  public Response getClaimItems() {
    return query(Database.CLAIM_ITEM);
  }

  private Response query(String resource) {
    logger.info("GET /query/" + resource);
    if (App.debugMode) {
      return Response.ok(App.getDB().generateAndRunQuery(resource)).build();
    } else {
      logger.warning("DebugEndpoint::query disabled");
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
  }
}
