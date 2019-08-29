package org.hl7.davinci.priorauth;

import java.util.HashMap;
import java.util.Map;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hl7.davinci.priorauth.Endpoint.RequestType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The ClaimResponse endpoint to READ, SEARCH for, and DELETE ClaimResponses to
 * submitted claims.
 */
@RequestScoped
@Path("ClaimResponse")
public class ClaimResponseEndpoint {

  static final Logger logger = LoggerFactory.getLogger(ClaimResponseEndpoint.class);

  @Context
  private UriInfo uri;

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response readClaimResponse(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient, @QueryParam("status") String status) {
    Map<String, Object> constraintMap = new HashMap<String, Object>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Database.CLAIM_RESPONSE, constraintMap, uri, RequestType.JSON);
  }

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response readClaimResponseXml(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient, @QueryParam("status") String status) {
    Map<String, Object> constraintMap = new HashMap<String, Object>();
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Database.CLAIM_RESPONSE, constraintMap, uri, RequestType.XML);
  }

  @DELETE
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response deleteClaimResponse(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Database.CLAIM_RESPONSE, RequestType.JSON);
  }

  @DELETE
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+xml" })
  public Response deleteClaimResponseXml(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient) {
    return Endpoint.delete(id, patient, Database.CLAIM_RESPONSE, RequestType.XML);
  }

}
