package org.hl7.davinci.rules;

import java.util.ArrayList;
import java.util.logging.Logger;

import org.cqframework.cql.cql2elm.CqlTranslator;
import org.cqframework.cql.cql2elm.CqlTranslatorException;
import org.cqframework.cql.cql2elm.FhirLibrarySourceProvider;
import org.cqframework.cql.cql2elm.LibraryManager;
import org.cqframework.cql.cql2elm.ModelManager;
import org.cqframework.cql.elm.tracking.TrackBack;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.Endpoint.RequestType;

public class PreconvertCql {

    private static final Logger logger = PALogger.getLogger();

    /**
     * Convert CQL string into ELM string
     * 
     * @param cql         - the cql to string to translate
     * @param requestType - the format of the ELM (json or xml)
     * @return string in json or xml for the elm
     */
    public static String convert(String cql, RequestType requestType) {
        logger.info("Converting CQL to ELM");
        ModelManager modelManager = new ModelManager();
        LibraryManager libraryManager = new LibraryManager(modelManager);

        libraryManager.getLibrarySourceLoader().registerProvider(new FhirLibrarySourceProvider());
        CqlTranslator translator = CqlTranslator.fromText(cql, modelManager, libraryManager);
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

        return requestType == RequestType.JSON ? translator.toJson() : translator.toXml();
    }

}