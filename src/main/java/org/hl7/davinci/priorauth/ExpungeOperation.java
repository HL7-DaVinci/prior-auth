package org.hl7.davinci.priorauth;

import java.util.*;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.fhir.dstu3.model.Subscription;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/$expunge")
public class ExpungeOperation {

    static final Logger logger = PALogger.getLogger();

    @PostMapping("")
    public ResponseEntity<String> postExpunge(HttpServletRequest request) {
        return expungeDatabase(request);
    }

    /**
     * Delete all the data in the database. Useful if the demonstration database
     * becomes too large and unwieldy.
     *
     * @return - HTTP 200
     */
    private ResponseEntity<String> expungeDatabase(HttpServletRequest request) {
        logger.info("POST /$expunge");
        if (App.isDebugModeEnabled()) {
            // Cascading delete of everything...
            App.getDB().delete(Table.SUBSCRIPTION);
            App.getDB().delete(Table.BUNDLE);
            App.getDB().delete(Table.CLAIM);
            App.getDB().delete(Table.CLAIM_ITEM);
            App.getDB().delete(Table.CLAIM_RESPONSE);

            Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.D, AuditEventOutcome.SUCCESS, null, request,
                    "$expunge everything (debug mode)");
            return ResponseEntity.ok().body("Expunge success!");
        } else {
            logger.warning("ExpungeOperation::expungeDatabase:query enabled");
            return expungeAllowedEntries(request);
        }
    }

    private ResponseEntity<String> expungeAllowedEntries(HttpServletRequest request) {
        boolean results = false;
        results = expungeSubscriptions("active");
        results = expungeSubscriptions("off");
        results = expungeSubscriptions("error");
        if (results) {
            Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.D, AuditEventOutcome.SUCCESS, null, request,
                    "Expunge only allowed entries");
            return ResponseEntity.ok().body("Expunge success!");
        } else {
            Audit.createAuditEvent(AuditEventType.REST, AuditEventAction.D, AuditEventOutcome.MINOR_FAILURE, null, request,
                    "Attempted to expunge only allowed entries but something went wrong");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Expunge operation failed");
        }
    }

    private boolean expungeSubscriptions(String status) {
        // if the time of the deletion is after the noted end date of the subscription
        // it should be expunged
        Map<String, Object> constraintMap = new Map<String, Object>() {
            @Override
            public int size() {
                return 0;
            }

            @Override
            public boolean isEmpty() {
                return false;
            }

            @Override
            public boolean containsKey(Object key) {
                return false;
            }

            @Override
            public boolean containsValue(Object value) {
                return false;
            }

            @Override
            public Object get(Object key) {
                return null;
            }

            @Override
            public Object put(String key, Object value) {
                return null;
            }

            @Override
            public Object remove(Object key) {
                return null;
            }

            @Override
            public void putAll(Map<? extends String, ?> m) {

            }

            @Override
            public void clear() {

            }

            @Override
            public Set<String> keySet() {
                return null;
            }

            @Override
            public Collection<Object> values() {
                return null;
            }

            @Override
            public Set<Map.Entry<String, Object>> entrySet() {
                return null;
            }
        };
        constraintMap.put("status", status);
        Date today = new Date();
        Subscription sub;
        List<IBaseResource> subscriptions = App.getDB().readAll(Table.SUBSCRIPTION, constraintMap);
        if (subscriptions != null) {

            while (subscriptions.iterator().hasNext()) {

                sub = (Subscription) subscriptions.iterator().next();
                if ((today.after(sub.getEnd())) || (status.equals("error")) || (status.equals("off"))) {
                    String id = FhirUtils.getIdFromResource(sub);
                    App.getDB().delete(Table.SUBSCRIPTION, id);
                }

            }

            return true;
        }
        return false;
    }

}
