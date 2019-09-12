package org.hl7.davinci.priorauth;

import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/home")
public class SpringEndpoint {

    static final Logger logger = PALogger.getLogger();

    @GetMapping("/test/{pathvar}")
    public ResponseEntity<String> getBundles(HttpServletRequest request, @PathVariable("pathvar") String pathvar,
            @RequestParam(name = "id", required = false) String id) {
        logger.info("SpringEndpoint::Received pathvar: " + pathvar);
        logger.info("URIINFO::Request uri " + request.getRequestURL().toString());
        if (id != null)
            logger.info("SpringEndpoint::Received id: " + id);
        return new ResponseEntity<>("Hello world! " + pathvar, HttpStatus.OK);
    }
}