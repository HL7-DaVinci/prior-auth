package org.hl7.davinci.priorauth;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.*;
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

  static final Logger logger = LoggerFactory.getLogger(ClaimResponseEndpoint.class);

  String REQUIRES_ID = "Instance ID is required: DELETE ClaimResponse?identifier=";
  String REQUIRES_PATIENT = "Patient Identifier is required: DELETE ClaimResponse?patient.identifier=";
  String DELETED_MSG = "Deleted ClaimResponse and all related and referenced resources.";

  @Context
  private UriInfo uri;
  
  @GET
  @Path("/")
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+json"})
  public Response readClaimResponse(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient, @QueryParam("status") String status) {
    logger.info("GET /ClaimResponse:" + id + "/" + patient + " fhir+json");
    if (patient == null) {
      logger.info("patient null");
      return Response.status(Status.UNAUTHORIZED).build();
    }
    String json = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
      Bundle claimResponses = App.DB.search(Database.CLAIM_RESPONSE, patient);
      json = App.DB.json(claimResponses);
    } else {
      // Read
      ClaimResponse claimResponse = (ClaimResponse) App.DB.read(Database.CLAIM_RESPONSE, id, patient);
      if (claimResponse == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      json = App.DB.json(claimResponse);
    }
    return Response.ok(json).build();
  }

  @GET
  @Path("/")
  @Produces({MediaType.APPLICATION_XML, "application/fhir+xml"})
  public Response readClaimResponseXml(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient, @QueryParam("status") String status) {
    logger.info("GET /ClaimResponse:" + id + "/" + patient + " fhir+json");
    if (patient == null) {
      logger.info("patient null");
      return Response.status(Status.UNAUTHORIZED).build();
    }
    String xml = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
      Bundle claimResponses = App.DB.search(Database.CLAIM_RESPONSE, patient);
      xml = App.DB.xml(claimResponses);
    } else {
      // Read
      ClaimResponse claimResponse = (ClaimResponse) App.DB.read(Database.CLAIM_RESPONSE, id, patient);
      if (claimResponse == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      xml = App.DB.xml(claimResponse);
    }
    return Response.ok(xml).build();
  }

  @DELETE
  @Path("/")
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+json"})
  public Response deleteClaimResponse(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient) {
    logger.info("DELETE /ClaimResponse:" + id + "/" + patient + " fhir+json");
    Status status = Status.OK;
    OperationOutcome outcome = null;
    if (id == null) {
      // Do not delete everything
      // ID is required...
      status = Status.BAD_REQUEST;
      outcome = App.DB.outcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_ID);
    } else if (patient == null) {
      // Do not delete everything
      // Patient ID is required...
      status = Status.UNAUTHORIZED;
      outcome = App.DB.outcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_PATIENT);
    } else {
      // Cascading delete
      App.DB.delete(Database.BUNDLE, id, patient);
      App.DB.delete(Database.CLAIM, id, patient);
      App.DB.delete(Database.CLAIM_RESPONSE, id, patient);
      outcome = App.DB.outcome(IssueSeverity.INFORMATION, IssueType.DELETED, DELETED_MSG);
    }
    String json = App.DB.json(outcome);
    return Response.status(status).entity(json).build();
  }

  @DELETE
  @Path("/")
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+xml"})
  public Response deleteClaimResponseXml(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient) {
    logger.info("DELETE /ClaimResponse:" + id + "/" + patient + " fhir+xml");
    Status status = Status.OK;
    OperationOutcome outcome = null;
    if (id == null) {
      // Do not delete everything
      // ID is required...
      status = Status.BAD_REQUEST;
      outcome = App.DB.outcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_ID);
    } else if (patient == null) {
      // Do not delete everything
      // Patient ID is required...
      status = Status.UNAUTHORIZED;
      outcome = App.DB.outcome(IssueSeverity.ERROR, IssueType.REQUIRED, REQUIRES_PATIENT);
    } else {
      // Cascading delete
      App.DB.delete(Database.BUNDLE, id, patient);
      App.DB.delete(Database.CLAIM, id, patient);
      App.DB.delete(Database.CLAIM_RESPONSE, id, patient);
      outcome = App.DB.outcome(IssueSeverity.INFORMATION, IssueType.DELETED, DELETED_MSG);
    }
    String xml = App.DB.xml(outcome);
    return Response.status(status).entity(xml).build();
  }

}
