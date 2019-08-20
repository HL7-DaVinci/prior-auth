package org.hl7.davinci.priorauth;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.QueryParam;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;
import javax.ws.rs.core.UriInfo;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.OperationOutcome;
import org.hl7.fhir.r4.model.OperationOutcome.IssueSeverity;
import org.hl7.fhir.r4.model.OperationOutcome.IssueType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The Bundle endpoint to READ, SEARCH for, and DELETE submitted Bundles.
 */
@RequestScoped
@Path("Bundle")
public class BundleEndpoint {

  static final Logger logger = LoggerFactory.getLogger(BundleEndpoint.class);

  String REQUIRES_ID = "Instance ID is required: DELETE Bundle?identifier=";
  String REQUIRES_PATIENT = "Patient Identifier is required: DELETE Bundle?patient.identifier=";
  String DELETED_MSG = "Deleted Bundle and all related and referenced resources.";

  @Context
  private UriInfo uri;

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response readBundle(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    logger.info("GET /Bundle:" + id + "/" + patient + " fhir+json");
    if (patient == null) {
      logger.info("patient null");
      return Response.status(Status.UNAUTHORIZED).build();
    }
    String json = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
      Bundle bundles;
      if (status == null) {
        bundles = App.DB.search(Database.BUNDLE, patient);
      } else {
        bundles = App.DB.search(Database.BUNDLE, patient, status);
      }
      json = App.DB.json(bundles);
    } else {
      // Read
      Bundle bundle;
      if (status == null) {
        bundle = (Bundle) App.DB.read(Database.BUNDLE, id, patient);
      } else {
        bundle = (Bundle) App.DB.read(Database.BUNDLE, id, patient, status);
      }
      if (bundle == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      json = App.DB.json(bundle);
    }
    return Response.ok(json).build();
  }

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response readBundleXml(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient,
      @QueryParam("status") String status) {
    logger.info("GET /Bundle:" + id + "/" + patient + " fhir+xml");
    if (patient == null) {
      logger.info("patient null");
      return Response.status(Status.UNAUTHORIZED).build();
    }
    String xml = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
      Bundle bundles;
      if (status == null) {
        bundles = App.DB.search(Database.BUNDLE, patient);
      } else {
        bundles = App.DB.search(Database.BUNDLE, patient, status);
      }
      xml = App.DB.xml(bundles);
    } else {
      // Read
      Bundle bundle;
      if (status == null) {
        bundle = (Bundle) App.DB.read(Database.BUNDLE, id, patient);
      } else {
        bundle = (Bundle) App.DB.read(Database.BUNDLE, id, patient, status);
      }
      if (bundle == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      xml = App.DB.xml(bundle);
    }
    return Response.ok(xml).build();
  }

  @DELETE
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response deleteBundle(@QueryParam("identifier") String id, @QueryParam("patient.identifier") String patient) {
    logger.info("DELETE /Bundle:" + id + "/" + patient + " fhir+json");
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
  public Response deleteBundleXml(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient) {
    logger.info("DELETE /Bundle:" + id + "/" + patient + " fhir+xml");
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
