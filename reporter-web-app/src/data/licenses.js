import {
    data as choosealicenseLicenses,
    metadata as choosealicenseMetaData
} from './choosealicense/index';
import config from '../config';

const licensesDataFromSPDX = require('spdx-license-list');
const correctToSPDX = require('spdx-correct');

export const LICENSES = (() => {
    const licensesDataFromConfig = (config && config.licenses) ? config.licenses : {};
    const licensesDataFromChoosealicense = choosealicenseLicenses.data;
    const licenseValueHandler = {
        get: (obj, prop) => {
            let data;
            let values = [];
            const object = obj;

            if (object[prop]) {
                return object[prop];
            }

            const { spdxId } = obj;
            // Check if property name has been defined in Reporter's config
            const licenseDataFromConfig = licensesDataFromConfig[spdxId]
                ? licensesDataFromConfig[spdxId] : licensesDataFromConfig[prop];

            if (licenseDataFromConfig && licenseDataFromConfig[prop]) {
                data = licenseDataFromConfig[prop];

                if (!Array.isArray(data)) {
                    object[prop] = data;
                    return obj[prop];
                }

                values = [...values, ...data];
            }

            const licenseDataFromSPDX = licensesDataFromSPDX[spdxId];

            if (licenseDataFromSPDX && licenseDataFromSPDX[prop]) {
                data = licenseDataFromSPDX[prop];

                if (!Array.isArray(data)) {
                    object[prop] = data;
                    return obj[prop];
                }

                values = [...values, ...data];
            }

            // If property name is not found in user specified config
            // try to see if it's in SPDX licenses metadata
            const licenseDataFromChoosealicense = licensesDataFromChoosealicense[spdxId];

            if (licenseDataFromChoosealicense && licenseDataFromChoosealicense[prop]) {
                data = licenseDataFromChoosealicense[prop];

                if (!Array.isArray(data)) {
                    object[prop] = data;
                    return object[prop];
                }

                values = [...values, ...data];
            }

            return (values.length !== 0) ? values : '';
        }
    };
    const licenseHandler = {
        get: (obj, prop) => {
            let licenseDataFromConfig;
            let spdxId;
            const object = obj;

            if (!object[prop]) {
                spdxId = correctToSPDX(prop);

                // Check if property name has been defined in Reporter's config
                licenseDataFromConfig = licensesDataFromConfig[spdxId]
                    ? licensesDataFromConfig[spdxId] : licensesDataFromConfig[prop];

                if (licenseDataFromConfig && licenseDataFromConfig[prop]) {
                    object[prop] = new Proxy(licenseDataFromConfig[prop], licenseValueHandler);
                    object[prop]['spdx-id'] = spdxId;
                    return object[prop];
                }

                // If property name is not found in user specified config
                // try to see if it's in SPDX licenses metadata
                if (licensesDataFromSPDX[spdxId]) {
                    object[prop] = new Proxy(licensesDataFromSPDX[spdxId], licenseValueHandler);
                    object[prop].spdxId = spdxId;
                    return object[prop];
                }

                return undefined;
            }

            return object[prop];
        }
    };

    /* Using ES6 Proxy to create Object that with default values for
     * specific attribute coming for SPDX license list e.g.
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
     * For more details on ES6 proxy please see
     * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy
     */
    return new Proxy({}, licenseHandler);
})();

export const LICENSES_PROVIDERS = {
    choosealicense: choosealicenseMetaData
};
