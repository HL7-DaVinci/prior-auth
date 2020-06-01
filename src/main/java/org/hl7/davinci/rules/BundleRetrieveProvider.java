package org.hl7.davinci.rules;

import java.util.List;
import java.util.stream.Collectors;

import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.opencds.cqf.cql.retrieve.RetrieveProvider;
import org.opencds.cqf.cql.runtime.Code;
import org.opencds.cqf.cql.runtime.Interval;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.util.BundleUtil;

public class BundleRetrieveProvider implements RetrieveProvider {

    private IBaseBundle bundle;
    private FhirContext fhirContext;

    public BundleRetrieveProvider(FhirContext fhirContext, IBaseBundle bundle) {
        this.fhirContext = fhirContext;
        this.bundle = bundle;
    }

    @Override
    public Iterable<Object> retrieve(String context, String contextPath, Object contextValue, String dataType,
            String templateId, String codePath, Iterable<Code> codes, String valueSet, String datePath,
            String dateLowPath, String dateHighPath, Interval dateRange) {
        List<? extends IBaseResource> resources = BundleUtil.toListOfResourcesOfType(fhirContext, this.bundle,
                fhirContext.getResourceDefinition(dataType).getImplementingClass());

        return resources.stream().map(x -> (Object) x).collect(Collectors.toList());
    }

}