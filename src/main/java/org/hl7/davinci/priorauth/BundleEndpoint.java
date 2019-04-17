package org.hl7.davinci.priorauth;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.hl7.fhir.r4.model.Bundle;

/**
 * The Bundle endpoint to READ and SEARCH for submitted Bundles.
 */
@RequestScoped
@Path("Bundle")
public class BundleEndpoint {

  @Context
  private UriInfo uri;
  
  @GET
  public Response searchBundles() {
    App.DB.setBaseUrl(uri.getBaseUri());
    Bundle bundles = App.DB.search(Database.BUNDLE);
    String json = App.DB.json(bundles);
    return Response.ok(json, MediaType.APPLICATION_JSON).build();
  }
  
  @GET
  @Path("/{id}")
  public Response readBundle(@PathParam("id") String id) {
    String json = null;
    if (id == null) {
      // Search
      Bundle bundles = App.DB.search(Database.BUNDLE);
      json = App.DB.json(bundles);
    } else {
      // Read
      Bundle bundle = (Bundle) App.DB.read(Database.BUNDLE, id);
      if (bundle == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      json = App.DB.json(bundle);
    }
    return Response.ok(json, MediaType.APPLICATION_JSON).build();
  }
}
