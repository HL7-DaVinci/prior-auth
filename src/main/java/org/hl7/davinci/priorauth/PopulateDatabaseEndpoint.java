package org.hl7.davinci.priorauth;

import org.hl7.davinci.priorauth.Database;
import org.hl7.davinci.priorauth.FhirUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;
import javax.ws.rs.core.Response.Status;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;

import ca.uhn.fhir.context.FhirContext;

@RequestScoped
@Path("PopulateDatabase")
public class PopulateDatabaseEndpoint {

    static final Logger logger = PALogger.getLogger();

    @Context
    private UriInfo uri;

    @GET
    @Path("/")
    public Response populateDatabase() {
        logger.info("PopulateDatabaseEndpoint::Prepopulating database with data");
        String responseData = "Success!";
        Status status = Status.OK;

        try {
            // Submit a claim and then update twice
            writeClaim(getResource("Claim.json"), null, "2200-09-09 15:23:34.1");
            writeClaimResponse(getResource("DeniedResponse.json"), "451fe716-4701-4cdf-b5bb-a6eedbe43bbb",
                    "2200-09-09 15:23:39.5");
            writeClaim(getResource("ClaimUpdate1.json"), "451fe716-4701-4cdf-b5bb-a6eedbe43bbb",
                    "2200-09-09 18:37:15.2");
            writeClaimResponse(getResource("GrantedResponse.json"), "ee7f76f8-5e4e-4733-8f85-3fe3c29cf30f",
                    "2200-09-09 18:37:22.5");
            writeClaim(getResource("ClaimUpdate2.json"), "ee7f76f8-5e4e-4733-8f85-3fe3c29cf30f",
                    "2200-09-10 08:43:32.9");
            writeClaimResponse(getResource("PendedClaimResponse.json"), "f57a4af7-e3b7-475e-9fae-b31cc0319e36",
                    "2200-09-10 08:43:38.3");
            writeClaimResponse(getResource("PendedFinalResponse.json"), "f57a4af7-e3b7-475e-9fae-b31cc0319e36",
                    "2200-09-10 10:55:03.0");

            Map<String, Object> dataMap = new HashMap<String, Object>();
            dataMap.put("id", "f57a4af7-e3b7-475e-9fae-b31cc0319e36");
            dataMap.put("sequence", "1");
            dataMap.put("status", "active");
            dataMap.put("timestamp", "2200-09-10 08:43:32.9");
            App.getDB().write(Database.CLAIM_ITEM, dataMap);
            dataMap.replace("sequence", "2");
            App.getDB().write(Database.CLAIM_ITEM, dataMap);
        } catch (FileNotFoundException e) {
            status = Status.BAD_REQUEST;
            responseData = "ERROR: Unable to read all resources to populate database";
            logger.log(Level.SEVERE,
                    "PopulateDatabaseEndpoint::FileNotFoundException reading resource file to insert into database", e);
        }

        logger.info("PopulateDatabaseEndpoint::Prepopulating database complete");
        return Response.status(status).type("application/fhir+json").entity(responseData).build();
    }

    private static Bundle getResource(String fileName) throws FileNotFoundException {
        java.nio.file.Path modulesFolder = Paths.get("src/main/resources/DatabaseResources");
        java.nio.file.Path fixture = modulesFolder.resolve(fileName);
        FileInputStream inputStream = new FileInputStream(fixture.toString());
        return (Bundle) FhirContext.forR4().newJsonParser().parseResource(inputStream);
    }

    private static boolean writeClaim(Bundle claimBundle, String related, String timestamp) {
        Claim claim = (Claim) claimBundle.getEntry().get(0).getResource();
        String id = claim.getId();
        String patient = FhirUtils.getPatientIdFromResource(claim);
        String status = FhirUtils.getStatusFromResource(claim);
        App.getDB().delete(Database.CLAIM, id, patient);
        App.getDB().delete(Database.BUNDLE, id, patient);
        Map<String, Object> dataMap = new HashMap<String, Object>();
        dataMap.put("id", id);
        dataMap.put("patient", patient);
        dataMap.put("resource", claimBundle);
        dataMap.put("timestamp", timestamp);

        App.getDB().write(Database.BUNDLE, dataMap);

        dataMap.put("status", status);
        if (related != null)
            dataMap.put("related", related);

        return App.getDB().write(Database.CLAIM, dataMap);
    }

    private static boolean writeClaimResponse(Bundle claimResponseBundle, String claimId, String timestamp) {
        ClaimResponse claimResponse = (ClaimResponse) claimResponseBundle.getEntry().get(0).getResource();
        String id = claimResponse.getId();
        String patient = FhirUtils.getPatientIdFromResource(claimResponse);
        String status = FhirUtils.getStatusFromResource(claimResponse);
        App.getDB().delete(Database.CLAIM_RESPONSE, id, patient);

        Map<String, Object> dataMap = new HashMap<String, Object>();
        dataMap.put("id", id);
        dataMap.put("patient", patient);
        dataMap.put("claimId", claimId);
        dataMap.put("status", status);
        dataMap.put("timestamp", timestamp);
        dataMap.put("resource", claimResponseBundle);

        return App.getDB().write(Database.CLAIM_RESPONSE, dataMap);
    }

}