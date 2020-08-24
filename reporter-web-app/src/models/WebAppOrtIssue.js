/*
 * Copyright (C) 2019-2020 HERE Europe B.V.
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

class WebAppOrtIssue {
    #_id;

    #howToFix

    #message;

    #package;

    #packageIndex;

    #path;

    #scanResult;

    #scanResultIndex;

    #severity;

    #source;

    #timestamp;

    #type;

    #resolutionIndexes = new Set();

    #resolutionReasons;

    #resolutions;

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

            if (obj.message) {
                this.#message = obj.message;
            }

            if (obj.path) {
                this.#path = obj.path;
            }

            if (Number.isInteger(obj.pkg)) {
                this.#packageIndex = obj.pkg;
            }

            if (Number.isInteger(obj.scan_result) || Number.isInteger(obj.scanResult)) {
                this.#scanResultIndex = obj.scan_result || obj.scanResult;
            }

            if (obj.severity) {
                this.#severity = obj.severity;
            }

            if (obj.source) {
                this.#source = obj.source;
            }

            if (obj.timestamp) {
                this.#timestamp = obj.timestamp;
            }

            if (obj.type) {
                this.#type = obj.type;
            }

            if (obj.resolutions) {
                this.#resolutionIndexes = new Set(obj.resolutions);
            }

            if (webAppOrtResult) {
                this.#webAppOrtResult = webAppOrtResult;

                const webAppPackage = webAppOrtResult.getPackageByIndex(this.#packageIndex);
                if (webAppPackage) {
                    this.#package = webAppPackage;
                }

                const webAppScanResult = webAppOrtResult.getScanResultByIndex(this.#packageIndex);
                if (webAppScanResult) {
                    this.#scanResult = webAppScanResult;
                }
            }

            this.key = randomStringGenerator(20);
        }
    }

    get _id() {
        return this.#_id;
    }

    get howToFix() {
        return this.#howToFix;
    }

    get isResolved() {
        if (this.#resolutionIndexes && this.#resolutionIndexes.size > 0) {
            return true;
        }

        return false;
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

    get path() {
        return this.#path;
    }

    get scanResult() {
        return this.#scanResult;
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

    get source() {
        return this.#source;
    }

    get timestamp() {
        return this.#timestamp;
    }

    get type() {
        return this.#type;
    }

    get resolutionIndexes() {
        return this.#resolutionIndexes;
    }

    get resolutions() {
        if (!this.#resolutions && this.#webAppOrtResult) {
            this.#resolutions = [];
            this.#resolutionIndexes.forEach((index) => {
                const webAppResolution = this.#webAppOrtResult.getIssueResolutionByIndex(index) || null;
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
                const webAppResolution = this.#webAppOrtResult.getIssueResolutionByIndex(index) || null;
                if (webAppResolution && webAppResolution.reason) {
                    this.#resolutionReasons.add(webAppResolution.reason);
                }
            });
        }

        return this.#resolutionReasons;
    }

    hasHowToFix() {
        return !!this.#howToFix;
    }
}

export default WebAppOrtIssue;
