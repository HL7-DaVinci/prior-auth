package org.hl7.davinci.priorauth;


import java.util.*;
import java.util.logging.Logger;

import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.fhir.dstu3.model.Subscription;
import org.hl7.fhir.instance.model.api.IBaseResource;
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
    public ResponseEntity<String> postExpunge() {
        return expungeDatabase();
    }

    /**
     * Delete all the data in the database. Useful if the demonstration database
     * becomes too large and unwieldy.
     *
     * @return - HTTP 200
     */
    private ResponseEntity<String> expungeDatabase() {
        logger.info("POST /$expunge");
        if (App.debugMode) {
            // Cascading delete of everything...
            App.getDB().delete(Table.SUBSCRIPTION);
            App.getDB().delete(Table.BUNDLE);
            App.getDB().delete(Table.CLAIM);
            App.getDB().delete(Table.CLAIM_ITEM);
            App.getDB().delete(Table.CLAIM_RESPONSE);
            return ResponseEntity.ok().body("Expunge success!");
        } else {
            logger.warning("ExpungeOperation::expungeDatabase:query enabled");
            return expungeAllowedEntries();
        }
    }

    private ResponseEntity<String> expungeAllowedEntries() {
        boolean results = false;
        results = expungeSubscriptions("active");
        results = expungeSubscriptions("off");
        results = expungeSubscriptions("error");
        if (results)
            return ResponseEntity.ok().body("Expunge success!");
        else
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Expunge operation disabled");
    }

    private boolean expungeSubscriptions(String status) {
        //if the time of the deletion is after the noted end date of the subscription it should be expunged
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
