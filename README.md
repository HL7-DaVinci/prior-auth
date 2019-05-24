# Prior Authorization Reference Implementation
The Da Vinci Prior Authorization Reference Implementation (RI) is a software project that conforms to the [Prior Authorization Implementation Guide (IG)](https://build.fhir.org/ig/HL7/davinci-pas/index.html) and the [Prior Authorization IG Proposal](http://wiki.hl7.org/index.php?title=Da_Vinci_Prior_Authorization_FHIR_IG_Proposal) developed by the [Da Vinci Project](http://www.hl7.org/about/davinci/index.cfm?ref=common) within the [HL7 Standards Organization](http://www.hl7.org/).

## Requirements
- Java JDK 8

## Getting Started

Build, test, and start the Prior Authorization microservice:
```
./gradlew install
./gradlew clean check
./gradlew run
```

Access the microservice:
```
curl http://localhost:9000/fhir/metadata
curl http://localhost:9000/fhir/Bundle
curl http://localhost:9000/fhir/Claim
curl http://localhost:9000/fhir/ClaimResponse
```

Submit a prior authorization request:
```
curl -X POST
     -H "Content-Type: application/json"
     -d @src/test/resources/bundle-prior-auth.json
     http://localhost:9000/fhir/Claim/\$submit
```

## FHIR Services

The service endpoints in the table below are relative to `http://localhost:9000/fhir`.

Service | Methods | Description
--------|---------|------------
`/metadata` | `GET` | The FHIR [capabilities interaction](http://hl7.org/fhir/R4/http.html#capabilities) that returns a FHIR [CapabilityStatement](http://hl7.org/fhir/R4/capabilitystatement.html) resource describing these services.
`/Bundle` | `GET` | The FHIR [Bundle](http://hl7.org/fhir/R4/bundle.html) endpoint returns all the `Bundle`s that were submitted to the `Claim/$submit` operation.
`/Bundle/{id}` | `GET` | Gets a single `Bundle` by `id`
`/Bundle/{id}` | `DELETE` | Deletes a single `Bundle` by `id`
`/Claim` | `GET` | The FHIR [Claim](http://hl7.org/fhir/R4/claim.html) endpoint returns all the `Claim`s that were submitted to the `Claim/$submit` operation.
`/Claim/{id}` | `GET` | Gets a single `Claim` by `id`
`/Claim/{id}` | `DELETE` | Deletes a single `Claim` by `id`
`/Claim/$submit` | `POST` | Submit a `Bundle` containing a Prior Authorization `Claim` with all the necessary supporting resources. The response to a successful submission is a `ClaimResponse`.
`/ClaimResponse` | `GET` | The FHIR [ClaimResponse](http://hl7.org/fhir/R4/claimresponse.html) endpoint returns all the `ClaimResponse`s that were generated in response to `Claim/$submit` operations.
`/ClaimResponse/{id}` | `GET` | Gets a single `ClaimResponse` by `id`
`/ClaimResponse/{id}` | `DELETE` | Deletes a single `ClaimResponse` by `id`

> *Note About IDs*: The Prior Authorization service generates an `id` when a successful `Claim/$submit` operation is performed. The `Bundle` that was submitted will subsequently be available at `/Bundle/{id}`, and the `Claim` from the submission will be available at `/Claim/{id}`, and the `ClaimResponse` will also be available at `/ClaimResponse/{id}`. _All three resources will share the same `id`._

> *Note About DELETE*: A DELETE by `id` to one resource (i.e. `Bundle`, `Claim`, `ClaimResponse`) is a _Cascading Delete_ and it will delete all associated and related resources.

## Contents of `/Claim/$submit` Submission

The body of the `/Claim/$submit` operation are as follows:

```
 + Bundle
 |
 +-+ entry
   |
   +-- Claim
   |
   +-- QuestionnaireResponse
   |
   +-- DeviceRequest
   |
   +-- Other Resources (Patient, Practitioner, Coverage, Condition, Observation)
```

The first `entry` of the submitted `Bundle` should contain a `Claim`, followed by a `QuestionnaireResponse` which includes answers in response to questions presented by Da Vinci [Documentation Templates and Rules](https://github.com/HL7-DaVinci/dtr) (DTR), then the `DeviceRequest` that actually requires the prior authorization, followed by all supporting FHIR resources including the `Patient`, `Practitioner`, `Coverage`, and relevant `Condition` and `Observation` resources used in DTR calculations or otherwise used as supporting information.

## Response of the `/Claim/$submit` Operation

Assuming the structure and contents of the submitted `Bundle` are adequate, the service will responsed with a `ClaimResponse` as detailed below. Otherwise, the service will respond with an `OperationalOutcome` containing an error message.

```
 + ClaimResponse
 + ClaimResponse.id = {id}
 + ClaimResponse.status
 + ClaimResponse.type
 + ClaimResponse.use = "preauthorization"
 + ClaimResponse.patient = { reference: Patient }
 + ClaimResponse.created
 + ClaimResponse.insurer
 + ClaimResponse.request = { reference: Claim/{id} }
 + ClaimResponse.outcome
 + ClaimResponse.disposition
 + ClaimResponse.preAuthRef = {prior authorization number}
```

With a successful submission, the actual Prior Authorization Number is located in the `ClaimResponse.preAuthRef` field.

For example:
```json
{
  "resourceType": "ClaimResponse",
  "id": "536d41f2-0273-4807-a0e6-8d9909146667",
  "status": "active",
  "type": {
    "coding": [ {
        "system": "http://terminology.hl7.org/CodeSystem/claim-type",
        "code": "professional",
        "display": "Professional"
    } ]
  },
  "use": "preauthorization",
  "patient": { "reference": "Patient/pat013" },
  "created": "2019-05-04T15:32:06+00:00",
  "insurer": {
    "display": "Unknown"
  },
  "request": { "reference": "Claim/536d41f2-0273-4807-a0e6-8d9909146667" },
  "outcome": "complete",
  "disposition": "Granted",
  "preAuthRef": "536d41f2-0273-4807-a0e6-8d9909146667"
}
```

## Demonstration

This project can be demonstrated in combination with the Da Vinci [Coverage Requirements Discovery](https://github.com/HL7-DaVinci/CRD) (CRD), [CRD request generator](https://github.com/HL7-DaVinci/crd-request-generator), and [Documentation Templates and Rules](https://github.com/HL7-DaVinci/dtr) (DTR) projects.

1. Follow the `CRD` instructions to start the `ehr-server` (i.e. `gradle tomcatRun` within `{CRD}/ehr-server`)
2. Follow the `CRD` instructions to start the CDS Hooks `server` (i.e. `gradle bootRun` within `{CRD}/server`)
3. Follow the `crd-request-generator` instructions to launch the request generator (i.e. `npm start` within that project)
4. Follow the `dtr` instructions to launch the DTR application (i.e. `npm start` within that project)
5. Follow the _Getting Started_ instructions above to start the Prior Authorization `Claim/$submit` service (i.e. `./gradlew run`)
6. Using the `crd-request-generator` application (i.e. browsing `http://localhost:3000`):
  - select `stu3`
  - enter Age `40`
  - enter Gender `Male`
  - select Code `Oxygen Thing - E0424`
  - select `Massachusetts` (in both Patient and Practitioner State)
  - select `Include Prefetch`
  - click `Submit`

![Request Generator Application](/documentation/request.png)

7. In the display CDS Hook card, select `SMART App`, which will open up a Questionnaire Form.

![CDS Hook Card](/documentation/card.png)

8. Scroll down to the bottom of the Questionnaire Form and click `Submit`.
9. A fancy `alert` will tell you the prior authorization request has been granted. Use the browser debug tools to view interesting messages on the `console`.

![Alert Message](/documentation/alert.png)


## Docker
Build the docker image:
```
docker build -t hspc/davinci-prior-auth:latest .
```

Run the docker image:
```
docker run -p 9000:9000 -it --rm --name davinci-prior-auth hspc/davinci-prior-auth:latest
```

## Questions and Contributions
Questions about the project can be asked in the [DaVinci stream on the FHIR Zulip Chat](https://chat.fhir.org/#narrow/stream/179283-DaVinci).

This project welcomes Pull Requests. Any issues identified with the RI should be submitted via the [GitHub issue tracker](https://github.com/HL7-DaVinci/prior-auth/issues).
