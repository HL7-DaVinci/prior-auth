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
import org.hl7.fhir.r4.model.Claim;

/**
 * The Claim endpoint to READ and SEARCH for submitted claims.
 */
@RequestScoped
@Path("Claim")
public class ClaimEndpoint {

  @Context
  private UriInfo uri;
  
  @GET
  public Response searchClaims() {
    App.DB.setBaseUrl(uri.getBaseUri());
    Bundle claims = App.DB.search(Database.CLAIM);
    String json = App.DB.json(claims);
    return Response.ok(json, MediaType.APPLICATION_JSON).build();
  }
  
  @GET
  @Path("/{id}")
  public Response readClaim(@PathParam("id") String id) {
    String json = null;
    if (id == null) {
      // Search
      Bundle claims = App.DB.search(Database.CLAIM);
      json = App.DB.json(claims);
    } else {
      // Read
      Claim claim = (Claim) App.DB.read(Database.CLAIM, id);
      if (claim == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      json = App.DB.json(claim);
    }
    return Response.ok(json, MediaType.APPLICATION_JSON).build();
  }
}
