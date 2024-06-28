package org.hl7.davinci.priorauth.endpoint;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import javax.servlet.http.HttpServletRequest;

import org.hl7.davinci.priorauth.App;
import org.hl7.davinci.priorauth.Audit;
import org.hl7.davinci.priorauth.FhirUtils;
import org.hl7.davinci.priorauth.Audit.AuditEventOutcome;
import org.hl7.davinci.priorauth.Audit.AuditEventType;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.CapabilityStatement;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.UriType;
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
      capabilityStatement = buildCapabilityStatement(request);

    String description = "Read metadata";
    Audit.createAuditEvent(AuditEventType.QUERY, AuditEventAction.R, AuditEventOutcome.SUCCESS, "/metadata", request,
        description);
    String json = FhirUtils.json(capabilityStatement);
    return new ResponseEntity<>(json, HttpStatus.OK);
  }

  @GetMapping(value = "", produces = { MediaType.APPLICATION_XML_VALUE, "application/fhir+xml" })
  public ResponseEntity<String> getMetadataXml(HttpServletRequest request) {
    if (capabilityStatement == null)
      capabilityStatement = buildCapabilityStatement(request);

    String description = "Read metadata";
    Audit.createAuditEvent(AuditEventType.QUERY, AuditEventAction.R, AuditEventOutcome.SUCCESS, "/metadata", request,
        description);
    String xml = FhirUtils.xml(capabilityStatement);
    return new ResponseEntity<>(xml, HttpStatus.OK);
  }

  /**
   * Builds the CapabilityStatement describing the Prior Authorization Reference
   * Implementation.
   * 
   * @return CapabilityStatement - the CapabilityStatement.
   */
  private CapabilityStatement buildCapabilityStatement(HttpServletRequest request) {
    CapabilityStatement metadata = new CapabilityStatement();

    // title
    metadata.setTitle("Da Vinci Prior Authorization Reference Implementation");

    // status
    metadata.setStatus(PublicationStatus.DRAFT);

    // experimental
    metadata.setExperimental(true);

    // date
    Calendar calendar = Calendar.getInstance();
    calendar.set(2019, 4, 28, 0, 0, 0);
    Date date = calendar.getTime();
    metadata.setDate(date);

    // publisher
    metadata.setPublisher("Da Vinci");

    // kind
    metadata.setKind(CapabilityStatementKind.INSTANCE);

    // software
    CapabilityStatementSoftwareComponent software = new CapabilityStatementSoftwareComponent();
    software.setName("https://github.com/HL7-DaVinci/prior-auth");
    metadata.setSoftware(software);

    // implementation
    CapabilityStatementImplementationComponent implementation = new CapabilityStatementImplementationComponent();
    implementation.setDescription(metadata.getTitle());
    implementation.setUrl(App.getBaseUrl() + "metadata");
    metadata.setImplementation(implementation);

    // version
    metadata.setFhirVersion(FHIRVersion._4_0_1);

    // format
    metadata.addFormat("json");
    metadata.addFormat("xml");

    // implementationGuide
    metadata.addImplementationGuide("https://build.fhir.org/ig/HL7/davinci-pas/index.html");
    metadata
        .addImplementationGuide("http://wiki.hl7.org/index.php?title=Da_Vinci_Prior_Authorization_FHIR_IG_Proposal");

    // rest
    CapabilityStatementRestComponent rest = getRest(request);
    metadata.addRest(rest);


    return metadata;
  }

  private CapabilityStatementRestComponent getRest(HttpServletRequest request) {
    CapabilityStatementRestComponent rest = new CapabilityStatementRestComponent();

    // extension:capabilityStatement-websocket
    Extension websocket = new Extension(FhirUtils.WEBSOCKET_EXTENSION_URL);
    websocket.setValue(new UriType("/fhir"));
    rest.addExtension(websocket);

    // mode
    rest.setMode(RestfulCapabilityMode.SERVER);

    // security
    CapabilityStatementRestSecurityComponent security = getSecurity(request);
    rest.setSecurity(security);

    // resource
    CapabilityStatementRestResourceComponent claim = getClaim();
    CapabilityStatementRestResourceComponent claimResponse = getClaimResponse();
    CapabilityStatementRestResourceComponent bundle = getBundle();
    CapabilityStatementRestResourceComponent subscription = getSubscriptionResponse();
    rest.addResource(claim);
    rest.addResource(claimResponse);
    rest.addResource(bundle);
    rest.addResource(subscription);

    // operation
    rest.addOperation().setName("$expunge")
        .setDefinition("https://smilecdr.com/docs/current/fhir_repository/deleting_data.html#drop-all-data")
        .setDocumentation(
            "For Demonstration Purposes Only. Deletes all data from the demonstration database. Not part of the Implementation Guide.");

    return rest;
  }

  private CapabilityStatementRestSecurityComponent getSecurity(HttpServletRequest request) {
    CapabilityStatementRestSecurityComponent security = new CapabilityStatementRestSecurityComponent();
    security.setCors(true);
    Extension oauthUris = new Extension("http://fhir-registry.smarthealthit.org/StructureDefinition/oauth-uris");
    String uriBase = request.getScheme() + "://" + request.getServerName()
                    + ("http".equals(request.getScheme()) && request.getServerPort() == 80
                    || "https".equals(request.getScheme()) && request.getServerPort() == 443 ? ""
                    : ":" + request.getServerPort());

    if (System.getenv("TOKEN_BASE_URI") != null && !System.getenv("TOKEN_BASE_URI").isBlank()) {
      uriBase = System.getenv("TOKEN_BASE_URI");
    }
    Extension tokenUri = new Extension("token", new UriType(uriBase + "/fhir/auth/token"));
    Extension authorizeUri = new Extension("authorize", new UriType(uriBase + "/fhir/auth/authorize"));
    oauthUris.addExtension(tokenUri);
    oauthUris.addExtension(authorizeUri);
    security.addExtension(oauthUris);
    return security;
  }

  private CapabilityStatementRestResourceComponent getBundle() {
    CapabilityStatementRestResourceComponent bundle = new CapabilityStatementRestResourceComponent();
    bundle.setType("Bundle");
    bundle.addInteraction().setCode(TypeRestfulInteraction.READ);
    bundle.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
    bundle.addInteraction().setCode(TypeRestfulInteraction.DELETE);
    return bundle;
  }

  private CapabilityStatementRestResourceComponent getClaimResponse() {
    CapabilityStatementRestResourceComponent claimResponse = new CapabilityStatementRestResourceComponent();
    claimResponse.setType("ClaimResponse");
    // TODO claimResponse.setSupportedProfile(theSupportedProfile);
    claimResponse.addInteraction().setCode(TypeRestfulInteraction.READ);
    claimResponse.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
    claimResponse.addInteraction().setCode(TypeRestfulInteraction.DELETE);
    return claimResponse;
  }

  private CapabilityStatementRestResourceComponent getClaim() {
    CapabilityStatementRestResourceComponent claim = new CapabilityStatementRestResourceComponent();
    claim.setType("Claim");
    // TODO claim.setSupportedProfile(theSupportedProfile);
    claim.addInteraction().setCode(TypeRestfulInteraction.READ);
    claim.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
    claim.addInteraction().setCode(TypeRestfulInteraction.DELETE);
    claim.addOperation().setName("$submit")
        .setDefinition("http://hl7.org/fhir/us/davinci-pas/OperationDefinition/Claim-submit");
    claim.addOperation().setName("$inquiry")
        .setDefinition("http://hl7.org/fhir/us/davinci-pas/OperationDefinition/Claim-inquiry");
    return claim;
  }

  private CapabilityStatementRestResourceComponent getSubscriptionResponse() {
    CapabilityStatementRestResourceComponent subscriptionResponse = new CapabilityStatementRestResourceComponent();
    subscriptionResponse.setType("Subscription");

    List<CanonicalType> canonicalTypeList = new ArrayList<>();
    CanonicalType canonicalType = new CanonicalType();
    canonicalType.setValue("http://build.fhir.org/ig/HL7/fhir-subscription-backport-ig/StructureDefinition-backport-subscription.html");

    canonicalTypeList.add(canonicalType);
    subscriptionResponse.setSupportedProfile(canonicalTypeList);
    subscriptionResponse.addInteraction().setCode(TypeRestfulInteraction.CREATE);
    subscriptionResponse.addInteraction().setCode(TypeRestfulInteraction.READ);
    subscriptionResponse.addInteraction().setCode(TypeRestfulInteraction.DELETE);

    return subscriptionResponse;
  }
}
