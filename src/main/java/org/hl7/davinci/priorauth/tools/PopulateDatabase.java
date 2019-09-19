package org.hl7.davinci.priorauth.tools;

import org.hl7.davinci.priorauth.Database;
import org.hl7.davinci.priorauth.FhirUtils;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.ClaimResponse;

import ca.uhn.fhir.context.FhirContext;

public class PopulateDatabase {

    public final static Database DB = new Database();

    private static Bundle getResource(String fileName) throws FileNotFoundException {
        Path modulesFolder = Paths.get("src/main/java/org/hl7/davinci/priorauth/tools/DatabaseResources");
        Path fixture = modulesFolder.resolve(fileName);
        FileInputStream inputStream = new FileInputStream(fixture.toString());
        return (Bundle) FhirContext.forR4().newJsonParser().parseResource(inputStream);
    }

    private static boolean writeClaim(Bundle claimBundle, String related) {
        Claim claim = (Claim) claimBundle.getEntry().get(0).getResource();
        String id = claim.getId();
        String patient = FhirUtils.getPatientIdFromResource(claim);
        String status = FhirUtils.getStatusFromResource(claim);
        DB.delete(Database.CLAIM, id, patient);
        DB.delete(Database.BUNDLE, id, patient);
        Map<String, Object> dataMap = new HashMap<String, Object>();
        dataMap.put("id", id);
        dataMap.put("patient", patient);
        dataMap.put("resource", claimBundle);

        DB.write(Database.BUNDLE, dataMap);

        dataMap.put("status", status);
        if (related != null)
            dataMap.put("related", related);

        return DB.write(Database.CLAIM, dataMap);
    }

    private static boolean writeClaimResponse(Bundle claimResponseBundle, String claimId) {
        ClaimResponse claimResponse = (ClaimResponse) claimResponseBundle.getEntry().get(0).getResource();
        String id = claimResponse.getId();
        String patient = FhirUtils.getPatientIdFromResource(claimResponse);
        String status = FhirUtils.getStatusFromResource(claimResponse);
        DB.delete(Database.CLAIM_RESPONSE, id, patient);

        Map<String, Object> dataMap = new HashMap<String, Object>();
        dataMap.put("id", id);
        dataMap.put("patient", patient);
        dataMap.put("claimId", claimId);
        dataMap.put("status", status);
        dataMap.put("resource", claimResponseBundle);

        return DB.write(Database.CLAIM_RESPONSE, dataMap);
    }

    public static void fillDB() throws FileNotFoundException {
        // Submit a claim and then update twice
        writeClaim(getResource("Claim.json"), null);
        writeClaimResponse(getResource("DeniedResponse.json"), "451fe716-4701-4cdf-b5bb-a6eedbe43bbb");
        writeClaim(getResource("ClaimUpdate1.json"), "451fe716-4701-4cdf-b5bb-a6eedbe43bbb");
        writeClaimResponse(getResource("GrantedResponse.json"), "ee7f76f8-5e4e-4733-8f85-3fe3c29cf30f");
        writeClaim(getResource("ClaimUpdate2.json"), "ee7f76f8-5e4e-4733-8f85-3fe3c29cf30f");
        writeClaimResponse(getResource("PendedClaimResponse.json"), "f57a4af7-e3b7-475e-9fae-b31cc0319e36");
        writeClaimResponse(getResource("PendedFinalResponse.json"), "f57a4af7-e3b7-475e-9fae-b31cc0319e36");

        Map<String, Object> dataMap = new HashMap<String, Object>();
        dataMap.put("id", "f57a4af7-e3b7-475e-9fae-b31cc0319e36");
        dataMap.put("sequence", "1");
        dataMap.put("status", "active");
        DB.write(Database.CLAIM_ITEM, dataMap);
        dataMap.replace("sequence", "2");
        DB.write(Database.CLAIM_ITEM, dataMap);
    }

    public static void main(String[] args) throws FileNotFoundException {
        fillDB();
        System.exit(0);
    }
}