import groovyx.net.http.HTTPBuilder
import static groovyx.net.http.ContentType.XML
import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.POST


// Authenticate with the Touchstone Server and get an API Authorization Token
String apiKey
def httpAuth = new HTTPBuilder('http://touchstone.aegis.net/touchstone/api/authenticate')

httpAuth.request(POST, XML) {
    headers.'Accept' = 'application/xml;charset=UTF-8'
    headers.'Content-Type' = 'application/xml;charset=UTF-8'
    body = '<?xml version="1.0" encoding="UTF-8"?><authenticateRequest xmlns="http://touchstone.aegis.net/api"><email>mriley@mitre.org
    </email><password>password</password></authenticateRequest>'

    response.success = { resp, authenticateResponse ->
        println "POST Response Status : ${resp.statusLine}"
        println "Authenticate Response info : ${authenticateResponse.info}"
        apiKey = "${authenticateResponse.'API-Key'}"
        println "Authenticate Response API-Key : ${apiKey}"
    }

    response.failure = { resp, authenticateResponse ->
        println "Failure Status : ${resp.statusLine}"
        println "Authenticate Response error : ${authenticateResponse.error}"
    }
}

assert apiKey != null : 'Authentication failed! No API-Key returned.'

// Execute a Test Setup
String execId
def httpExecute = new HTTPBuilder('http://touchstone.aegis.net/touchstone/api/testExecution')

httpExecute.request(POST, XML) {
    headers.'API-Key' = apiKey
    headers.'Accept' = 'application/xml;charset=UTF-8'
    headers.'Content-Type' = 'application/xml;charset=UTF-8'
    body = '<?xml version="1.0" encoding="UTF-8"?><executeTestRequest xmlns="http://touchstone.aegis.net/api"><testSetup>
    FHIRSandbox-DaVinci-FHIR4-0-0-Test-PAS-01-ClaimScenario-01-ClaimSubmit--All</testSetup></executeTestRequest>'

    response.success = { resp, executeTestResponse ->
        println "POST Response Status : ${resp.statusLine}"
        println "Test Execute Response info : ${executeTestResponse.info}"
        println "Test Execute Response exec url : ${executeTestResponse.testExecURL}"
        execId = "${executeTestResponse.testExecId}"
        println "Test Execute Response exec id : ${execId}"
    }

    response.failure = { resp, executeTestResponse ->
        println "Failure Status : ${resp.statusLine}"
        println "Test Execute Response error : ${executeTestResponse.error}"
    }
}

assert execId != null : 'Test Execution not started! No test execution id returned.'