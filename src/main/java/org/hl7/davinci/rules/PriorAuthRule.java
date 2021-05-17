package org.hl7.davinci.rules;

import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.opencds.cqf.cql.execution.Context;
import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.PALogger;
import org.hl7.davinci.priorauth.PropertyProvider;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.davinci.ruleutils.CqlUtils;
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
        logger.info("PriorAuthRule::computeDisposition:Bundle/" + FhirUtils.getIdFromResource(bundle) + "/" + sequence);

        Claim claim = FhirUtils.getClaimFromRequestBundle(bundle);
        ItemComponent claimItem = claim.getItem().stream().filter(item -> item.getSequence() == sequence).findFirst()
                .get();
        String elmFile = getRuleFileFromItem(claimItem);
        Disposition disposition;
        if (elmFile == null) {
            logger.warning("PriorAuthRule::getRuleFileFromItem:Code does not exist in rules table");
            disposition = Disposition.PENDING;
        } else {
            String elm = CqlUtils.readFile(elmFile);
            Context context = CqlUtils.createBundleContextFromElm(elm, bundle, App.getFhirContext(),
                    App.getModelResolver());

            if (executeRule(context, Rule.GRANTED))
                disposition = Disposition.GRANTED;
            else if (executeRule(context, Rule.PENDED))
                disposition = Disposition.PENDING;
            else
                disposition = Disposition.DENIED;
        }

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
        Object rawValue = CqlUtils.executeExpression(context, rule.value());

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
        if (topic == null || rule == null)
            return null;
        return PropertyProvider.getProperty("CDS_library") + topic + "/" + rule;
    }

}