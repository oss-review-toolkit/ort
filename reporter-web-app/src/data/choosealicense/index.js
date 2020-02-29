/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * SPDX-License-Identifier: Apache-2.0
 * License-Filename: LICENSE
 */

/* eslint-disable */
/* We should only include in the WebApp report the
 * license summaries for the licenses found in the code.
 * Code analysis and scanning is handled by ORT's Kotlin code
 * so disabling ESlint as functionality included in this file
 * will be moved to the Kotlin code in the near future.
 */

import RULES from './json/rules.json';
import AFL_3_0 from './json/AFL-3.0';
import AGPL_3_0 from './json/AGPL-3.0';
import Apache_2_0 from './json/Apache-2.0';
import Artistic_2_0 from './json/Artistic-2.0';
import BSD_2_Clause from './json/BSD-2-Clause';
import BSD_3_Clause from './json/BSD-3-Clause';
import BSL_1_0 from './json/BSL-1.0';
import CC_BY_4_0 from './json/CC-BY-4.0';
import CC_BY_SA_4_0 from './json/CC-BY-SA-4.0';
import CC0_1_0 from './json/CC0-1.0';
import ECL_2_0 from './json/ECL-2.0';
import EPL_1_0 from './json/EPL-1.0';
import EPL_2_0 from './json/EPL-2.0';
import EUPL_1_1 from './json/EUPL-1.1';
import EUPL_1_2 from './json/EUPL-1.2';
import GPL_2_0 from './json/GPL-2.0';
import GPL_3_0 from './json/GPL-3.0';
import ISC from './json/ISC';
import LGPL_2_1 from './json/LGPL-2.1';
import LGPL_3_0 from './json/LGPL-3.0';
import LPPL_1_3c from './json/LPPL-1.3c';
import MIT from './json/MIT';
import MPL_2_0 from './json/MPL-2.0';
import MS_PL from './json/MS-PL';
import MS_RL from './json/MS-RL';
import NCSA from './json/NCSA';
import OFL_1_1 from './json/OFL-1.1';
import OSL_3_0 from './json/OSL-3.0';
import PostgreSQL from './json/PostgreSQL';
import Unlicense from './json/Unlicense';
import UPL_1_0 from './json/UPL-1.0';
import WTFPL from './json/WTFPL';
import Zlib from './json/Zlib';

export const metadata = {
    packageName: 'choosealicense.com',
    packageCopyrightText: 'GitHub, Inc. and contributors',
    packageComment: 'We are not lawyers. Well, most of us anyway. It is not the goal of this site to provide legal advice. The goal of choosealicense.com is to provide a starting point to help you make an informed choice by providing information on popular open source licenses. If you have any questions regarding the right license for your code or any other legal issues relating to it, it’s up to you to do further research or consult with a professional.',
    packageDescription: 'GitHub wants to help developers choose an open source license for their source code.',
    packageDownloadLocation: 'git+https://github.com/github/choosealicense.com.git@a4311ad861a40d10be2fce0c1db284d26c95f6a5',
    packageHomePage: 'https://choosealicense.com/',
    packageLicenseDeclared: 'CC-BY-3.0',
    packageSupplier: 'GitHub, Inc.',
    packageVersion: 'a4311ad861a40d10be2fce0c1db284d26c95f6a5'
};

export const data = (() => {
    const createLicenseWithProxies = (license) => {
        const legend = (() => {
            const rules = {};

            // Convert rules which is the licens esummary legenda
            // from array to object for easier and faster lookups
            Object.entries(RULES).forEach(([key, value]) => {
                rules[key] = {};

                value.forEach((obj) => {
                    rules[key][obj.tag] = obj;
                });
            });

            return rules;
        })();
        /* Using ES6 Proxy to create Object which includes properties
         * with extended arrays of strings.
         * In these extended string arrays you access an array item based on its value and
         * if you do so not a string but object with additional information will be returned.
         *
         * Run choosealicense.data['Apache-2.0'].conditions in console will return:
         * Proxy {0: "include-copyright", 1: "document-changes", length: 2}
         *
         * Run choosealicense.data['Apache-2.0'].conditions['include-copyright']
         * in console will return:
         * {
         *     description: "A copy of the license and copyright notice must be included...",
         *     label: "License and copyright notice",
         *     tag: "include-copyright"
         * }
         *
         * For more details on ES6 proxy please see
         * https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy
         */
        const applyProxy = (type, obj) => new Proxy(
            obj,
            {
                get(obj, prop) {
                    // By default behavior return the value; prop is usually an integer
                    if (prop in obj) {
                        return obj[prop];
                    }

                    if (legend[type][prop]) {
                        return legend[type][prop];
                    }

                    return undefined;
                }
            }
        );

        license.summary = [];

        // Convert summary tag field for a license into ES6 proxy
        // Allows for property lookup of summary tag 'include-copyright'
        // into Choosealicense's legend. See also example above 'applyProxy'.
        if (license.permissions) {
            license.permissions = applyProxy('permissions', license.permissions);
            license.summary.push({
                key: 'permissions',
                title: 'Permissions',
                color: 'green',
                provider: 'choosealicense',
                tags: ((tags = license.permissions) => tags.map((tag) => {
                    if (legend.permissions[tag]) {
                        return legend.permissions[tag];
                    }

                    return undefined;
                }))()
            });
        }

        if (license.conditions) {
            license.conditions = applyProxy('conditions', license.conditions);
            license.summary.push({
                key: 'conditions',
                title: 'Conditions',
                color: 'orange',
                provider: 'choosealicense',
                tags: ((tags = license.conditions) => tags.map((tag) => {
                    if (legend.conditions[tag]) {
                        return legend.conditions[tag];
                    }

                    return undefined;
                }))()
            });
        }

        if (license.limitations) {
            license.limitations = applyProxy('limitations', license.limitations);
            license.summary.push({
                key: 'limitations',
                title: 'Limitations',
                color: 'red',
                provider: 'choosealicense',
                tags: ((tags = license.limitations) => tags.map((tag) => {
                    if (legend.limitations[tag]) {
                        return legend.limitations[tag];
                    }

                    return undefined;
                }))()
            });
        }

        // Swap title prop for name as this is more commonly used in other datasets
        if (license.title) {
            license.name = license.title;
            delete license.title;
        }

        // Remove props included solely for use in choosealicense.com
        if (license.featured) {
            delete license.featured;
        }

        if (license.hidden) {
            delete license.hidden;
        }

        if (license.redirect_from) {
            delete license.redirect_from;
        }

        return license;
    };

    return {
        ...metadata,
        data: {
            'AFL-3.0': createLicenseWithProxies(AFL_3_0),
            'AGPL-3.0': createLicenseWithProxies(AGPL_3_0),
            'AGPL-3.0-only': createLicenseWithProxies(AGPL_3_0),
            'AGPL-3.0-or-later': createLicenseWithProxies(AGPL_3_0),
            'Apache-2.0': createLicenseWithProxies(Apache_2_0),
            'Artistic-2.0': createLicenseWithProxies(Artistic_2_0),
            'BSD-2-Clause': createLicenseWithProxies(BSD_2_Clause),
            'BSD-3-Clause': createLicenseWithProxies(BSD_3_Clause),
            'BSL-1.0': createLicenseWithProxies(BSL_1_0),
            'CC-BY-4.0': createLicenseWithProxies(CC_BY_4_0),
            'CC-BY-SA-4.0': createLicenseWithProxies(CC_BY_SA_4_0),
            'CC0-1.0': createLicenseWithProxies(CC0_1_0),
            'ECL-2.0': createLicenseWithProxies(ECL_2_0),
            'EPL-1.0': createLicenseWithProxies(EPL_1_0),
            'EPL-2.0': createLicenseWithProxies(EPL_2_0),
            'EUPL-1.1': createLicenseWithProxies(EUPL_1_1),
            'EUPL-1.2': createLicenseWithProxies(EUPL_1_2),
            'GPL-2.0': createLicenseWithProxies(GPL_2_0),
            'GPL-2.0-only': createLicenseWithProxies(GPL_2_0),
            'GPL-2.0-or-later': createLicenseWithProxies(GPL_2_0),
            'GPL-3.0': createLicenseWithProxies(GPL_3_0),
            'GPL-3.0-only': createLicenseWithProxies(GPL_3_0),
            'GPL-3.0-or-later': createLicenseWithProxies(GPL_3_0),
            ISC: createLicenseWithProxies(ISC),
            'LGPL-2.1': createLicenseWithProxies(LGPL_2_1),
            'LGPL-2.1-only': createLicenseWithProxies(LGPL_2_1),
            'LGPL-2.1-or-later': createLicenseWithProxies(LGPL_2_1),
            'LGPL-3.0': createLicenseWithProxies(LGPL_3_0),
            'LGPL-3.0-only': createLicenseWithProxies(LGPL_3_0),
            'LGPL-3.0-or-later': createLicenseWithProxies(LGPL_3_0),
            'LPPL-1.3c': createLicenseWithProxies(LPPL_1_3c),
            MIT: createLicenseWithProxies(MIT),
            'MPL-2.0': createLicenseWithProxies(MPL_2_0),
            'MS-PL': createLicenseWithProxies(MS_PL),
            'MS-RL': createLicenseWithProxies(MS_RL),
            NCSA: createLicenseWithProxies(NCSA),
            'OFL-1.1': createLicenseWithProxies(OFL_1_1),
            'OSL-3.0': createLicenseWithProxies(OSL_3_0),
            PostgreSQL: createLicenseWithProxies(PostgreSQL),
            Unlicense: createLicenseWithProxies(Unlicense),
            'UPL-1.0': createLicenseWithProxies(UPL_1_0),
            WTFPL: createLicenseWithProxies(WTFPL),
            Zlib: createLicenseWithProxies(Zlib)
        },
        type: 'license-summary'
    };
})();
