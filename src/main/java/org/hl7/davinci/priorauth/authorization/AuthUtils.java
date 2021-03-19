package org.hl7.davinci.priorauth.authorization;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.fhir.r4.model.ContactPoint;
import org.hl7.fhir.r4.model.Organization;

public class AuthUtils {

    static final Logger logger = PALogger.getLogger();

    public static final int TOKEN_LIFE_MIN = 5;
    private static final String ADMIN_TOKEN = "Y3YWq2l08kvFqy50fQJY";

    /**
     * Populate the Client table with default data
     */
    public static void populateClientTable() {
        // Auto register DTR RI
        String clientId = "797da153-9a36-4493-9910-10648a4deb03";
        Organization organization = new Organization();
        organization.setId(clientId);
        organization.setName("MITRE");
        ContactPoint telecom = new ContactPoint();
        telecom.setValue("blangley@example.com");
        organization.setTelecom(Collections.singletonList(telecom));

        HashMap<String, Object> dataMap = new HashMap<>();
        dataMap.put("id", clientId);
        dataMap.put("jwks", "{\"keys\":[{\"ext\":true,\"kty\":\"RSA\",\"e\":\"AQAB\",\"kid\":\"3ab8b05b64d799e289e10a201786b38c\",\"key_ops\":[\"verify\"],\"alg\":\"RS384\",\"n\":\"52tcPrGJgzyGqjcUiHsbSk_PxQ7Uovz4saGxva3iyBoidsekonigJJ3LnFlHYb3vBa2NA-0GpX2E1KhNNcYWAWQFcu069zi0YZ_wWGn6PWZURuonUoKH4dGHggym3xxVUxuA8OPubGe5ji56eic4RPINg0z-TtPlS-H9dnDIVznRUTXf3fy2dqWMuTY4D2e4fXGII6OpFAsEyrOqIoR8pLWGu7AiQkothunopp9q_Gu2xqB6l8BNulsbiwsQMeRE-9SGfeFpyblHiizHDwSqeZ3iv49Ellk4yjmrf6wOaFA2IXRqL1cCLj86B6KIDrjdzOL4lOSiES-PclNpioG2rQ\"}]}");
        dataMap.put("jwks_url", null);
        dataMap.put("token", "Y3YWq2l08kvFqy50fQJY");
        dataMap.put("organization", organization);
        App.getDB().write(Table.CLIENT, dataMap);

        // Auto register Aegis Touchstone
        clientId = "d9dc3d3e-5be5-4882-832b-e74a93c2d01b";
        organization = new Organization();
        organization.setId(clientId);
        organization.setName("Aegis Touchstone");
        telecom = new ContactPoint();
        telecom.setValue("blangley@example.com");
        organization.setTelecom(Collections.singletonList(telecom));

        dataMap = new HashMap<>();
        dataMap.put("id", clientId);
        dataMap.put("jwks", "{\"keys\":[{\"kty\":\"RSA\",\"e\":\"AQAB\",\"use\":\"sig\",\"kid\":\"QjemCvDBrY2NjImHhd2gO9M6ghMW0ZK3R6wcmKGk6NMvgj_H5NZIeCOeopx5brEX\",\"alg\":\"RS384\",\"n\":\"iLVJtm0Tb6-S5qomxnmwxaHtWPLMu0K3lLCIwz_7KLKgEsbN0FUaHU0SivGsztD9LRsQ72QtBuyA9LxmK5m_50Lx7hbfUxUPR9rzdj8sz3isiSzryDFm0jq3LsjSu27duiGU0c2oxqpqiC5LWJcW6JcRbtHwnMkU2T0_mdwLLFcdv8ZCxY8ce4vG4E4V3kUmiOYGClkP3eeHiMdXJQ_r4SFwXAuH-uWv_YtkAFE3ChpVlyKsIvXtnncQPoRmf1kPq0obp7veQZvjHIzM9A56TfsnSfBBFwx2oxEX0T2zArDKf3FdMgCzoi63WUUiLmRoPIl5lrtzg17GAhzbAR_WzQ\"}]}");
        dataMap.put("jwks_url", null);
        dataMap.put("token", "Y3YWq2l08kvFqy50fQJY");
        dataMap.put("organization", organization);
        App.getDB().write(Table.CLIENT, dataMap);
    }

    /**
     * Determine whether or not the access token in the Authorization header is authorized
     * 
     * @param request HttpServletRequest
     * @return true if the access token is valid, false if invalid or no bearer token provided
     */
    public static boolean validateAccessToken(HttpServletRequest request) {
        String accessToken = getAccessToken(request);
        if (accessToken == null) return false;

        if (accessToken.equals(ADMIN_TOKEN)) {
            logger.fine("AuthEndpoint::validateAccessToken:Admin token used");
            return true;
        }

        String timestamp = App.getDB().readString(Table.CLIENT, Collections.singletonMap("token", accessToken), "timestamp");
        if (timestamp == null) return false;

        // Validate token age
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date issuedAt;
		try {
			issuedAt = formatter.parse(timestamp);
		} catch (java.text.ParseException e) {
            e.printStackTrace();
            logger.log(Level.SEVERE, "AuthEndpoint::validateAccessToken:Error parsing date", e);
            return false;
        }
        
        Date now = new Date();
        long tokenAgeMs = now.getTime() - issuedAt.getTime();

        if (tokenAgeMs > TOKEN_LIFE_MIN * 60000) {
            logger.severe("AuthEndpoint::validateAccessToken:Access Token expired. Max Age " + TOKEN_LIFE_MIN*60000 + " but token age is " + tokenAgeMs);
            return false;
        }

        return true;
    }

    /**
     * Helper method to get client id via the access token from the request
     * 
     * @param request HttpServletRequest
     * @return clientId if it exists or a string explaining why it does not
     */
    public static String getClientId(HttpServletRequest request) {
        String accessToken = getAccessToken(request);
        if (accessToken == null) return "Unknown Client: No Access Token";

        if (accessToken.equals(ADMIN_TOKEN)) return "Admin";

        String clientId = App.getDB().readString(Table.CLIENT, Collections.singletonMap("token", accessToken), "id");
        return clientId == null ? "Unknown Client: Invalid Access Token" : clientId;
    }

    /**
     * Helper method to get a list of all the supported scopes
     * 
     * @return list of scopes supported
     */
    public static List<String> getSupportedScopes() {
        ArrayList<String> scopes = new ArrayList<>();
        scopes.add("system/*.read");
        scopes.add("offline_access");
        return scopes;
    }

    /**
     * Helper method to read the access token from the Authorization: Bearer {token} header
     * 
     * @param request request to get the token from
     * @return access token if it was included, otherwise null
     */
    private static String getAccessToken(HttpServletRequest request) {
        String authHeader = request.getHeader("Authorization");
        logger.log(Level.FINE, "AuthEndpoint::getAccessToken:Authorization header: " + authHeader);
        if (authHeader != null) {
            String regex = "Bearer (.*)";
            Pattern pattern = Pattern.compile(regex);
            Matcher accessTokenMatcher = pattern.matcher(authHeader);
            if (accessTokenMatcher.find() && accessTokenMatcher.groupCount() == 1) {
                return accessTokenMatcher.group(1);
            }
        }

        return null;
    }
}
