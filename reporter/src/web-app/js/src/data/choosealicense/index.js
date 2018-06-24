/*
 * Copyright (c) 2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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

import * as RULES from './json/rules.json';
import * as AFL_3_0 from './json/AFL-3.0';
import * as AGPL_3_0 from './json/AGPL-3.0';
import * as Apache_2_0 from './json/Apache-2.0';
import * as Artistic_2_0 from './json/Artistic-2.0';
import * as BSD_2_Clause from './json/BSD-2-Clause';
import * as BSD_3_Clause from './json/BSD-3-Clause';
import * as BSL_1_0 from './json/BSL-1.0';
import * as CC_BY_4_0 from './json/CC-BY-4.0';
import * as CC_BY_SA_4_0 from './json/CC-BY-SA-4.0';
import * as CC0_1_0 from './json/CC0-1.0';
import * as ECL_2_0 from './json/ECL-2.0';
import * as EPL_1_0 from './json/EPL-1.0';
import * as EPL_2_0 from './json/EPL-2.0';
import * as EUPL_1_1 from './json/EUPL-1.1';
import * as EUPL_1_2 from './json/EUPL-1.2';
import * as GPL_2_0 from './json/GPL-2.0';
import * as GPL_3_0 from './json/GPL-3.0';
import * as ISC from './json/ISC';
import * as LGPL_2_1 from './json/LGPL-2.1';
import * as LGPL_3_0 from './json/LGPL-3.0';
import * as LPPL_1_3c from './json/LPPL-1.3c';
import * as MIT from './json/MIT';
import * as MPL_2_0 from './json/MPL-2.0';
import * as MS_PL from './json/MS-PL';
import * as MS_RL from './json/MS-RL';
import * as NCSA from './json/NCSA';
import * as OFL_1_1 from './json/OFL-1.1';
import * as OSL_3_0 from './json/OSL-3.0';
import * as PostgreSQL from './json/PostgreSQL';
import * as Unlicense from './json/Unlicense';
import * as UPL_1_0 from './json/UPL-1.0';
import * as WTFPL from './json/WTFPL';
import * as Zlib from './json/Zlib';

export const choosealicense = (() => {
    let createLicenseWithProxies = (license) => {
      let legend = window.legend = (() => {
        let rules = {};

        Object.entries(RULES).forEach(([key, value]) => {
          rules[key] = {};

          value.forEach((obj) => {
            rules[key][obj.tag] = obj;
          });
        });

        return rules;
      })(),
     /* Using ES6 Proxy to create Object to extend arrays so array item object can by accessed by its property.
      *
      * Run choosealicense.data['Apache-2.0'].conditions in console will return:
      * Proxy {0: "include-copyright", 1: "document-changes", length: 2}
      *
      * Run choosealicense.data['Apache-2.0'].conditions['include-copyright'] in console will return:
      * {
      *   description: "A copy of the license and copyright notice must be included with the software.",
      *   label: "License and copyright notice",
      *   tag: "include-copyright"
      *.}
      *
      * For more details on ES6 proxy please see https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/Proxy
      */
      applyProxy = (type, obj) => {
        return new Proxy(
          obj,
          {
            get: function (obj, prop) {
              // By default behavior return the value; prop is usually an integer
              if (prop in obj) {
                return obj[prop];
              }

              if (legend[type].hasOwnProperty(prop)) {
                return legend[type][prop];
              }

              return undefined;
            }
          }
        );
      }

      if (license.hasOwnProperty('conditions')) {
        license.conditions = applyProxy('conditions', license.conditions);
      }

      if (license.hasOwnProperty('limitations')) {
        license.permissions = applyProxy('limitations', license.permissions);
      }

      if (license.hasOwnProperty('permissions')) {
        license.limitations = applyProxy('permissions', license.limitations);
      }

      return license;
    };

  return {
    packageName: 'choosealicense.com',
    packageCopyrightText: 'GitHub, Inc. and contributors',
    packageComment: 'We are not lawyers. Well, most of us anyway. It is not the goal of this site to provide legal advice. The goal of choosealicense.com is to provide a starting point to help you make an informed choice by providing information on popular open source licenses. If you have any questions regarding the right license for your code or any other legal issues relating to it, it’s up to you to do further research or consult with a professional.',
    packageDescription: 'GitHub wants to help developers choose an open source license for their source code.',
    packageDownloadLocation: 'git+https://github.com/github/choosealicense.com.git@a4311ad861a40d10be2fce0c1db284d26c95f6a5',
    packageHomePage: 'https://choosealicense.com/',
    packageLicenseDeclared: 'CC-BY-3.0',
    packageSupplier: 'GitHub, Inc.',
    packageVersion: 'a4311ad861a40d10be2fce0c1db284d26c95f6a5',
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
      'EUPL-1.2': createLicenseWithProxies( EUPL_1_2),
      'GPL-2.0': createLicenseWithProxies(GPL_2_0),
      'GPL-2.0-only': createLicenseWithProxies(GPL_2_0),
      'GPL-2.0-or-later': createLicenseWithProxies(GPL_2_0),
      'GPL-3.0': createLicenseWithProxies(GPL_3_0),
      'GPL-3.0-only': createLicenseWithProxies(GPL_3_0),
      'GPL-3.0-or-later': createLicenseWithProxies(GPL_3_0),
      'ISC': createLicenseWithProxies(ISC),
      'LGPL-2.1': createLicenseWithProxies(LGPL_2_1),
      'LGPL-2.1-only': createLicenseWithProxies(LGPL_2_1),
      'LGPL-2.1-or-later': createLicenseWithProxies(LGPL_2_1),
      'LGPL-3.0': createLicenseWithProxies(LGPL_3_0),
      'LGPL-3.0-only': createLicenseWithProxies(LGPL_3_0),
      'LGPL-3.0-or-later': createLicenseWithProxies(LGPL_3_0),
      'LPPL-1.3c': createLicenseWithProxies(LPPL_1_3c),
      'MIT': createLicenseWithProxies(MIT),
      'MPL-2.0': createLicenseWithProxies(MPL_2_0),
      'MS-PL': createLicenseWithProxies(MS_PL),
      'MS-RL': createLicenseWithProxies(MS_RL),
      'NCSA': createLicenseWithProxies(NCSA),
      'OFL-1.1': createLicenseWithProxies(OFL_1_1),
      'OSL-3.0': createLicenseWithProxies(OSL_3_0),
      'PostgreSQL': createLicenseWithProxies(PostgreSQL),
      'Unlicense': createLicenseWithProxies(Unlicense),
      'UPL-1.0': createLicenseWithProxies(UPL_1_0),
      'WTFPL': createLicenseWithProxies(WTFPL),
      'Zlib': createLicenseWithProxies(Zlib)
    },
    type: 'license-summary'
  };
})();