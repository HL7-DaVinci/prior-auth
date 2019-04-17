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
import org.hl7.fhir.r4.model.ClaimResponse;

/**
 * The ClaimResponse endpoint to READ and SEARCH for ClaimResponses to submitted claims.
 */
@RequestScoped
@Path("ClaimResponse")
public class ClaimResponseEndpoint {

  @Context
  private UriInfo uri;
  
  @GET
  public Response searchClaimResponses() {
    App.DB.setBaseUrl(uri.getBaseUri());
    Bundle claimResponses = App.DB.search(Database.CLAIM_RESPONSE);
    String json = App.DB.json(claimResponses);
    return Response.ok(json, MediaType.APPLICATION_JSON).build();
  }
  
  @GET
  @Path("/{id}")
  public Response readClaimResponse(@PathParam("id") String id) {
    String json = null;
    if (id == null) {
      // Search
      Bundle claimResponses = App.DB.search(Database.CLAIM_RESPONSE);
      json = App.DB.json(claimResponses);
    } else {
      // Read
      ClaimResponse claimResponse = (ClaimResponse) App.DB.read(Database.CLAIM_RESPONSE, id);
      if (claimResponse == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      json = App.DB.json(claimResponse);
    }
    return Response.ok(json, MediaType.APPLICATION_JSON).build();
  }
}
