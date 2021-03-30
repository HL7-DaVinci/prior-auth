package org.hl7.davinci.priorauth;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import com.google.common.net.MediaType;

import org.apache.http.HttpEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.hl7.davinci.priorauth.Database.Table;
import org.hl7.davinci.priorauth.FhirUtils.Disposition;
import org.hl7.davinci.rules.PriorAuthRule;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.Claim.ClaimStatus;
import org.hl7.fhir.r4.model.Claim.ItemComponent;
import org.springframework.http.HttpStatus;
import org.springframework.http.RequestEntity;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ProcessClaimItemTask implements Runnable {

    static final Logger logger = PALogger.getLogger();

    private String id;
    private String status;
    private String relatedId;
    private Bundle bundle;
    private Thread thread;
    private ItemComponent item;

    /**
     * Thread Status codes
     * 
     * 0 - finished successfully 1 - finished with errors 2 - not yet started 3 -
     * running
     */
    private volatile int threadStatus = 2; // Not yet started

    public ProcessClaimItemTask(Bundle bundle, ItemComponent item, String id, String relatedId, String status) {
        this.id = id;
        this.item = item;
        this.bundle = bundle;
        this.status = status;
        this.thread = null;
        this.relatedId = relatedId;
    }

    public void run() {
        logger.info("ProcessClaimItemTask::run:ClaimItem " + this.getItemName());
        boolean ret = process();
        this.threadStatus = ret ? 0 : 1; // 0 for success, 1 for error
        logger.fine("ProcessClaimItemTask::run:Thread exiting for ClaimItem " + this.getItemName() + ":"
                + this.threadStatus);
    }

    public void start() {
        logger.fine("ProcessClaimItemTask::start:ClaimItem " + this.getItemName());
        if (this.thread == null) {
            this.thread = new Thread(this, this.getItemName());
            this.thread.start();
            this.threadStatus = 3; // Running
        }
    }

    /**
     * Get the status of the thread
     * 
     * @return status of the thread 0 - finished successfully 1 - finished with
     *         errors 2 - not yet started 3 - running
     */
    public int getStatus() {
        return this.threadStatus;
    }

    /**
     * Get the thread for this task
     * 
     * @return the thread for this task
     */
    public Thread getThread() {
        return this.thread;
    }

    /**
     * Helper method to get the claim id and sequence to identify this specific
     * claim item
     * 
     * @return string of claim.id/item.sequence
     */
    public String getItemName() {
        return this.id + "/" + this.item.getSequence();
    }

    /**
     * Process the claim item and compute a disposition
     * 
     * @return true if the claim item was processed successfully, false otherwise
     */
    private boolean process() {
        boolean ret = true;
        boolean itemIsCancelled = false;
        String rulesEngine = PropertyProvider.getProperty("rules_engine");
        if (this.item.hasModifierExtension()) {
            List<Extension> exts = this.item.getModifierExtension();
            for (Extension ext : exts) {
                if (ext.getUrl().equals(FhirUtils.ITEM_CANCELLED_EXTENSION_URL) && ext.hasValue()) {
                    Type type = ext.getValue();
                    itemIsCancelled = type.castToBoolean(type).booleanValue();
                }
            }
        }

        Disposition itemDisposition;
        if (!itemIsCancelled) {
            // here is the place to switch
            if (rulesEngine.equals("internal")) {
                itemDisposition = PriorAuthRule.computeDisposition(this.bundle, this.item.getSequence());
            } else {
                String URL = PropertyProvider.getProperty("url");
                try {
                    itemDisposition = sendAndGetDisposition(this.bundle, this.item.getSequence(), URL);
                } catch (IOException e) {
                    // if we fail to talk to the external rules engine just say we don't know the
                    // state of the claim
                    itemDisposition = Disposition.UNKNOWN;
                    e.printStackTrace();
                }

            }

        } else
            itemDisposition = Disposition.CANCELLED;

        Map<String, Object> dataMap = new HashMap<>();
        dataMap.put("id", id);
        dataMap.put("sequence", this.item.getSequence());
        dataMap.put("status", itemIsCancelled ? ClaimStatus.CANCELLED.getDisplay().toLowerCase() : this.status);
        dataMap.put("outcome", FhirUtils.dispositionToReviewAction(itemDisposition).value());
        if (this.relatedId != null) {
            // This is an update
            Map<String, Object> constraintMap = new HashMap<>();
            constraintMap.put("id", this.relatedId);
            constraintMap.put("sequence", this.item.getSequence());

            // Update if item exists
            if (App.getDB().readStatus(Table.CLAIM_ITEM, constraintMap) != null) {
                if (!App.getDB().update(Table.CLAIM_ITEM, constraintMap, dataMap)) {
                    logger.warning(
                            "ClaimEndpoint::processClaimItems:unable to update claim item:" + this.getItemName());
                    ret = false;
                }
                return ret;
            }
        }

        // Add new item to the database
        if (!App.getDB().write(Table.CLAIM_ITEM, dataMap)) {
            logger.warning("ClaimEndpoint::processClaimItems:unable to write claim item:" + this.getItemName());
            ret = false;
        }

        return ret;
    }

    private Disposition sendAndGetDisposition(Bundle bundle, int seq, String address) throws IOException {

        // create post request

        OkHttpClient client = new OkHttpClient();
        RequestBody body = RequestBody.create(null, bundle.toString());
        Request request = new Request.Builder().url(address).post(body).build();
        // if no response in alloted time return some default value
        System.out.println("I actualy managed to send  response");
        try (Response response = client.newCall(request).execute()) {
            String answer = response.body().string();
            return convertStringTDisposition(answer);
        } catch (Exception e) {
            return Disposition.CANCELLED;
        }
    }

    private Disposition convertStringTDisposition(String answer) {

        if (answer.equals("Unknown"))
            return FhirUtils.Disposition.UNKNOWN;
        if (answer.equals("Granted"))
            return FhirUtils.Disposition.GRANTED;
        if (answer.equals("Denied"))
            return FhirUtils.Disposition.DENIED;
        if (answer.equals("Pending"))
            return FhirUtils.Disposition.PENDING;
        if (answer.equals("Cancelled"))
            return FhirUtils.Disposition.CANCELLED;
        if (answer.equals("Partial"))
            return FhirUtils.Disposition.PARTIAL;
        else
            return null;

    }
}