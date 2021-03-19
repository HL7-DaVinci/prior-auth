package org.hl7.davinci.priorauth;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.authorization.AuthUtils;
import org.hl7.davinci.priorauth.Database.Table;
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
        AUDIT("110101", "Audit Log Used"), REST("rest", "RESTful Operation"),
        ACTIVITY("110100", "Application Activity"), QUERY("110112", "Query");

        private final String code;
        private final String display;
        private static final String SYSTEM = "http://dicom.nema.org/resources/ontology/DCM";

        AuditEventType(String code, String display) {
            this.code = code;
            this.display = display;
        }

        public Coding toCoding() {
            return new Coding(SYSTEM, this.code, this.display);
        }

        public String toCode() {
            return this.code;
        }
    }

    public enum AuditEventOutcome {
        SUCCESS("0"), MINOR_FAILURE("4"), SERIOUS_FAILURE("8"), MAJOR_FAILURE("12");

        private final String code;

        AuditEventOutcome(String code) {
            this.code = code;
        }

        public AuditEvent.AuditEventOutcome getOutcome() {
            return AuditEvent.AuditEventOutcome.fromCode(this.code);
        }

        public String toCode() {
            return this.code;
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
        if (request == null)
            return null;
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

    private static AuditEventEntityComponent createEntityComponent(Reference what, String query, String description) {
        AuditEventEntityComponent entity = new AuditEventEntityComponent();
        entity.setDescription(description);
        if (query != null)
            entity.setQuery(query.getBytes());
        if (what != null)
            entity.setWhat(what);
        return entity;
    }

    private static AuditEventAgentComponent createAgentComponent(HttpServletRequest request) {
        String ip = getIPAddress(request);
        AuditEventAgentComponent agent = new AuditEventAgentComponent(new BooleanType(request != null));
        if (ip != null) {
            AuditEventAgentNetworkComponent network = new AuditEventAgentNetworkComponent();
            network.setAddress(ip);
            network.setType(AuditEventAgentNetworkType.fromCode("2"));
            agent.setNetwork(network);
        }

        if (request != null) {
            String clientId = AuthUtils.getClientId(request);
            if (!clientId.contains("Unknown"))
                agent.setWho(new Reference(App.getBaseUrl() + "/Organization/" + clientId));
            agent.setAltId(clientId);
        } else
            agent.setName("MITRE PAS Reference Implementation");

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
    private static AuditEvent auditEventFactory(AuditEventType eventType, AuditEventAction eventAction,
            AuditEventOutcome outcome, HttpServletRequest request, Reference what, String query, String description) {
        Coding type = eventType.toCoding();
        InstantType recorded = new InstantType(new Date());
        AuditEventSourceComponent source = createSourceComponent();
        AuditEventEntityComponent entity = createEntityComponent(what, query, description);
        AuditEventAgentComponent agent = createAgentComponent(request);

        AuditEvent auditEvent = new AuditEvent(type, recorded, source);
        auditEvent.addAgent(agent);
        auditEvent.addEntity(entity);
        auditEvent.setAction(eventAction);
        auditEvent.setOutcome(outcome.getOutcome());
        auditEvent.setId(UUID.randomUUID().toString());

        return auditEvent;
    }

    public static void createAuditEvent(AuditEventType eventType, AuditEventAction eventAction, AuditEventOutcome outcome, String referenceUrl,
            HttpServletRequest request, String description) {
        String query = request != null ? request.getRequestURL().toString() : null;
        AuditEvent audit = auditEventFactory(eventType, eventAction, outcome, request, new Reference(referenceUrl), query, 
                description);
        Map<String, Object> data = new HashMap<>();
        data.put("id", audit.getId());
        data.put("type", eventType.toCode());
        data.put("action", eventAction.getDisplay());
        data.put("outcome", outcome.toCode());
        data.put("what", referenceUrl);
        data.put("query", query);
        data.put("ip", getIPAddress(request));
        data.put("resource", FhirUtils.json(audit));
        App.getDB().write(Table.AUDIT, data);
    }
}
