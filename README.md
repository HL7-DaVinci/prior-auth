# Prior Authorization Reference Implementation
The Da Vinci Prior Authorization Reference Implementation (RI) is a software project that conforms to the [Prior Authorization Implementation Guide](http://wiki.hl7.org/index.php?title=Da_Vinci_Prior_Authorization_FHIR_IG_Proposal) developed by the [Da Vinci Project](http://www.hl7.org/about/davinci/index.cfm?ref=common) within the [HL7 Standards Organization](http://www.hl7.org/).

## Requirements
- Java JDK 8

## Getting Started

Start the Prior Authorization microservice:
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
