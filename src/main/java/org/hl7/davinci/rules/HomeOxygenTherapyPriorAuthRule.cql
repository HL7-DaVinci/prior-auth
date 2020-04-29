library HomeOxygenTherapyPriorAuthRule version '0.1.0'
using FHIR version '4.0.0'
include FHIRHelpers version '4.0.0' called FHIRHelpers

define PRIORAUTH_GRANTED:
  false

define PRIORAUTH_PENDED:
  true