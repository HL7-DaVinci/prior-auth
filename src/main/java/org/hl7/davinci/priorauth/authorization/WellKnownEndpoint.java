package org.hl7.davinci.priorauth.authorization;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.endpoint.Endpoint;
import org.hl7.davinci.priorauth.PALogger;
import org.json.simple.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/.well-known")
public class WellKnownEndpoint {
   
    static final Logger logger = PALogger.getLogger();

    @GetMapping(value = "smart-configuration", produces = {MediaType.APPLICATION_JSON_VALUE})
    public ResponseEntity<String> smartConfiguration(HttpServletRequest request) {
        logger.info("GET /.well-known/smart-configuration");
        App.setBaseUrl(Endpoint.getServiceBaseUrl(request));

        Map<String, Object> response = new HashMap<>();
        response.put("registration_endpoint", App.getBaseUrl() + "/auth/register");
        response.put("token_endpoint", App.getBaseUrl() + "/auth/token");
        response.put("response_types_supported", "token");
        response.put("scopes_supported", AuthUtils.getSupportedScopes());

        return ResponseEntity.status(HttpStatus.OK).body(JSONObject.toJSONString(response));
    }
}
