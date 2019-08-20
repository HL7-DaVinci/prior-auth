package org.hl7.davinci.priorauth;

import java.util.Date;
import java.util.UUID;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
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
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.ResourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Claim endpoint to READ, SEARCH for, and DELETE submitted claims.
 */
@RequestScoped
@Path("Claim")
public class ClaimEndpoint {

  static final Logger logger = LoggerFactory.getLogger(ClaimEndpoint.class);

  String REQUIRES_BUNDLE = "Prior Authorization Claim/$submit Operation requires a Bundle with a single Claim as the first entry and supporting resources.";
  String REQUIRES_ID = "Instance Identifier is required: DELETE Claim?identifier=";
  String REQUIRES_PATIENT = "Patient Identifier is required: DELETE Claim?patient.identifier=";
  String DELETED_MSG = "Deleted Claim and all related and referenced resources.";

  @Context
  private UriInfo uri;

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response readClaim(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    logger.info("GET /Claim:" + id + "/" + patient + "/" + status + " fhir+json");
    if (patient == null) {
      logger.info("patient null");
      return Response.status(Status.UNAUTHORIZED).build();
    }
    String json = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
      Bundle claims;
      if (status == null) {
        claims = App.DB.search(Database.CLAIM, patient);
      } else {
        claims = App.DB.search(Database.CLAIM, patient, status);
      }
      json = App.DB.json(claims);
    } else {
      // Read
      Claim claim;
      if (status == null) {
        claim = (Claim) App.DB.read(Database.CLAIM, id, patient);
      } else {
        claim = (Claim) App.DB.read(Database.CLAIM, id, patient, status);
      }
      if (claim == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      json = App.DB.json(claim);
    }
    return Response.ok(json).build();
  }

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response readClaimXml(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    logger.info("GET /Claim:" + id + "/" + patient + "/" + status + " fhir+xml");
    if (patient == null) {
      logger.info("patient null");
      return Response.status(Status.UNAUTHORIZED).build();
    }
    String xml = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
      Bundle claims;
      if (status == null) {
        claims = App.DB.search(Database.CLAIM, patient);
      } else {
        claims = App.DB.search(Database.CLAIM, patient, status);
      }
      xml = App.DB.xml(claims);
    } else {
      // Read
      Claim claim;
      if (status == null) {
        claim = (Claim) App.DB.read(Database.CLAIM, id, patient);
      } else {
        claim = (Claim) App.DB.read(Database.CLAIM, id, patient, status);
      }
      if (claim == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      xml = App.DB.xml(claim);
    }
    return Response.ok(xml).build();
  }

  @DELETE
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response deleteClaim(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient) {
    logger.info("DELETE /Claim:" + id + "/" + patient + " fhir+json");
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
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+xml" })
  public Response deleteClaimXml(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient) {
    logger.info("DELETE /Claim:" + id + "/" + patient + " fhir+xml");
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

  @POST
  @Path("/$submit")
  @Consumes({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response submitOperation(String body) {
    logger.info("POST /Claim/$submit fhir+json");
    String id = null;
    Status status = Status.OK;
    String json = null;
    try {
      IBaseResource resource = App.FHIR_CTX.newJsonParser().parseResource(body);
      if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        if (bundle.hasEntry() && (bundle.getEntry().size() > 1) && bundle.getEntryFirstRep().hasResource()
            && bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.Claim) {
          IBaseResource response = processBundle(bundle);
          if (response.getIdElement().hasIdPart()) {
            id = response.getIdElement().getIdPart();
          }
          json = App.DB.json(response);
        } else {
          // Claim is required...
          status = Status.BAD_REQUEST;
          OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
          json = App.DB.json(error);
        }
      } else {
        // Bundle is required...
        status = Status.BAD_REQUEST;
        OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
        json = App.DB.json(error);
      }
    } catch (Exception e) {
      // The submission failed so spectacularly that we need
      // catch an exception and send back an error message...
      status = Status.BAD_REQUEST;
      OperationOutcome error = App.DB.outcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
      json = App.DB.json(error);
    }
    ResponseBuilder builder = Response.status(status).type("application/fhir+json").entity(json);
    if (id != null) {
      builder = builder.header("Location", uri.getBaseUri() + "ClaimResponse/" + id);
    }
    return builder.build();
  }

  @POST
  @Path("/$submit")
  @Consumes({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response submitOperationXml(String body) {
    logger.info("POST /Claim/$submit fhir+xml");
    String id = null;
    Status status = Status.OK;
    String xml = null;
    try {
      IBaseResource resource = App.FHIR_CTX.newXmlParser().parseResource(body);
      if (resource instanceof Bundle) {
        Bundle bundle = (Bundle) resource;
        if (bundle.hasEntry() && (bundle.getEntry().size() > 1) && bundle.getEntryFirstRep().hasResource()
            && bundle.getEntryFirstRep().getResource().getResourceType() == ResourceType.Claim) {
          IBaseResource response = processBundle(bundle);
          if (response.getIdElement().hasIdPart()) {
            id = response.getIdElement().getIdPart();
          }
          xml = App.DB.xml(response);
        } else {
          // Claim is required...
          status = Status.BAD_REQUEST;
          OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
          xml = App.DB.xml(error);
        }
      } else {
        // Bundle is required...
        status = Status.BAD_REQUEST;
        OperationOutcome error = App.DB.outcome(IssueSeverity.ERROR, IssueType.INVALID, REQUIRES_BUNDLE);
        xml = App.DB.xml(error);
      }
    } catch (Exception e) {
      // The submission failed so spectacularly that we need
      // catch an exception and send back an error message...
      status = Status.BAD_REQUEST;
      OperationOutcome error = App.DB.outcome(IssueSeverity.FATAL, IssueType.STRUCTURE, e.getMessage());
      xml = App.DB.xml(error);
    }
    ResponseBuilder builder = Response.status(status).type("application/fhir+xml").entity(xml);
    if (id != null) {
      builder = builder.header("Location", uri.getBaseUri() + "ClaimResponse/" + id);
    }
    return builder.build();
  }

  /**
   * Process the $submit operation Bundle. Theoretically, this is where business
   * logic should be implemented or overridden.
   * 
   * @param bundle Bundle with a Claim followed by other required resources.
   * @return ClaimResponse with the result.
   */
  private IBaseResource processBundle(Bundle bundle) {
    logger.info("processBundle");
    // Store the submission...
    // Generate a shared id...
    String id = UUID.randomUUID().toString();

    // get the patient
    Claim claim = (Claim) bundle.getEntryFirstRep().getResource();
    String patient = "";
    try {
      String[] patientParts = claim.getPatient().getReference().split("/");
      patient = patientParts[patientParts.length - 1];
      logger.info("processBundle: patient: " + patientParts[patientParts.length - 1]);
    } catch (Exception e) {
      logger.error("processBundle: error procesing patient: " + e.toString());
    }

    // Store the bundle...
    bundle.setId(id);
    App.DB.write(Database.BUNDLE, id, patient, bundle);

    // Store the claim...
    claim.setId(id);
    App.DB.write(Database.CLAIM, id, patient, claim);

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
    App.DB.write(Database.CLAIM_RESPONSE, id, patient, response);

    // Respond...
    return response;
  }
}
