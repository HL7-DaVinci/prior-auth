package org.hl7.davinci.priorauth;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
// import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class PALogger {

    private static PALogger singletonPALogger;
    private Logger logger;
    private static String LOG_FILE = "priorauth.log";

    private PALogger() {
        this.logger = Logger.getLogger("PriorAuth");
        try {
            createLogFileIfNotExists();
            FileHandler fh = new FileHandler(LOG_FILE);
            this.logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
            // this.logger.setLevel(Level.FINEST);
        } catch (SecurityException se) {
            this.logger.info("ERROR: SecurityException creating file handler. Logging will not go to file");
            this.logger.info(se.getMessage());
        } catch (IOException ioe) {
            this.logger.info("Log File IOException");
            ioe.printStackTrace(); // Will this print to file as well or just console?
        }
    }

    public static Logger getLogger() {
        if (singletonPALogger == null)
            singletonPALogger = new PALogger();
        return singletonPALogger.logger;
    }

    private static void createLogFileIfNotExists() throws IOException {
        File logFile = new File(LOG_FILE);
        logFile.createNewFile();
    }
}