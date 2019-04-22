package org.hl7.davinci.priorauth;

import java.util.Date;
import java.util.UUID;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.PathParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.ResponseBuilder;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;
import org.hl7.fhir.r4.model.ClaimResponse.ClaimResponseStatus;
import org.hl7.fhir.r4.model.ClaimResponse.RemittanceOutcome;
import org.hl7.fhir.r4.model.ClaimResponse.Use;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.hl7.fhir.r4.model.OperationOutcome.OperationOutcomeIssueComponent;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;

/**
 * The Claim endpoint to READ and SEARCH for submitted claims.
 */
@RequestScoped
@Path("Claim")
public class ClaimEndpoint {

  String REQUIRES_BUNDLE = "Prior Authorization Claim/$submit Operation requires a Bundle with a single Claim and supporting resources.";

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

  @POST
  @Path("/$submit")
  public Response submitOperation(String body) {
    String id = null;
    Status status = Status.OK;
    String json = null;
    try {
      IBaseResource resource = App.FHIR_CTX.newJsonParser().parseResource(body);
      if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        if (bundle.hasEntry() && (bundle.getEntry().size() > 1)
            && bundle.getEntryFirstRep().hasResource()
            && bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.Claim) {
          // Store the submission...
          // Generate a shared id...
          id = UUID.randomUUID().toString();

          // Store the bundle...
          bundle.setId(id);
          App.DB.write(Database.BUNDLE, id, bundle);

          // Store the claim...
          Claim claim = (Claim) bundle.getEntryFirstRep().getResource();
          claim.setId(id);
          App.DB.write(Database.CLAIM, id, claim);

          // Process the claim...
          // TODO

          // Generate the claim response...
          ClaimResponse response = new ClaimResponse();
          response.setStatus(ClaimResponseStatus.ACTIVE);
          response.setType(claim.getType());
          response.setUse(Use.PREAUTHORIZATION);
          response.setPatient(claim.getPatient());
          response.setCreated(new Date());
          if (claim.hasInsurer()) {
            response.setInsurer(claim.getInsurer());
          } else {
            response.setInsurer(new Reference().setDisplay("Unknown"));
          }
          response.setRequest(new Reference(uri.getBaseUri() + "Claim/" + id));
          response.setOutcome(RemittanceOutcome.COMPLETE);
          response.setDisposition("Granted");
          response.setPreAuthRef(id);
          // TODO response.setPreAuthPeriod(period)?
          // Store the claim response...
          response.setId(id);
          App.DB.write(Database.CLAIM_RESPONSE, id, response);

          // Respond...
          json = App.DB.json(response);
        } else {
          // Claim is required...
          status = Status.BAD_REQUEST;
          OperationOutcome error = error(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
          json = App.DB.json(error);
        }
      } else {
        // Bundle is required...
        status = Status.BAD_REQUEST;
        OperationOutcome error = error(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
        json = App.DB.json(error);
      }
    } catch (Exception e) {
      // The submission failed so spectacularly that we need
      // catch an exception and send back an error message...
      status = Status.BAD_REQUEST;
      OperationOutcome error = error(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
      json = App.DB.json(error);
    }
    ResponseBuilder builder = Response.status(status).encoding(MediaType.APPLICATION_JSON).entity(json);
    if (id != null) {
      builder = builder.header("Location", uri.getBaseUri() + "ClaimResponse/" + id);
    }
    return builder.build();
  }

  private OperationOutcome error(IssueSeverity severity, IssueType type, String message) {
    OperationOutcome error = new OperationOutcome();
    OperationOutcomeIssueComponent issue = error.addIssue();
    issue.setSeverity(severity);
    issue.setCode(type);
    issue.setDiagnostics(message);
    return error;
  }
}
