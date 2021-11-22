/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

import { randomStringGenerator } from '../utils';

class WebAppRuleViolation {
    #_id;

    #howToFix;

    #license;

    #licenseIndex;

    #licenseSource;

    #message;

    #package;

    #packageIndex;

    #severity;

    #resolutionIndexes = new Set();

    #resolutionReasons;

    #resolutions;

    #rule;

    #webAppOrtResult;

    constructor(obj, webAppOrtResult) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.how_to_fix || obj.howToFix) {
                this.#howToFix = obj.how_to_fix
                    || obj.howToFix;
            }

            if (obj.license) {
                this.#licenseIndex = obj.license;
            }

            if (obj.license_source || obj.licenseSource) {
                this.#licenseSource = obj.license_source
                    || obj.licenseSource;
            }

            if (obj.message) {
                this.#message = obj.message;
            }

            if (Number.isInteger(obj.pkg)) {
                this.#packageIndex = obj.pkg;
            } else {
                this.#packageIndex = -1;
            }

            if (obj.severity) {
                this.#severity = obj.severity;
            }

            if (obj.resolutions) {
                this.#resolutionIndexes = new Set(obj.resolutions);
            }

            if (obj.rule) {
                this.#rule = obj.rule;
            }

            if (webAppOrtResult) {
                this.#webAppOrtResult = webAppOrtResult;

                const webAppPackage = webAppOrtResult.getPackageByIndex(this.#packageIndex);
                if (webAppPackage) {
                    this.#package = webAppPackage;
                }

                const webAppLicense = webAppOrtResult.getLicenseByIndex(this.#licenseIndex);
                if (webAppLicense) {
                    this.#license = webAppLicense;
                }
            }

            this.key = randomStringGenerator(20);
        }
    }

    get _id() {
        return this.#_id;
    }

    get isResolved() {
        if (this.#resolutionIndexes && this.#resolutionIndexes.size > 0) {
            return true;
        }

        return false;
    }

    get howToFix() {
        return this.#howToFix;
    }

    get license() {
        return this.#license;
    }

    get licenseName() {
        return this.#license.id;
    }

    get message() {
        return this.#message;
    }

    get package() {
        return this.#package;
    }

    get packageIndex() {
        return this.#packageIndex;
    }

    get packageName() {
        return this.#package ? this.#package.id : '';
    }

    get resolutionIndexes() {
        return this.#resolutionIndexes;
    }

    get resolutions() {
        if (!this.#resolutions && this.#webAppOrtResult) {
            this.#resolutions = [];
            this.#resolutionIndexes.forEach((index) => {
                const webAppResolution = this.#webAppOrtResult.getRuleViolationResolutionByIndex(index) || null;
                if (webAppResolution) {
                    this.#resolutions.push(webAppResolution);
                }
            });
        }

        return this.#resolutions;
    }

    get resolutionReasons() {
        if (!this.#resolutionReasons && this.#webAppOrtResult) {
            this.#resolutionReasons = new Set();
            this.#resolutionIndexes.forEach((index) => {
                const webAppResolution = this.#webAppOrtResult.getRuleViolationResolutionByIndex(index) || null;
                if (webAppResolution && webAppResolution.reason) {
                    this.#resolutionReasons.add(webAppResolution.reason);
                }
            });
        }

        return this.#resolutionReasons;
    }

    get rule() {
        return this.#rule;
    }

    get severity() {
        return this.#severity;
    }

    get severityIndex() {
        if (this.isResolved) {
            return 3;
        }

        if (this.#severity === 'ERROR') {
            return 0;
        }

        if (this.#severity === 'WARNING') {
            return 1;
        }

        if (this.#severity === 'HINT') {
            return 2;
        }

        return -1;
    }

    hasHowToFix() {
        return !!this.#howToFix;
    }

    hasPackage() {
        return !!this.#package;
    }
}

export default WebAppRuleViolation;
