package org.hl7.davinci.rules;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.bind.JAXBException;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.opencds.cqf.cql.data.DataProvider;
import org.opencds.cqf.cql.data.CompositeDataProvider;

import org.opencds.cqf.cql.execution.Context;
import org.opencds.cqf.cql.execution.CqlLibraryReader;
import org.cqframework.cql.elm.execution.ExpressionDef;
import org.cqframework.cql.elm.execution.Library;
import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.PropertyProvider;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Claim;
import org.hl7.fhir.r4.model.Claim.ItemComponent;

/**
 * The main class for executing priorauthorization rules
 */
public class PriorAuthRule {

    private static final Logger logger = PALogger.getLogger();

    private static final Map<String, String> CODE_SYSTEM_SHORT_NAME_TO_FULL_NAME;
    static {
        Map<String, String> tempMap = new HashMap<String, String>();
        tempMap.put("cpt", "http://www.ama-assn.org/go/cpt");
        tempMap.put("hcpcs", "https://bluebutton.cms.gov/resources/codesystem/hcpcs");
        tempMap.put("rxnorm", "http://www.nlm.nih.gov/research/umls/rxnorm");
        tempMap.put("sct", "http://snomed.info/sct");
        CODE_SYSTEM_SHORT_NAME_TO_FULL_NAME = Collections.unmodifiableMap(tempMap);
    }

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

    /**
     * Determine the disposition of the Claim by executing the bundle against the
     * CQL rule file
     * 
     * @param bundle   - the Claim Bundle
     * @param sequence - the sequence ID of the claim item to compute the
     *                 disposition of
     * @return the disposition of Granted, Pending, or Denied
     */
    public static Disposition computeDisposition(Bundle bundle, int sequence) {
        logger.info("PriorAuthRule::computeDisposition:Bundle/" + FhirUtils.getIdFromResource(bundle));

        Claim claim = FhirUtils.getClaimFromRequestBundle(bundle);
        ItemComponent claimItem = claim.getItem().stream().filter(item -> item.getSequence() == sequence).findFirst()
                .get();
        String elmFile = getRuleFileFromItem(claimItem);
        String elm = getFileContent(elmFile);
        Library library = createLibrary(elm);
        Context context = new Context(library);
        context.registerDataProvider("http://hl7.org/fhir", createDataProvider(bundle));

        Disposition disposition;
        if (executeRule(context, Rule.GRANTED))
            disposition = Disposition.GRANTED;
        else if (executeRule(context, Rule.PENDED))
            disposition = Disposition.PENDING;
        else
            disposition = Disposition.DENIED;

        logger.info("PriorAuthRule::computeDisposition:" + disposition.value());

        return disposition;
    }

    /**
     * Use the CDS Library Metadata to populate the table
     * 
     * @return true if all of the mappings were written successfully, false
     *         otherwise
     */
    public static boolean populateRulesTable() {
        String cdsLibraryPath = PropertyProvider.getProperty("CDS_library");
        File filePath = new File(cdsLibraryPath);

        File[] topics = filePath.listFiles();
        for (File topic : topics) {
            if (topic.isDirectory()) {
                String topicName = topic.getName();

                // Ignore shared folder and hidden folder
                if (!topicName.startsWith(".") && !topicName.equalsIgnoreCase("Shared")) {
                    logger.fine("PriorAuthRule::populateRulesTable:Found topic " + topicName);

                    // Get the metadata file
                    for (File file : topic.listFiles()) {
                        // Consume the metadata file and upload to db
                        if (file.getName().equalsIgnoreCase("TopicMetadata.json")) {
                            try {
                                // Read the file
                                String content = new String(Files.readAllBytes(file.toPath()));

                                // Convert to object
                                ObjectMapper objectMapper = new ObjectMapper();
                                TopicMetadata metadata = objectMapper.readValue(content, TopicMetadata.class);

                                // Add each system/code pait to the database
                                for (Mapping mapping : metadata.getMappings()) {
                                    for (String code : mapping.getCodes()) {
                                        String elmFileName = metadata.getTopic() + "PriorAuthRule.elm.xml";
                                        Map<String, Object> dataMap = new HashMap<String, Object>();
                                        dataMap.put("system",
                                                CODE_SYSTEM_SHORT_NAME_TO_FULL_NAME.get(mapping.getCodeSystem()));
                                        dataMap.put("code", code);
                                        dataMap.put("topic", topicName);
                                        dataMap.put("rule", elmFileName);
                                        if (!App.getDB().write(Table.RULES, dataMap))
                                            return false;
                                    }
                                }
                            } catch (Exception e) {
                                logger.log(Level.SEVERE, "PriorAuthRule::populateRulesTable", e);
                                return false;
                            }
                        }
                    }
                }
            }
        }

        return true;
    }

    /**
     * Execute the rule on a given bundle and determine the disposition
     * 
     * @param rule - the CQL expression to execute
     * @return true if the PriorAuth is granted, false otherwise
     */
    private static boolean executeRule(Context context, Rule rule) {
        logger.info("PriorAuthRule::executing rule:" + rule.value());
        ExpressionDef expression = context.resolveExpressionRef(rule.value());
        Object rawValue = null;
        synchronized (ExpressionDef.class) {
            rawValue = expression.evaluate(context);
            logger.fine("PriorAuthRule::executeRule:" + rule.value() + ":" + rawValue.toString());
        }

        try {
            return (boolean) rawValue;
        } catch (Exception e) {
            logger.log(Level.SEVERE,
                    "Rule " + rule.value() + " did not return a boolean (Returned value: " + rawValue.toString() + ")",
                    e);
            return false;
        }
    }

    /**
     * Get the Rule rule file name based on the requested item
     * 
     * @param claimItem - the item requested
     * @return name of the rule file
     */
    private static String getRuleFileFromItem(ItemComponent claimItem) {
        Map<String, Object> constraintParams = new HashMap<String, Object>();
        constraintParams.put("code", FhirUtils.getCode(claimItem.getProductOrService()));
        constraintParams.put("system", FhirUtils.getSystem(claimItem.getProductOrService()));
        String topic = App.getDB().readString(Table.RULES, constraintParams, "topic");
        String rule = App.getDB().readString(Table.RULES, constraintParams, "rule");
        return topic + "/" + rule;
    }

    /**
     * Read in the contents file and return the contents
     * 
     * @param fileName - the name of the file
     * @return string contents of the file or null if the file does not exist
     */
    private static String getFileContent(String fileName) {
        String content = null;
        String path = PropertyProvider.getProperty("CDS_library") + fileName;
        try {
            content = new String(Files.readAllBytes(Paths.get(path)));
            logger.fine("PriorAuthRule::getFileContent:Read file:" + path);
        } catch (Exception e) {
            logger.warning("PriorAuthRule::getFileContent:File does not exist:" + path);
        }
        return content;
    }

    /**
     * Helper method to create the Library for the constructor
     * 
     * @param elm - the elm to create the library from
     * @return Library or null
     */
    private static Library createLibrary(String elm) {
        logger.fine("PriorAuthRule::createLibrary");
        Library library = null;
        try {
            synchronized (CqlLibraryReader.class) {
                library = CqlLibraryReader.read(new StringReader(elm));
            }
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
    private static DataProvider createDataProvider(Bundle bundle) {
        logger.info("PriorAuthRule::createDataProvider:Bundle/" + FhirUtils.getIdFromResource(bundle));
        BundleRetrieveProvider bundleRetrieveProvider = new BundleRetrieveProvider(App.getFhirContext(), bundle);
        return new CompositeDataProvider(App.getModelResolver(), bundleRetrieveProvider);
    }

}