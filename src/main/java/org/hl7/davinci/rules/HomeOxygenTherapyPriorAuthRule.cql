library HomeOxygenTherapyPriorAuthRule version '0.1.0'
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0' called FHIRHelpers

codesystem "LOINC": 'http://loinc.org'
code "TNM clinical primary tumor code": '21905-5' from "LOINC" display 'Primary tumor.clinical [Class] Cancer'

context Patient

define PRIORAUTH_GRANTED:
  [Claim] C
  return C.status.value

define PRIORAUTH_PENDED:
  false