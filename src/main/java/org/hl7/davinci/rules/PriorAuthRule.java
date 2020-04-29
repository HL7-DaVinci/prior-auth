package org.hl7.davinci.rules;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import org.opencds.cqf.cql.data.DataProvider;
import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.FhirLibrarySourceProvider;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.Library;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.fhir.r4.model.Bundle;

/**
 * The main class for executing priorauthorization rules
 */
public class PriorAuthRule {

    private static final Logger logger = PALogger.getLogger();

    private String cql;
    private Library library;
    private Context context;

    public PriorAuthRule(String request) {
        // TODO: add a proper map from request to CQL
        this.cql = getCQLFromFile(request + ".cql");
        this.library = createLibrary();
        this.context = new Context(library);
        // DataProvider dataProvider = new Data
        // this.context.registerDataProvider("http://hl7.org/fhir", dataProvider);
        // this.context.
    }

    public Disposition computeDisposition(Bundle bundle) {
        if (this.executeRule(bundle, "PRIORAUTH_GRANTED"))
            return Disposition.GRANTED;
        else if (this.executeRule(bundle, "PRIORAUTH_PENDED"))
            return Disposition.PENDING;
        else
            return Disposition.DENIED;
    }

    /**
     * Execute the rule on a given bundle and determine the disposition
     * 
     * @param bundle - Claim Bundle to run the rule against
     * @return true if the PriorAuth is granted, false otherwise
     */
    private boolean executeRule(Bundle bundle, String rule) {
        logger.info("PriorAuthRule::executeRule");
        boolean disposition = (boolean) this.context.resolveExpressionRef(rule).evaluate(context);
        logger.info("PriorAuthRule::executeRule:" + rule + ":" + disposition);
        return disposition;
    }

    /**
     * Read in the CQL file and return the contents
     * 
     * @param fileName - the name of the CQL file
     * @return string contents of the file or null if the file does not exist
     */
    private String getCQLFromFile(String fileName) {
        String cql = null;
        String path = "src/main/java/org/hl7/davinci/rules/" + fileName;
        try {
            cql = new String(Files.readAllBytes(Paths.get(path)));
        } catch (Exception e) {
            logger.warning("PriorAuthRule::getCQLFromFile:CQL File does not exist:" + fileName);
        }
        return cql;
    }

    /**
     * Helper method to create the Library for the constructor
     * 
     * @return Library or null
     */
    private Library createLibrary() {
        ModelManager modelManager = new ModelManager();
        LibraryManager libraryManager = new LibraryManager(modelManager);
        libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        CqlTranslator translator = CqlTranslator.fromText(this.cql, modelManager, libraryManager);
        Library library = null;
        try {
            library = CqlLibraryReader.read(new StringReader(translator.toXml()));
        } catch (IOException | JAXBException e) {
            logger.log(Level.SEVERE, "PriorAuthRule::createLibrary:exception reading library", e);
        }
        return library;
    }

}