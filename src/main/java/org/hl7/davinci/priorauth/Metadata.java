package org.hl7.davinci.priorauth;

import java.util.Calendar;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.AuditEvent.AuditEventAction;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementImplementationComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementKind;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestResourceComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementRestSecurityComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.CapabilityStatementSoftwareComponent;
import org.hl7.fhir.r4.model.CapabilityStatement.RestfulCapabilityMode;
import org.hl7.fhir.r4.model.CapabilityStatement.TypeRestfulInteraction;
import org.hl7.fhir.r4.model.Enumerations.FHIRVersion;
import org.hl7.fhir.r4.model.Enumerations.PublicationStatus;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The metadata microservice provides a CapabilityStatement.
 */
@CrossOrigin
@RestController
@RequestMapping("/metadata")
public class Metadata {

  /**
   * Cached CapabilityStatement.
   */
  private CapabilityStatement capabilityStatement = null;

  @GetMapping(value = "", produces = { MediaType.APPLICATION_JSON_VALUE, "application/fhir+json" })
  public ResponseEntity<String> getMetadata(HttpServletRequest request) {
    if (capabilityStatement == null)
      capabilityStatement = buildCapabilityStatement();

    String description = "Read metadata";
    new Audit(AuditEventType.QUERY, AuditEventAction.R, AuditEventOutcome.SUCCESS, "/metadata", request, description);
    String json = FhirUtils.json(capabilityStatement);
    return new ResponseEntity<String>(json, HttpStatus.OK);
  }

  @GetMapping(value = "", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> getMetadataXml(HttpServletRequest request) {
    if (capabilityStatement == null)
      capabilityStatement = buildCapabilityStatement();

    String description = "Read metadata";
    new Audit(AuditEventType.QUERY, AuditEventAction.R, AuditEventOutcome.SUCCESS, "/metadata", request, description);
    String xml = FhirUtils.xml(capabilityStatement);
    return new ResponseEntity<String>(xml, HttpStatus.OK);
  }

  /**
   * Builds the CapabilityStatement describing the Prior Authorization Reference
   * Implementation.
   * 
   * @return CapabilityStatement - the CapabilityStatement.
   */
  private CapabilityStatement buildCapabilityStatement() {
    CapabilityStatement metadata = new CapabilityStatement();
    metadata.setTitle("Da Vinci Prior Authorization Reference Implementation");
    metadata.setStatus(PublicationStatus.DRAFT);
    metadata.setExperimental(true);
    Calendar calendar = Calendar.getInstance();
    calendar.set(2019, 4, 28, 0, 0, 0);
    metadata.setDate(calendar.getTime());
    metadata.setPublisher("Da Vinci");
    metadata.setKind(CapabilityStatementKind.INSTANCE);
    CapabilityStatementSoftwareComponent software = new CapabilityStatementSoftwareComponent();
    software.setName("https://github.com/HL7-DaVinci/prior-auth");
    metadata.setSoftware(software);
    CapabilityStatementImplementationComponent implementation = new CapabilityStatementImplementationComponent();
    implementation.setDescription(metadata.getTitle());
    implementation.setUrl(App.getBaseUrl() + "metadata");
    metadata.setImplementation(implementation);
    metadata.setFhirVersion(FHIRVersion._4_0_0);
    metadata.addFormat("json");
    metadata.addFormat("xml");
    metadata.addExtension(FhirUtils.WEBSOCKET_EXTENSION_URL, new StringType("/fhir"));
    metadata.addImplementationGuide("https://build.fhir.org/ig/HL7/davinci-pas/index.html");
    metadata
        .addImplementationGuide("http://wiki.hl7.org/index.php?title=Da_Vinci_Prior_Authorization_FHIR_IG_Proposal");
    CapabilityStatementRestComponent rest = new CapabilityStatementRestComponent();
    rest.setMode(RestfulCapabilityMode.SERVER);
    CapabilityStatementRestSecurityComponent security = new CapabilityStatementRestSecurityComponent();
    security.setCors(true);
    rest.setSecurity(security);

    // Claim Resource
    CapabilityStatementRestResourceComponent claim = new CapabilityStatementRestResourceComponent();
    claim.setType("Claim");
    // TODO claim.setSupportedProfile(theSupportedProfile);
    claim.addInteraction().setCode(TypeRestfulInteraction.READ);
    claim.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
    claim.addInteraction().setCode(TypeRestfulInteraction.DELETE);
    claim.addOperation().setName("$submit").setDefinition("http://hl7.org/fhir/OperationDefinition/Claim-submit");
    claim.addOperation().setName("$submit")
        .setDefinition("https://build.fhir.org/ig/HL7/davinci-pas/Claim-submit.html");
    rest.addResource(claim);

    // ClaimResponse Resource
    CapabilityStatementRestResourceComponent claimResponse = new CapabilityStatementRestResourceComponent();
    claimResponse.setType("ClaimResponse");
    // TODO claimResponse.setSupportedProfile(theSupportedProfile);
    claimResponse.addInteraction().setCode(TypeRestfulInteraction.READ);
    claimResponse.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
    claimResponse.addInteraction().setCode(TypeRestfulInteraction.DELETE);
    rest.addResource(claimResponse);

    // Bundle Resource
    CapabilityStatementRestResourceComponent bundle = new CapabilityStatementRestResourceComponent();
    bundle.setType("Bundle");
    bundle.addInteraction().setCode(TypeRestfulInteraction.READ);
    bundle.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
    bundle.addInteraction().setCode(TypeRestfulInteraction.DELETE);
    rest.addResource(bundle);

    rest.addOperation().setName("$expunge")
        .setDefinition("https://smilecdr.com/docs/current/fhir_repository/deleting_data.html#drop-all-data")
        .setDocumentation(
            "For Demonstration Purposes Only. Deletes all data from the demonstration database. Not part of the Implementation Guide.");

    metadata.addRest(rest);

    return metadata;
  }
}
