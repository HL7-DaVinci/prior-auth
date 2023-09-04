package org.hl7.davinci.rules;

import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.StringType;

// TODO, The TraceNumber should be an Identifier type, but this results in a conflict
// Exception on ItemRequiresFollowup :Conflicting setter definitions for property "referenceElement": org.hl7.fhir.r4.model.Reference#setReferenceElement(org.hl7.fhir.instance.model.api.IIdType) vs org.hl7.fhir.r4.model.Reference#setReferenceElement(org.hl7.fhir.r4.model.StringType)
// To fix this, Identifier probably would need to be extended and override the setter method and annotate it with @JsonIgnore or @com.fasterxml.jackson.annotation.JsonSetter
// See https://stackoverflow.com/questions/37452689/how-to-overcome-conflicting-setter-definitions-for-property

public class RequestMapping {
    private Coding productOrService;
    private Coding contentModifier;
    private Coding traceNumber;
    private StringType questionnaireURL;

    public Coding getProductOrService() { return productOrService; }
    public void setProductOrService(Coding value) { this.productOrService = value; }

    public Coding getContentModifier() { return contentModifier; }
    public void setContentModifier(Coding value) { this.contentModifier = value; }

    public Coding getTraceNumber() { return traceNumber; }
    public void setTraceNumber(Coding value) { this.traceNumber = value; }

    public StringType getQuestionnaireURL() { return questionnaireURL; }
    public void setQuestionnaireURL(StringType value) { this.questionnaireURL = value; }
}
