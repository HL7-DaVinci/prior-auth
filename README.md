# Prior Authorization Reference Implementation

The Da Vinci Prior Authorization Reference Implementation (RI) is a software project that conforms to the [Prior Authorization Implementation Guide (IG)](https://build.fhir.org/ig/HL7/davinci-pas/index.html) and the [Prior Authorization IG Proposal](http://wiki.hl7.org/index.php?title=Da_Vinci_Prior_Authorization_FHIR_IG_Proposal) developed by the [Da Vinci Project](http://www.hl7.org/about/davinci/index.cfm?ref=common) within the [HL7 Standards Organization](http://www.hl7.org/).

## Requirements

- Java JDK 8

## Getting Started

Build, test, and start the Prior Authorization microservice:

```
./gradlew install
./gradlew clean check
./gradlew bootRun
```

To run the microservice in debug mode (which enables debug log statements, an endpoint to view the database, and and endpoint to prefill the database with test data) use:

```
./gradlew bootRun --args='debug'
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

| Service                                                                       | Methods  | Description                                                                                                                                                                                                        |
| ----------------------------------------------------------------------------- | -------- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
| `/metadata`                                                                   | `GET`    | The FHIR [capabilities interaction](http://hl7.org/fhir/R4/http.html#capabilities) that returns a FHIR [CapabilityStatement](http://hl7.org/fhir/R4/capabilitystatement.html) resource describing these services.  |
| `/Bundle?patient.identifier={patient}`                                        | `GET`    | The FHIR [Bundle](http://hl7.org/fhir/R4/bundle.html) endpoint returns all the `Bundle`s that were submitted to the `Claim/$submit` operation for `patient`.                                                       |
| `/Bundle?patient.identifier={patient}&status={status}`                        | `GET`    | The FHIR [Bundle](http://hl7.org/fhir/R4/bundle.html) endpoint returns all the `Bundle`s that were submitted to the `Claim/$submit` operation for `patient` with the given `status`.                               |
| `/Bundle?identifier={id}&patient.identifier={patient}`                        | `GET`    | Gets a single `Bundle` by `id` and `patient`                                                                                                                                                                       |
| `/Bundle?identifier={id}&patient.identifier={patient}&status={status}`        | `GET`    | Gets a single `Bundle` by `id`, `patient`, and `status`.                                                                                                                                                           |
| `/Bundle?identifier={id}&patient.identifier={patient}`                        | `DELETE` | Deletes a single `Bundle` by `id` and `patient`                                                                                                                                                                    |
| `/Claim?patient.identifier={patient}`                                         | `GET`    | The FHIR [Claim](http://hl7.org/fhir/R4/claim.html) endpoint returns all the `Claim`s that were submitted to the `Claim/$submit` operation for `patient`.                                                          |
| `/Claim?patient.identifier={patient}&status={status}`                         | `GET`    | The FHIR [Claim](http://hl7.org/fhir/R4/claim.html) endpoint returns all the `Claim`s that were submitted to the `Claim/$submit` operation for `patient` with the given `status`.                                  |
| `/Claim?identifier={id}&patient.identifier={patient}`                         | `GET`    | Gets a single `Claim` by `id` and `patient`                                                                                                                                                                        |
| `/Claim?identifier={id}&patient.identifier={patient}&status={status}`         | `GET`    | Gets a single `Claim` by `id`, `patient`, and `status`.                                                                                                                                                            |
| `/Claim?identifier={id}&patient.identifier={patient}`                         | `DELETE` | Deletes a single `Claim` by `id` and `patient`                                                                                                                                                                     |
| `/Claim$submit`                                                               | `POST`   | Submit a `Bundle` containing a Prior Authorization `Claim` with all the necessary supporting resources. The response to a successful submission is a `ClaimResponse`.                                              |
| `/ClaimResponse?patient.identifier={patient}`                                 | `GET`    | The FHIR [ClaimResponse](http://hl7.org/fhir/R4/claimresponse.html) endpoint returns all the `ClaimResponse`s that were generated in response to `Claim/$submit` operations for `patient`.                         |
| `/ClaimResponse?patient.identifier={patient}&status={status}`                 | `GET`    | The FHIR [ClaimResponse](http://hl7.org/fhir/R4/claimresponse.html) endpoint returns all the `ClaimResponse`s that were generated in response to `Claim/$submit` operations for `patient` with the given `status`. |
| `/ClaimResponse?identifier={id}&patient.identifier={patient}`                 | `GET`    | Gets a single `ClaimResponse` by `id` and `patient`.                                                                                                                                                               |
| `/ClaimResponse?identifier={id}&patient.identifier={patient}&status={status}` | `GET`    | Gets a single `ClaimResponse` by `id`, `patient`, and `status`.                                                                                                                                                    |
| `/ClaimResponse?identifier={id}&patient.identifier={patient}`                 | `DELETE` | Deletes a single `ClaimResponse` by `id` and `patient`.                                                                                                                                                            |
| `/Subscription`                                                               | `POST`   | Submit a new Subscription for a pended or partial ClaimResponse using rest-hook or websockets.                                                                                                                     |
| `/Subscription?identifier={id}&patient.identifier={patient}`                  | `DELETE` | Deletes (todo update which id this uses and if it deletes all or just a single).                                                                                                                                   |

> _Note About IDs_: The Prior Authorization service generates an `id` when a successful `Claim/$submit` operation is performed. The `Bundle` that was submitted will subsequently be available at `/Bundle?identifier={id}&patient.identifier={patient}`, and the `Claim` from the submission will be available at `/Claim?identifier={id}&patient.identifier={patient}`, and the `ClaimResponse` will also be available at `/ClaimResponse?identifier={id}&patient.identifier={patient}`. _All three resources will share the same `id`._

> _Note About DELETE_: A DELETE by `id` to one resource (i.e. `Bundle`, `Claim`, `ClaimResponse`) is a _Cascading Delete_ and it will delete all associated and related resources.

If debug mode is enabled the following endpoints are available for use at `http://localhost:9000/fhir/debug`:

| Service          | Methods | Description                                               |
| ---------------- | ------- | --------------------------------------------------------- |
| `/Bundle`        | `GET`   | HTML page to view the Bundle table in the database        |
| `/Claim`         | `GET`   | HTML page to view the Claim table in the database         |
| `/ClaimResponse` | `GET`   | HTML page to view the ClaimResponse table in the database |
| `/ClaimItem`     | `GET`   | HTML page to view the ClaimItem table in the database     |
| `/Subscription`  | `GET`   | HTML page to view the Subscription table in the database  |

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

The first `entry` of the submitted `Bundle` should contain a `Claim`, followed by a `QuestionnaireResponse` which includes answers in response to questions presented by Da Vinci [Documentation Templates and Rules](https://github.com/HL7-DaVinci/dtr) (DTR), then the `DeviceRequest` (or other resource type) that actually requires the prior authorization, followed by all supporting FHIR resources including the `Patient`, `Practitioner`, `Coverage`, and relevant `Condition` and `Observation` resources used in DTR calculations or otherwise used as supporting information.

To cancel the Claim submit a `Claim` resource with the `id` of the Claim to cancel and set the `status` to `cancelled`. If the Claim exists and is not already cancelled the database will be update to reflect the cancellation.

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
    "coding": [
      {
        "system": "http://terminology.hl7.org/CodeSystem/claim-type",
        "code": "professional",
        "display": "Professional"
      }
    ]
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

## Contents of `/Subscription` Submission

`POST`ing to the `/Subscription` endpoint is used to submit a new Rest-Hook or WebSocket based subscription for a pended or partial ClaimResponse. Once an update has been made a notification will be sent to the subscription. The subscriber can then poll using the original `identifier` to obtain the most updated ClaimResponse.

The body for a Rest-Hook subscription is as follows:

```json
{
  "resourceType": "Subscription",
  "criteria": "identifier={id}&patient.identifier={patient}&status=active",
  "channel": {
    "type": "rest-hook",
    "endpoint": "http://localhost:9090/fhir/SubscriptionNotification?identifier={id}&patient.identifier={patient}&status=active"
  }
}
```

For more information on rest-hook subscriptions jump to Using Rest-Hook Subscriptions.

The body for a WebSocket subscription is as follows:

```json
{
  "resourceType": "Subscription",
  "criteria": "identifier={id}&patient.identifier={patient}&status=active",
  "channel": {
    "type": "websocket"
  }
}
```

For more information on WebSocket subscriptions jump to Using WebSocket Subscriptions.

## Response to `/Subscription` Submission

Assuming the contents of the Subscription are valid and the server is able to process the request correctly it will respond with the same Subscription resource and the id set to the logical id of the Subscription. For example, the response to a WebSocket Subscription would be:

```json
{
  "resourceType": "Subscription",
  "id": "{new subscription id}",
  "criteria": "identifier={id}&patient.identifier={patient}&status=active",
  "channel": {
    "type": "websocket"
  }
}
```

When using WebSocket subscriptions the id provided in the response is the id used in all WebSocket messages.

## Using Rest-Hook Subscriptions

Rest-Hook subscriptions require the client to operate an external server which can operate REST endpoints. The client server for this RI is provided in the [Prior Auth Client Github](https://github.com/HL7-DaVinci/prior-auth-client). By default this client will start the server at `http://localhost:9090/fhir` and will receive notifications on the `/SubscriptionNotification?identifier={id}&patient.identifier={patient}&status=active` endpoint. More details can be found on the Prior Auth Client Github.

The flow for Rest-Hook subscriptions is as follows:

1.  Start the Prior Auth service
2.  Start the Prior Auth Client service
3.  Submit a Claim to `/Claim/$submit`
4.  Subscribe to a pended or partial ClaimResponse by submitting a Rest-Hook subscription to `/Subscription`
5.  When an update is ready the Prior Auth service will send a `GET` to the `channel.endpoint` provided in the Subscription
6.  The Prior Auth Client will receive the notification and poll for the updated ClaimResponse resource. If the ClaimResponse has outcome `complete` or `error` the client performs a `DELETE` on `/Subscription`

## Using WebSocket Subscriptions

WebSocket subscriptions do not require the client to operate an external REST server, however

To use WebSocket subscriptions the client must submit a Subscription as well as bind the Subscription to a WebSocket using the WebSocket client. The steps to do that are as follows:

1.  Start the Prior Auth service
2.  Submit a Claim to `/Claim/$submit`
3.  Subscribe to a pended or partial ClaimResponse by submitting a WebSocket subscription to `/Subscription`. The response to this submission will contain the logical id of the Subscription used in step 5
4.  The client should connect to the WebSocket `ws://localhost:9000/connect` and subscribe to `/private/notification`
5.  The client then binds the Subscription id by sending the message `bind: id` (using the logical id of the Subscription) to `/subscribe` over the WebSocket
6.  If the id is bound successfully the client receives the message `bound: id` over `/private/notification`
7.  When an update is ready the Prior Auth service will send the message `ping: id` over `/private/notification`
8.  The client can then poll for the updated ClaimResponse

The [Prior Auth Client Github](https://github.com/HL7-DaVinci/prior-auth-client) provides a WebSocket client in `src/main/resources/index.html`. This client handles steps 4 and 5 through the web interface. Details on how to use the client are provided in the Prior Auth Client README.

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
