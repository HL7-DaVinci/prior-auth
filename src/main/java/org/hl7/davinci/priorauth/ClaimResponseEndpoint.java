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

/**
 * The ClaimResponse endpoint to READ, SEARCH for, and DELETE ClaimResponses to
 * submitted claims.
 */
@RequestScoped
@Path("ClaimResponse")
public class ClaimResponseEndpoint {

  @Context
  private UriInfo uri;

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_JSON, "application/fhir+json" })
  public Response readClaimResponseJson(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient, @QueryParam("status") String status) {
    return readClaimResponse(id, patient, status, RequestType.JSON);
  }

  @GET
  @Path("/")
  @Produces({ MediaType.APPLICATION_XML, "application/fhir+xml" })
  public Response readClaimResponseXml(@QueryParam("identifier") String id,
      @QueryParam("patient.identifier") String patient, @QueryParam("status") String status) {
    return readClaimResponse(id, patient, status, RequestType.XML);
  }

  public Response readClaimResponse(String id, String patient, String status, RequestType requestType) {
    Map<String, Object> constraintMap = new HashMap<String, Object>();

    // get the claim id from the claim response id
    constraintMap.put("id", id);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    String claimId = App.getDB().readString(Database.CLAIM_RESPONSE, constraintMap, "claimId");

    // get the most recent claim id
    claimId = App.getDB().getMostRecentId(claimId);

    // get the most reset claim response
    constraintMap.clear();
    constraintMap.put("claimId", claimId);
    constraintMap.put("patient", patient);
    if (status != null)
      constraintMap.put("status", status);
    return Endpoint.read(Database.CLAIM_RESPONSE, constraintMap, uri, requestType);
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
