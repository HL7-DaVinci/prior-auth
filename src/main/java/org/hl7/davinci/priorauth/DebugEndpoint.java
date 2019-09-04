package org.hl7.davinci.priorauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import java.lang.invoke.MethodHandles;

@RequestScoped
@Path("query")
public class DebugEndpoint {

  static final Logger logger = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

  @Context
  private UriInfo uri;

  @GET
  public Response runQuery(@QueryParam("resource") String resource) {
    logger.info("GET /query?resource=" + resource);
    if (App.debugMode && resource != null) {
      return Response.ok(App.DB.generateAndRunQuery(resource)).build();
    } else {
      logger.warn("query disabled");
      return Response.status(Response.Status.BAD_REQUEST).build();
    }
  }
}
