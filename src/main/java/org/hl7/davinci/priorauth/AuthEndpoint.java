package org.hl7.davinci.priorauth;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.Database.Table;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/auth")
public class AuthEndpoint {

    static final Logger logger = PALogger.getLogger();
    static final SecureRandom random = new SecureRandom();

    @PostMapping(value = "/register", consumes = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
    public ResponseEntity<String> registerClient(HttpServletRequest request, HttpEntity<String> entity) {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR;
        String body = entity.getBody();
        String clientId = UUID.randomUUID().toString();
        try {
            // Read the body
            JSONObject jsonObject = (JSONObject) new JSONParser().parse(body);
            status = HttpStatus.BAD_REQUEST;
            String jwks = (String) jsonObject.get("jwks");
            String jwks_url = (String) jsonObject.get("jwks_url");
            status = HttpStatus.OK;

            // Add the new client to the database
            HashMap<String, Object> dataMap = new HashMap<String, Object>();
            dataMap.put("id", clientId);
            dataMap.put("jwks", jwks);
            dataMap.put("jwks_url", jwks_url);
            App.getDB().write(Table.CLIENT, dataMap);
        } catch (ParseException e) {
            logger.log(Level.SEVERE, "AuthEndpoint::registerClient:Unable to parse body\n" + body, e);
        }

        return ResponseEntity.status(status).contentType(MediaType.APPLICATION_JSON)
                .body("{ client_id: " + clientId + " }");
    }

    @PostMapping(value = "/token")
    public ResponseEntity<String> token(HttpServletRequest request,
            @RequestParam(name = "scope", required = true) String scope,
            @RequestParam(name = "grant_type", required = true) String grantType,
            @RequestParam(name = "client_assertion_type", required = true) String clientAssertionType,
            @RequestParam(name = "client_assertion", required = true) String clientAssertion) {
        // Check client_credentials and client_assertion_type
        if (!grantType.equals("client_credentials")
                && !clientAssertionType.equals("urn:ietf:params:oauth:client-assertion-type:jwt-bearer")) {
            String message = "AuthEndpoint::token:Invalid grant_type or client_assertion_type";
            logger.warning(message);
            Map<String, String> error = new HashMap<String, String>();
            error.put("error", "invalid_request");
            error.put("error_description", message);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).contentType(MediaType.APPLICATION_JSON)
                    .body(JSONObject.toJSONString(error));
        }

        // TODO: Validate JWT
        // JWTVerifier verifier;
        final String authToken = generateAuthToken();
        // App.getDB().update(Table.CLIENT, Collections.singletonMap("id", clientId),
        // Collections.singletonMap("token", authToken));

        Map<String, Object> response = new HashMap<String, Object>();
        response.put("access_token", authToken);
        response.put("token_type", "bearer");
        response.put("expires_in", 300);
        response.put("scope", "system/*.read");
        return ResponseEntity.status(HttpStatus.OK).contentType(MediaType.APPLICATION_JSON)
                .body(JSONObject.toJSONString(response));
    }

    private static String generateAuthToken() {
        byte[] bytes = new byte[10];
        random.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

}
