package org.hl7.davinci.priorauth;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.Produces;
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
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+json"})
  public Response searchClaimResponses() {
    App.DB.setBaseUrl(uri.getBaseUri());
    Bundle claimResponses = App.DB.search(Database.CLAIM_RESPONSE);
    String json = App.DB.json(claimResponses);
    return Response.ok(json).build();
  }

  @GET
  @Produces({MediaType.APPLICATION_XML, "application/fhir+xml"})
  public Response searchClaimResponsesXml() {
    App.DB.setBaseUrl(uri.getBaseUri());
    Bundle claimResponses = App.DB.search(Database.CLAIM_RESPONSE);
    String xml = App.DB.xml(claimResponses);
    return Response.ok(xml).build();
  }
  
  @GET
  @Path("/{id}")
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+json"})
  public Response readClaimResponse(@PathParam("id") String id) {
    String json = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
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
    return Response.ok(json).build();
  }

  @GET
  @Path("/{id}")
  @Produces({MediaType.APPLICATION_XML, "application/fhir+xml"})
  public Response readClaimResponseXml(@PathParam("id") String id) {
    String xml = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
      Bundle claimResponses = App.DB.search(Database.CLAIM_RESPONSE);
      xml = App.DB.xml(claimResponses);
    } else {
      // Read
      ClaimResponse claimResponse = (ClaimResponse) App.DB.read(Database.CLAIM_RESPONSE, id);
      if (claimResponse == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      xml = App.DB.xml(claimResponse);
    }
    return Response.ok(xml).build();
  }
}
