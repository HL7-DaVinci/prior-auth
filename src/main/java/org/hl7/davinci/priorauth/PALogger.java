package org.hl7.davinci.priorauth;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class PALogger {

    private static String LOG_FILE = "priorauth.log";

    public static Logger getLogger(String name) {
        Logger logger = Logger.getLogger(name);
        try {
            createLogFileIfNotExists();
            FileHandler fh = new FileHandler(LOG_FILE);
            logger.addHandler(fh);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);
        } catch (SecurityException se) {
            logger.info("ERROR: SecurityException creating file handler. Logging will not go to file");
            logger.info(se.getMessage());
        } catch (IOException ioe) {
            logger.info("Log File IOException");
            ioe.printStackTrace(); // Will this print to file as well or just console?
        }
        return logger;

    }

    private static void createLogFileIfNotExists() throws IOException {
        File logFile = new File(LOG_FILE);
        logFile.createNewFile();
    }
}