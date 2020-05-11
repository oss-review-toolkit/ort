/*
 * Copyright (C) 2020 HERE Europe B.V.
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

class WebAppFinding {
    #copyright;

    #endLine;

    #isExcluded = false;

    #license;

    #path;

    #pathExcludes;

    #pathExcludeIndexes = new Set();

    #pathExcludeReasons;

    #startLine;

    #scanResult;

    #type;

    #webAppOrtResult;

    constructor(obj, webAppOrtResult) {
        if (obj) {
            if (Number.isInteger(obj.copyright)) {
                this.#copyright = obj.copyright;
            }

            if (Number.isInteger(obj.end_line) || Number.isInteger(obj.endLine)) {
                this.#endLine = obj.end_line || obj.endLine;
            }

            if (Number.isInteger(obj.license)) {
                this.#license = obj.license;
            }

            if (obj.path !== null) {
                this.#path = obj.path;
            }

            const pathExcludes = obj.path_excludes || obj.pathExcludes;
            if (Array.isArray(pathExcludes) && pathExcludes.length > 0) {
                this.#pathExcludeIndexes = new Set(pathExcludes);
                this.#isExcluded = true;
            }

            if (Number.isInteger(obj.start_line) || Number.isInteger(obj.startLine)) {
                this.#startLine = obj.start_line || obj.startLine;
            }

            if (Number.isInteger(obj.scan_result) || Number.isInteger(obj.scanResult)) {
                this.#scanResult = obj.scan_result || obj.scanResult;
            }

            if (obj.type !== null) {
                this.#type = obj.type;
            }

            if (webAppOrtResult !== null) {
                this.#webAppOrtResult = webAppOrtResult;
            }

            this.key = randomStringGenerator(20);
        }
    }

    get copyright() {
        if (this.#webAppOrtResult) {
            const webAppFinding = this.#webAppOrtResult.getCopyrightByIndex(this.#copyright);
            if (webAppFinding) {
                return webAppFinding.statement;
            }
        }

        return null;
    }

    get endLine() {
        return this.#endLine;
    }

    get isExcluded() {
        return this.#isExcluded;
    }

    get license() {
        if (this.#webAppOrtResult) {
            const webAppFinding = this.#webAppOrtResult.getLicenseByIndex(this.#license);
            if (webAppFinding) {
                return webAppFinding.id;
            }
        }

        return null;
    }

    get path() {
        return this.#path;
    }

    get pathExcludes() {
        if (!this.#pathExcludes && this.#webAppOrtResult) {
            this.#pathExcludes = [];
            this.#pathExcludeIndexes.forEach((index) => {
                const webAppPathExclude = this.#webAppOrtResult.getPathExcludeByIndex(index) || null;
                if (webAppPathExclude) {
                    this.#pathExcludes.push(webAppPathExclude);
                }
            });
        }

        return this.#pathExcludes;
    }

    get pathExcludeIndexes() {
        return this.#pathExcludeIndexes;
    }

    get pathExcludeReasons() {
        if (!this.#pathExcludeReasons && this.#webAppOrtResult) {
            this.#pathExcludeReasons = new Set();

            this.#pathExcludeIndexes.forEach((index) => {
                const webAppPathExclude = this.#webAppOrtResult.getPathExcludeByIndex(index) || null;
                if (webAppPathExclude && webAppPathExclude.reason) {
                    this.#pathExcludeReasons.add(webAppPathExclude.reason);
                }
            });
        }

        return this.#pathExcludeReasons;
    }

    get startLine() {
        return this.#startLine;
    }

    get scanResult() {
        if (this.#webAppOrtResult) {
            return this.#webAppOrtResult.getScanResultByIndex(this.#scanResult);
        }

        return null;
    }

    get type() {
        return this.#type;
    }

    get value() {
        if (this.#type === 'COPYRIGHT') {
            return this.copyright;
        }

        return this.license;
    }
}

export default WebAppFinding;
