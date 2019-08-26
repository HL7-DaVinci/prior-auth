package org.hl7.davinci.priorauth.fhirresources;

import ca.uhn.fhir.model.api.annotation.Child;
import ca.uhn.fhir.model.api.annotation.Description;
import ca.uhn.fhir.model.api.annotation.Extension;
import ca.uhn.fhir.model.api.annotation.ResourceDef;
import org.hl7.fhir.r4.model.Claim;

@ResourceDef(name = "Claim", profile = "https://build.fhir.org/ig/HL7/davinci-pas/profile-claim-update.html")
public class PriorauthClaim extends Claim {

    // private static final long serialVersionUID = 1L;
    @Child(name = "ItemCancelledFlag")
    @Extension(url = "http://hl7.org/fhir/us/davinci-pas/StructureDefinition/extension-itemCancelled", definedLocally = false, isModifier = false)
    @Description(shortDefinition = "Whether the item has been cancelled or not.")
    private boolean ItemCancelledFlag;

    /**
     * Gets the ItemCancelledFlag
     * 
     * @return the ItemCancelledFlag
     */
    public boolean getItemCancelledFlag() {
        return ItemCancelledFlag;
    }

    public void setItemCancelledFlag(boolean flag) {
        this.ItemCancelledFlag = flag;
    }

}