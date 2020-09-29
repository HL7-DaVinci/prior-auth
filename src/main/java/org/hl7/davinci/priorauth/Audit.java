package org.hl7.davinci.priorauth;

import java.util.Date;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.hl7.fhir.r4.model.AuditEvent;
import org.hl7.fhir.r4.model.BooleanType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.InstantType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAgentComponent;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAgentNetworkComponent;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAgentNetworkType;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventEntityComponent;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventSourceComponent;

public class Audit {
    // FHIR: records as much detail as reasonable at the time the event happened.
    // HRex: SHALL log all IDs, access rights, requests, and exchanges.
    // PAS: The system will rely on audit and regulatory/payer consequences to
    // ensure that prior authorizations are not accessed without a legitimate
    // business requirement. This approach is used because there is no reasonable
    // way for a payer to know ‘a priori’ whether a given provider has a legitimate
    // need to know tha prior authorization status or for the patient to be involved
    // in verifying their need to know.

    // TODO: add this resource to the server
    private static final Reference observer = new Reference(App.getBaseUrl() + "/Organization/PASRI");
    private static final Coding sourceType = new Coding("http://terminology.hl7.org/CodeSystem/security-source-type",
            "4", "Application Server");

    public enum AuditEventType {
        AUDIT("110101", "Audit Log Used"), QUERY("110112", "Query");

        private final String code;
        private final String display;
        private final String SYSTEM = "http://dicom.nema.org/resources/ontology/DCM";

        AuditEventType(String code, String display) {
            this.code = code;
            this.display = display;
        }

        public Coding toCoding() {
            return new Coding(SYSTEM, this.code, this.display);
        }
    }

    /**
     * Get the client IP Address via the XFF header or Servlet Remote Addr. Note
     * this is succeptible to IP spoofing but is the only way to get the IP.
     * 
     * @param request - the servlet request
     * @return the IP Address
     */
    private static String getIPAddress(HttpServletRequest request) {
        String xffHeader = request.getHeader("X-Forwarded-For");
        if (xffHeader != null)
            return xffHeader;
        return request.getRemoteAddr();
    }

    private static AuditEventSourceComponent createSourceComponent() {
        AuditEventSourceComponent source = new AuditEventSourceComponent(observer);
        source.setSite("MITRE PAS Reference Implementation");
        source.addType(sourceType);
        return source;
    }

    private static AuditEventEntityComponent createEntityComponent(Reference what, String query) {
        AuditEventEntityComponent entity = new AuditEventEntityComponent();
        entity.setWhat(what);
        entity.setQuery(query.getBytes());
        return entity;
    }

    private static AuditEventAgentComponent createAgentComponent(String ip) {
        AuditEventAgentNetworkComponent network = new AuditEventAgentNetworkComponent();
        network.setAddress(ip);
        network.setType(AuditEventAgentNetworkType.fromCode("2"));
        AuditEventAgentComponent agent = new AuditEventAgentComponent(new BooleanType(true));
        agent.setNetwork(network);
        return agent;
    }

    /**
     * Create an AuditEvent for an action
     * 
     * @param eventType   - the type of event
     * @param eventAction - the kind of CRUDE action
     * @param what        - reference to the resource accessed
     * @param request     - the servlet request
     * @return an AuditEvent resource for the action
     */
    public static AuditEvent AuditEventFactory(AuditEventType eventType, AuditEventAction eventAction, Reference what,
            HttpServletRequest request) {
        Coding type = eventType.toCoding();
        InstantType recorded = new InstantType(new Date());
        AuditEventSourceComponent source = createSourceComponent();
        AuditEventEntityComponent entity = createEntityComponent(what, request.getRequestURL().toString());
        AuditEventAgentComponent agent = createAgentComponent(getIPAddress(request));

        // TODO: how to verify agent in PAS RI since IP can be spoofed
        AuditEvent auditEvent = new AuditEvent(type, recorded, source);
        auditEvent.addAgent(agent);
        auditEvent.addEntity(entity);
        auditEvent.setId(UUID.randomUUID().toString());

        return auditEvent;
    }
}
