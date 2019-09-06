package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

@RequestScoped
@Path("Log")
public class LogEndpoint {

    static final Logger logger = PALogger.getLogger();

    @Context
    private UriInfo uri;

    @GET
    @Path("/")
    public Response getLog() {
        logger.info("GET /Log");
        try {
            String log = new String(Files.readAllBytes(Paths.get(PALogger.getLogPath())));
            return Response.ok(log).build();
        } catch (IOException e) {
            logger.log(Level.SEVERE, "LogEndpoint::IOException", e);
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
    }
}
