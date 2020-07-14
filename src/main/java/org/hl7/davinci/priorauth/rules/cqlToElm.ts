import { CqlObject } from './cql-extractor';
import config from 'utils/ConfigManager';
import {
  extractJSONContent,
  extractMultipartBoundary,
  extractMultipartFileName
} from 'utils/regexes';

const url = config.get('cqlToElmWebserviceUrl');

export interface ElmObject {
  main: object;
  libraries: {
    [key: string]: object;
  };
}

/**
 * Function that requests web_service to convert the cql into elm.
 * @param cql - cql file that is the input to the function.
 * @return The resulting elm translation of the cql file.
 */
export default function convertCQL(cql: CqlObject): Promise<ElmObject> {
  // Connect to web service
  const formdata = new FormData();
  Object.keys(cql.libraries).forEach((key, i) => {
    formdata.append(`${key}`, cql.libraries[key]);
  });

  formdata.append('main', cql.main);
  return fetch(url, {
    method: 'POST',
    body: formdata
  }).then(elm => {
    const header = elm.headers.get('content-type');
    let boundary = '';
    if (header) {
      // sample header= "multipart/form-data;boundary=Boundary_1"
      const result = extractMultipartBoundary.exec(header);
      boundary = result ? `--${result[1]}` : '';
    }
    const obj: ElmObject = { main: {}, libraries: {} };
    return elm.text().then(text => {
      const elms = text.split(boundary).reduce((oldArray, line, i) => {
        const body = extractJSONContent.exec(line);
        if (body) {
          const elmName = extractMultipartFileName.exec(line);
          if (elmName && elmName[1] === 'main') {
            oldArray[elmName[1]] = JSON.parse(body[1]);
          } else if (elmName) {
            oldArray.libraries[elmName[1]] = JSON.parse(body[1]);
          }
        }
        return oldArray;
      }, obj);

      return elms;
    });
  });
}

export function convertBasicCQL(cql: string): Promise<object> {
  // Connect to web service

  return fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/cql',
      Accept: 'application/elm+json'
    },
    body: cql
  }).then(elm => elm.json());
}