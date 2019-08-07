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

  static final Logger logger = LoggerFactory.getLogger(ClaimEndpoint.class);

  String REQUIRES_ID = "Instance ID is required: DELETE Bundle/{id}";
  String DELETED_MSG = "Deleted Bundle and all related and referenced resources.";

  @Context
  private UriInfo uri;
  
  @GET
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+json"})
  public Response searchBundles() {
    logger.info("GET /Bundle fhir+json");
    App.DB.setBaseUrl(uri.getBaseUri());
    Bundle bundles = App.DB.search(Database.BUNDLE);
    String json = App.DB.json(bundles);
    return Response.ok(json).build();
  }

  @GET
  @Produces({MediaType.APPLICATION_XML, "application/fhir+xml"})
  public Response searchBundlesXml() {
    logger.info("GET /Bundle fhir+xml");
    App.DB.setBaseUrl(uri.getBaseUri());
    Bundle bundles = App.DB.search(Database.BUNDLE);
    String xml = App.DB.xml(bundles);
    return Response.ok(xml).build();
  }

  @GET
  @Path("/{id}")
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+json"})
  public Response readBundle(@PathParam("id") String id) {
    logger.info("GET /Bundle/" + id + " fhir+json");
    String json = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
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
    return Response.ok(json).build();
  }

  @GET
  @Path("/{id}")
  @Produces({MediaType.APPLICATION_XML, "application/fhir+xml"})
  public Response readBundleXml(@PathParam("id") String id) {
    logger.info("GET /Bundle/" + id + " fhir+xml");
    String xml = null;
    if (id == null) {
      // Search
      App.DB.setBaseUrl(uri.getBaseUri());
      Bundle bundles = App.DB.search(Database.BUNDLE);
      xml = App.DB.xml(bundles);
    } else {
      // Read
      Bundle bundle = (Bundle) App.DB.read(Database.BUNDLE, id);
      if (bundle == null) {
        return Response.status(Status.NOT_FOUND).build();
      }
      xml = App.DB.xml(bundle);
    }
    return Response.ok(xml).build();
  }

  @DELETE
  @Path("/{id}")
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+json"})
  public Response deleteBundle(@PathParam("id") String id) {
    logger.info("DELETE /Bundle/" + id + " fhir+json");
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
  public Response deleteBundleXml(@PathParam("id") String id) {
    logger.info("DELETE /Bundle/" + id + " fhir+xml");
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
