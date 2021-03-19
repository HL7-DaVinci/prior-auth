package org.hl7.davinci.priorauth;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

public class PALogger {

    /**
     * Singelton instance of PALogger
     */
    private static PALogger singletonPALogger;

    private Logger logger;
    private static String LOG_FILE = "priorauth.log";

    private PALogger() {
        this.logger = Logger.getLogger("PriorAuth");
        try {
            createLogFileIfNotExists();
            FileHandler fh = new FileHandler(LOG_FILE);
            SimpleFormatter formatter = new SimpleFormatter();
            fh.setFormatter(formatter);

            if (App.isDebugModeEnabled())
                this.setLevel(Level.FINEST);
            else
                this.setLevel(Level.INFO);

            this.logger.addHandler(fh);

        } catch (SecurityException e) {
            this.logger.log(Level.SEVERE,
                    "PALogger::PALogger:SecurityException(SecurityException creating file handler. Logging will not go to file)",
                    e);
        } catch (IOException e) {
            this.logger.log(Level.SEVERE, "PALogger::PALogger:IOException", e);
        }
    }

    /**
     * Get the logger
     * 
     * @return the logger
     */
    public static Logger getLogger() {
        if (singletonPALogger == null)
            singletonPALogger = new PALogger();
        return singletonPALogger.logger;
    }

    /**
     * Get the path of the log file
     * 
     * @return the path of the log file
     */
    public static String getLogPath() {
        return LOG_FILE;
    }

    /**
     * Sets the level of logging to the logger and all handlers
     * 
     * @param level the new level
     */
    private void setLevel(Level level) {
        this.logger.info("PALogger::Setting logger level to " + level.getName());
        this.logger.setLevel(level);
        for (Handler handler : this.logger.getHandlers()) {
            handler.setLevel(level);
        }
    }

    private static void createLogFileIfNotExists() throws IOException {
        File logFile = new File(LOG_FILE);
        logFile.createNewFile();
    }
}