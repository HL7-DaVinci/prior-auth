package org.hl7.davinci.priorauth;

import java.util.Calendar;

import javax.enterprise.context.RequestScoped;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriInfo;

import org.hl7.fhir.r4.model.CapabilityStatement;
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

/**
 * The metadata microservice provides a CapabilityStatement.
 */
@RequestScoped
@Path("metadata")
public class Metadata {

  @Context
  private UriInfo uri;

  /**
   * Cached CapabilityStatement.
   */
  private CapabilityStatement capabilityStatement = null;

  @GET
  @Produces({MediaType.APPLICATION_JSON, "application/fhir+json"})
  public Response getMetadata() {
    if (capabilityStatement == null) {
      capabilityStatement = buildCapabilityStatement();
    }
    String json = App.DB.json(capabilityStatement);
    return Response.ok(json).build();
  }

  @GET
  @Produces({MediaType.APPLICATION_XML, "application/fhir+xml"})
  public Response getMetadataXml() {
    if (capabilityStatement == null) {
      capabilityStatement = buildCapabilityStatement();
    }
    String xml = App.DB.xml(capabilityStatement);
    return Response.ok(xml).build();
  }

  /**
   * Builds the CapabilityStatement describing the Prior Authorization
   * Reference Implementation.
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
    CapabilityStatementSoftwareComponent software =
        new CapabilityStatementSoftwareComponent();
    software.setName("https://github.com/HL7-DaVinci/prior-auth");
    metadata.setSoftware(software);
    CapabilityStatementImplementationComponent implementation = 
        new CapabilityStatementImplementationComponent();
    implementation.setDescription(metadata.getTitle());
    implementation.setUrl(uri.getBaseUri() + "metadata");
    metadata.setImplementation(implementation);
    metadata.setFhirVersion(FHIRVersion._4_0_0);
    metadata.addFormat("json");
    metadata.addFormat("xml");
    metadata.addImplementationGuide("https://build.fhir.org/ig/HL7/davinci-pas/index.html");
    metadata.addImplementationGuide("http://wiki.hl7.org/index.php?title=Da_Vinci_Prior_Authorization_FHIR_IG_Proposal");
    CapabilityStatementRestComponent rest =
        new CapabilityStatementRestComponent();
    rest.setMode(RestfulCapabilityMode.SERVER);
    CapabilityStatementRestSecurityComponent security =
        new CapabilityStatementRestSecurityComponent();
    security.setCors(true);
    rest.setSecurity(security);

    // Claim Resource
    CapabilityStatementRestResourceComponent claim =
        new CapabilityStatementRestResourceComponent();
    claim.setType("Claim");
    // TODO claim.setSupportedProfile(theSupportedProfile);
    claim.addInteraction().setCode(TypeRestfulInteraction.READ);
    claim.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
    claim.addInteraction().setCode(TypeRestfulInteraction.DELETE);
    claim.addOperation()
      .setName("$submit")
      .setDefinition("http://hl7.org/fhir/OperationDefinition/Claim-submit");
    claim.addOperation()
      .setName("$submit")
      .setDefinition("https://build.fhir.org/ig/HL7/davinci-pas/Claim-submit.html");
    rest.addResource(claim);

    // ClaimResponse Resource
    CapabilityStatementRestResourceComponent claimResponse =
        new CapabilityStatementRestResourceComponent();
    claimResponse.setType("ClaimResponse");
    // TODO claimResponse.setSupportedProfile(theSupportedProfile);
    claimResponse.addInteraction().setCode(TypeRestfulInteraction.READ);
    claimResponse.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
    claimResponse.addInteraction().setCode(TypeRestfulInteraction.DELETE);
    rest.addResource(claimResponse);

    // Bundle Resource
    CapabilityStatementRestResourceComponent bundle =
        new CapabilityStatementRestResourceComponent();
    bundle.setType("Bundle");
    bundle.addInteraction().setCode(TypeRestfulInteraction.READ);
    bundle.addInteraction().setCode(TypeRestfulInteraction.SEARCHTYPE);
    bundle.addInteraction().setCode(TypeRestfulInteraction.DELETE);
    rest.addResource(bundle);
  
    metadata.addRest(rest);

    return metadata;
  }
}
