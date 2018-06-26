import { data as choosealicenseLicenses } from '../data/choosealicense/index';
import { metadata as choosealicenseMetaData } from '../data/choosealicense/index';
import { config } from '../config';

export const LICENSES = window.LICENSES =  (() => {
  const licensesDataFromConfig = (config && config.hasOwnProperty('licenses')) ? config.licenses : {}, 
        licensesDataFromChoosealicense = choosealicenseLicenses.data,
        licensesDataFromSPDX = require('spdx-license-list/full');

  var correctToSPDX = require('spdx-correct');

  // FIXME Remove window vars added for debugging purposes
  window.config = config;
  window.correctToSPDX = correctToSPDX;
  window.licensesDataFromConfig = licensesDataFromConfig
  window.licensesDataFromSPDX = licensesDataFromSPDX;
  window.choosealicense = licensesDataFromChoosealicense;
  window.choosealicenseMetaData = choosealicenseMetaData;

  let licenseHandler = {
        get: function (obj, prop) {
          let licenseDataFromConfig,
              spdxId;

          if (!obj.hasOwnProperty(prop)) {
            spdxId = correctToSPDX(prop);

            // Check if property name has been defined in Reporter's config
            licenseDataFromConfig = licensesDataFromConfig[spdxId] ? licensesDataFromConfig[spdxId] : licensesDataFromConfig[prop];

            if (licenseDataFromConfig && licenseDataFromConfig.hasOwnProperty(prop)) {
              obj[prop] = new Proxy(licenseDataFromConfig[prop], licenseValueHandler);
              obj[prop]['spdx-id'] = spdxId;
              return obj[prop];
            }

            // If property name is not found in user specified config try to see if it's in SPDX licenses metadata
            if (licensesDataFromSPDX.hasOwnProperty(spdxId)) {
              obj[prop] = new Proxy(licensesDataFromSPDX[spdxId], licenseValueHandler);
              obj[prop]['spdxId'] = spdxId;
              return obj[prop];
            }

            return undefined;
          } else {
            return obj[prop];
          }
        }
      },
      licenseValueHandler = {
        get: function (obj, prop) {
          let data,
              licenseDataFromConfig,
              licenseDataFromChoosealicense,
              licenseDataFromSPDX,
              spdxId = obj.spdxId,
              values = [];

          if (obj.hasOwnProperty(prop)) {
            return obj[prop];
          }

          // Check if property name has been defined in Reporter's config
          licenseDataFromConfig = licensesDataFromConfig[spdxId] ? licensesDataFromConfig[spdxId] : licensesDataFromConfig[prop];

          if (licenseDataFromConfig && licenseDataFromConfig.hasOwnProperty(prop)) {
            data = licenseDataFromConfig[prop];
            
            if (!Array.isArray(data)) {
              return obj[prop] = data;
            } else {
              values = [...values, ...data];
            }
          }

          licenseDataFromSPDX = licensesDataFromSPDX[spdxId];

          if (licenseDataFromSPDX && licenseDataFromSPDX.hasOwnProperty(prop)) {
            data = licenseDataFromSPDX[prop];
            
            if (!Array.isArray(data)) {
              return obj[prop] = data;
            } else {
               values = [...values, ...data];
            }
          }

          // If property name is not found in user specified config try to see if it's in SPDX licenses metadata
          licenseDataFromChoosealicense = licensesDataFromChoosealicense[spdxId];

          if (licenseDataFromChoosealicense && licenseDataFromChoosealicense[prop]) {
            data = licenseDataFromChoosealicense[prop];

            if (!Array.isArray(data)) {
              return obj[prop] = data;
            } else {
              values = [...values, ...data];
            }
          }
          
          return (values.length !== 0) ? values : '';
        }
      };

  /* Using ES6 Proxy to create Object that with default values for specific attribute coming for SPDX license list e.g.
   *
   * licenses[Apache-2.0] = {
   *  spdxId: Apache-2.0
   * }
   *
   * results effectively in
   * 
   * licenses[Apache-2.0] = {
   *   spdxId: Apache-2.0,
   *.  name: Apache License 2.0,
   *.  licenseText: "Apache License↵Version 2.0, January 2004↵http://www.apache.org/licenses/ …"
   *.  osiApproved: true,
   *.  url: "http://www.apache.org/licenses/LICENSE-2.0"
   *.}
   *
   * For more details on ES6 proxy please see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy
   */
  return new Proxy({}, licenseHandler);
})();

export const LICENSES_PROVIDERS = window.LICENSES_PROVIDERS = (() => {
    return {
      choosealicense: choosealicenseMetaData
    }
})();