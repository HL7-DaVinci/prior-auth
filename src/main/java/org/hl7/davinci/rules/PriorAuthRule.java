package org.hl7.davinci.rules;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import org.opencds.cqf.cql.data.DataProvider;
import org.opencds.cqf.cql.data.CompositeDataProvider;
import org.opencds.cqf.cql.model.R4FhirModelResolver;

import ca.uhn.fhir.context.FhirContext;

import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.FhirLibrarySourceProvider;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.Library;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.fhir.r4.model.Bundle;

/**
 * The main class for executing priorauthorization rules
 */
public class PriorAuthRule {

    private static final Logger logger = PALogger.getLogger();

    private String cql;
    private Context context;

    /**
     * Enum to represent the different CQL rule names. All of the prior auth rule
     * files should include all of these define expressions
     */
    public enum Rule {
        GRANTED("PRIORAUTH_GRANTED"), PENDED("PRIORAUTH_PENDED");

        private final String value;

        Rule(String value) {
            this.value = value;
        }

        public String value() {
            return this.value;
        }
    }

    public PriorAuthRule(String request) {
        // TODO: add a proper map from request to CQL
        logger.info("PriorAuthRule::Creating Rule:" + request);
        this.cql = getCQLFromFile(request + ".cql");
        Library library = createLibrary();
        this.context = new Context(library);
    }

    /**
     * Determine the disposition of the Claim by executing the bundle against the
     * CQL rule file
     * 
     * @param bundle - the Claim Bundle
     * @return the disposition of Granted, Pending, or Denied
     */
    public Disposition computeDisposition(Bundle bundle) {
        logger.info("PriorAuthRule::computeDisposition:Bundle/" + FhirUtils.getIdFromResource(bundle));
        this.context.registerDataProvider("http://hl7.org/fhir", createDataProvider(bundle));
        logger.info("PriorAuthRule::dataprovider is registered");
        if (this.executeRule(Rule.GRANTED))
            return Disposition.GRANTED;
        else if (this.executeRule(Rule.PENDED))
            return Disposition.PENDING;
        else
            return Disposition.DENIED;
    }

    /**
     * Execute the rule on a given bundle and determine the disposition
     * 
     * @param rule - the CQL expression to execute
     * @return true if the PriorAuth is granted, false otherwise
     */
    private boolean executeRule(Rule rule) {
        logger.info("PriorAuthRule::executing rule:" + rule.value());
        Object rawValue = this.context.resolveExpressionRef(rule.value()).evaluate(this.context);
        logger.info("PriorAuthRule::executeRule:" + rule.value() + ":" + rawValue.toString());
        boolean value = true;
        return value;
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
            logger.fine("PriorAuthRule::getCQLFromFile:Read CQL file:" + path);
        } catch (Exception e) {
            logger.warning("PriorAuthRule::getCQLFromFile:CQL File does not exist:" + path);
        }
        return cql;
    }

    /**
     * Helper method to create the Library for the constructor
     * 
     * @return Library or null
     */
    private Library createLibrary() {
        logger.info("PriorAuthRule::createLibrary");
        ModelManager modelManager = new ModelManager();
        LibraryManager libraryManager = new LibraryManager(modelManager);
        libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        logger.info("HERE");
        CqlTranslator translator = CqlTranslator.fromText(this.cql, modelManager, libraryManager);
        Library library = null;
        try {
            library = CqlLibraryReader.read(new StringReader(translator.toXml()));
        } catch (IOException | JAXBException e) {
            logger.log(Level.SEVERE, "PriorAuthRule::createLibrary:exception reading library", e);
        }
        logger.info("Created library");
        return library;
    }

    private DataProvider createDataProvider(Bundle bundle) {
        logger.info("PriorAuthRule::createDataProvider:Bundle/" + FhirUtils.getIdFromResource(bundle));
        R4FhirModelResolver modelResolver = new R4FhirModelResolver();
        BundleRetrieveProvider bundleRetrieveProvider = new BundleRetrieveProvider(FhirContext.forR4(), bundle);
        return new CompositeDataProvider(modelResolver, bundleRetrieveProvider);
    }

}