package org.hl7.davinci.priorauth;

import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Period;
import org.hl7.fhir.r4.model.Reference;

import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.*;

public class FhirScanner {

  /**
   * Finds any instance (recursively) of a Reference within the specified object
   *
   * @param obj The object to search
   * @return A list of Reference instances found in the object
   */
  public static List<Reference> findReferences(Object obj) {
    List<Reference> references = new ArrayList<>();
    scanInstance(obj, Reference.class, Collections.newSetFromMap(new IdentityHashMap<>()), references);
    return references;
  }

  public static List<Period> findPeriods(Object obj) {
    List<Period> periods = new ArrayList<>();
    scanInstance(obj, Period.class, Collections.newSetFromMap(new IdentityHashMap<>()), periods);
    return periods;
  }

  /**
   * Finds any instance (recursively) of a CodeableConcept or Coding within the specified object
   *
   * @param obj The object to search
   * @return A list of CodeableConcept or Coding instances found in the object
   */
  public static List<Coding> findCodings(Object obj) {
    List<Coding> codes = new ArrayList<>();
    scanInstance(obj, Coding.class, Collections.newSetFromMap(new IdentityHashMap<>()), codes);
    return codes;
  }

  public static List<CodeableConcept> findCodeableConcepts(Object obj) {
    List<CodeableConcept> codeableConcepts = new ArrayList<>();
    scanInstance(obj, CodeableConcept.class, Collections.newSetFromMap(new IdentityHashMap<>()), codeableConcepts);
    return codeableConcepts;
  }

  /**
   * Scans an object recursively to find any instances of the specified type
   *
   * @param objectToScan The object to scan
   * @param lookingFor   The class/type to find instances of
   * @param scanned      A pre-initialized set that is used internally to determine what has already been scanned to avoid endless recursion on self-referencing objects
   * @param results      A pre-initialized collection/list that will be populated with the results of the scan
   * @param <T>          The type of class to look for instances of that must match the initialized results collection
   * @implNote Found this code online from https://stackoverflow.com/questions/57758392/is-there-are-any-way-to-get-all-the-instances-of-type-x-by-reflection-utils
   */
  private static <T> void scanInstance(Object objectToScan, Class<T> lookingFor, Set<? super Object> scanned, Collection<? super T> results) {
    if (objectToScan == null) {
      return;
    }
    if (!scanned.add(objectToScan)) { // to prevent any endless scan loops
      return;
    }
    // you might need some extra code if you want to correctly support scanning for primitive types
    if (lookingFor.isInstance(objectToScan)) {
      results.add(lookingFor.cast(objectToScan));
      // either return or continue to scan of target object might contains references to other objects of this type
    }
    // we won't find anything intresting in Strings, and it is pretty popular type
    if (objectToScan instanceof String) {
      return;
    }
    // LINK-805: avoid illegal reflective access
    // consider adding checks for other types we don't want to recurse into
    else if (objectToScan instanceof Enum) {
      return;
    }
    // basic support for popular java types to prevent scanning too much of java internals in most common cases, but might cause
    // side-effects in some cases
    else if (objectToScan instanceof Iterable) {
      ((Iterable<?>) objectToScan).forEach(obj -> scanInstance(obj, lookingFor, scanned, results));
    } else if (objectToScan instanceof Map) {
      ((Map<?, ?>) objectToScan).forEach((key, value) -> {
        scanInstance(key, lookingFor, scanned, results);
        scanInstance(value, lookingFor, scanned, results);
      });
    }
    // remember about arrays, if you want to support primitive types remember to use Array class instead.
    else if (objectToScan instanceof Object[]) {
      int length = Array.getLength(objectToScan);
      for (int i = 0; i < length; i++) {
        scanInstance(Array.get(objectToScan, i), lookingFor, scanned, results);
      }
    } else if (objectToScan.getClass().isArray()) {
      return; // primitive array
    } else {
      Class<?> currentClass = objectToScan.getClass();
      while (currentClass != Object.class) {
        for (Field declaredField : currentClass.getDeclaredFields()) {
          // skip static fields
          if (Modifier.isStatic(declaredField.getModifiers())) {
            continue;
          }
          // skip primitives, to prevent wrapping of "int" to "Integer" and then trying to scan its "value" field and loop endlessly.
          if (declaredField.getType().isPrimitive()) {
            return;
          }
          try {
            if (!declaredField.trySetAccessible()) {
              // either throw error, skip, or use more black magic like Unsafe class to make field accessible anyways.
              continue; // I will just skip it, it's probably some internal one.
            }
            scanInstance(declaredField.get(objectToScan), lookingFor, scanned, results);
          } catch (IllegalAccessException | SecurityException ignored) {
            continue;
          }
        }
        currentClass = currentClass.getSuperclass();
      }
    }
  }
}
