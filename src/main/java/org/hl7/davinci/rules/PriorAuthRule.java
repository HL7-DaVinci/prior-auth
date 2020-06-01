package org.hl7.davinci.rules;

import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
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
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.FhirLibrarySourceProvider;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.execution.Library;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.fhir.ucum.UcumEssenceService;
import org.fhir.ucum.UcumException;
import org.fhir.ucum.UcumService;
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
        Library library = null;
        try {
            library = createLibrary();
        } catch (UcumException e) {
            logger.log(Level.SEVERE, "PriorAuthRule::Unable to create library", e);
        }
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
        logger.fine("PriorAuthRule::executeRule:" + rule.value() + ":" + rawValue.toString());
        try {
            return (boolean) rawValue;
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Rule " + rule.value() + " did not return a boolean", e);
            return false;
        }
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
     * @throws UcumException
     */
    private Library createLibrary() throws UcumException {
        logger.fine("PriorAuthRule::createLibrary");
        ModelManager modelManager = new ModelManager();
        LibraryManager libraryManager = new LibraryManager(modelManager);
        libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        CqlTranslator translator = CqlTranslator.fromText(this.cql, modelManager, libraryManager);
        if (translator.getErrors().size() > 0) {
            ArrayList<String> errors = new ArrayList<>();
            for (CqlTranslatorException error : translator.getErrors()) {
                TrackBack tb = error.getLocator();
                String lines = tb == null ? "[n/a]"
                        : String.format("[%d:%d, %d:%d]", tb.getStartLine(), tb.getStartChar(), tb.getEndLine(),
                                tb.getEndChar());
                errors.add(lines + error.getMessage());
            }
            throw new IllegalArgumentException(errors.toString());
        }

        Library library = null;
        try {
            library = CqlLibraryReader.read(new StringReader(translator.toXml()));
        } catch (IOException | JAXBException e) {
            logger.log(Level.SEVERE, "PriorAuthRule::createLibrary:exception reading library", e);
        }
        return library;
    }

    /**
     * Create a DataProvider from the request Bundle to execute the CQL against
     * 
     * @param bundle - the request bundle for the CQL
     * @return a FHIR DataProvider
     */
    private DataProvider createDataProvider(Bundle bundle) {
        logger.info("PriorAuthRule::createDataProvider:Bundle/" + FhirUtils.getIdFromResource(bundle));
        R4FhirModelResolver modelResolver = new R4FhirModelResolver();
        BundleRetrieveProvider bundleRetrieveProvider = new BundleRetrieveProvider(FhirContext.forR4(), bundle);
        return new CompositeDataProvider(modelResolver, bundleRetrieveProvider);
    }

}