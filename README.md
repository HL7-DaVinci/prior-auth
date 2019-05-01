# Prior Authorization Reference Implementation
The Da Vinci Prior Authorization Reference Implementation (RI) is a software project that conforms to the [Prior Authorization Implementation Guide](http://wiki.hl7.org/index.php?title=Da_Vinci_Prior_Authorization_FHIR_IG_Proposal) developed by the [Da Vinci Project](http://www.hl7.org/about/davinci/index.cfm?ref=common) within the [HL7 Standards Organization](http://www.hl7.org/).

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

## Demonstration

This project can be demonstrated in combination with the Da Vinci [Coverage Requirements Discovery](https://github.com/HL7-DaVinci/CRD) (CRD), [CRD request generator](https://github.com/HL7-DaVinci/crd-request-generator), and [Documentation Templates and Rules](https://github.com/HL7-DaVinci/dtr) (DTR) projects.

1. Follow the `CRD` instructions to start the `ehr-server` (i.e. `gradle tomcatRun` within `{CRD}/ehr-server`)
2. Follow the `CRD` instructions to start the CDS Hooks `server` (i.e. `gradle bootRun` within `{CRD}/server`)
3. Follow the `crd-request-generator` instructions to launch the request generator (i.e. `npm start` within that project)
4. Follow the `dtr` instructions to launch the DTR application (i.e. `npm start` within that project)
4. Follow the _Getting Started_ instructions above to start the Prior Authorization `Claim/$submit` service (i.e. `./gradlew run`)
5. Using the `crd-request-generator` application (i.e. browsing `http://localhost:3000`):
  - select `stu3`
  - enter Age `40`
  - enter Gender `Male`
  - select Code `Oxygen Thing - E0424`
  - select `Massachusetts` (in both Patient and Practitioner State)
  - select `Include Prefetch`
  - click `Submit`
![Request Generator Application](/documentation/request.png)
6. In the display CDS Hook card, select `SMART App`, which will open up a Questionnaire Form.
![CDS Hook Card](/documentation/card.png)
7. Scroll down to the bottom of the Questionnaire Form and click `Submit`.
8. A fancy `alert` will tell you the prior authorization request has been granted. Use the browser debug tools to view interesting messages on the `console`.
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
