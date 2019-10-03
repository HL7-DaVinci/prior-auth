package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/Log")
public class LogEndpoint {

    static final Logger logger = PALogger.getLogger();

    @GetMapping("")
    public ResponseEntity<String> getLog() {
        logger.info("GET /Log");
        MultiValueMap<String, String> headers = new HttpHeaders();
        headers.add(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        try {
            String log = new String(Files.readAllBytes(Paths.get(PALogger.getLogPath())));
            return new ResponseEntity<>(log, headers, HttpStatus.OK);
        } catch (IOException e) {
            logger.log(Level.SEVERE, "LogEndpoint::IOException", e);
            return new ResponseEntity<>(headers, HttpStatus.BAD_REQUEST);
        }
    }
}
