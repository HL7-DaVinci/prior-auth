package org.hl7.davinci.priorauth;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.DELETE;
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
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ClaimResponse endpoint to READ, SEARCH for, and DELETE ClaimResponses to submitted claims.
 */
@RequestScoped
@Path("ClaimResponse")
public class ClaimResponseEndpoint {

  static final Logger logger = LoggerFactory.getLogger(ClaimEndpoint.class);

  String REQUIRES_ID = "Instance ID is required: DELETE ClaimResponse/{id}";
  String DELETED_MSG = "Deleted ClaimResponse and all related and referenced resources.";

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

  @DELETE
  @Path("/{id}")
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+json"})
  public Response deleteClaimResponse(@PathParam("id") String id) {
    Status status = Status.OK;
    OperationOutcome outcome = null;
    if (id == null) {
      // Do not delete everything
      // ID is required...
      status = Status.BAD_REQUEST;
      outcome = App.DB.outcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_ID);
    } else {
      // Cascading delete
      App.DB.delete(Database.BUNDLE, id);
      App.DB.delete(Database.CLAIM, id);
      App.DB.delete(Database.CLAIM_RESPONSE, id);
      outcome = App.DB.outcome(IssueSeverity.INFORMATION, IssueType.DELETED, DELETED_MSG);
    }
    String json = App.DB.json(outcome);
    return Response.status(status).entity(json).build();
  }

  @DELETE
  @Path("/{id}")
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+xml"})
  public Response deleteClaimResponseXml(@PathParam("id") String id) {
    Status status = Status.OK;
    OperationOutcome outcome = null;
    if (id == null) {
      // Do not delete everything
      // ID is required...
      status = Status.BAD_REQUEST;
      outcome = App.DB.outcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_ID);
    } else {
      // Cascading delete
      App.DB.delete(Database.BUNDLE, id);
      App.DB.delete(Database.CLAIM, id);
      App.DB.delete(Database.CLAIM_RESPONSE, id);
      outcome = App.DB.outcome(IssueSeverity.INFORMATION, IssueType.DELETED, DELETED_MSG);
    }
    String xml = App.DB.xml(outcome);
    return Response.status(status).entity(xml).build();
  }

}
