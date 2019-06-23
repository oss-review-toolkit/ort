/*
 * Copyright (C) 2019 HERE Europe B.V.
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

import ScanResult from './ScanResult';

class ScanResultContainer {
    #detectedLicenses;

    #id;

    #results = [];

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.id) {
                this.#id = obj.id;
            }

            if (obj.results) {
                for (let i = 0, len = obj.results.length; i < len; i++) {
                    this.#results.push(new ScanResult(obj.results[i]));
                }
            }
        }
    }

    get id() {
        return this.#id;
    }

    get results() {
        return this.#results;
    }

    getAllDetectedLicenses() {
        if (!this.#detectedLicenses) {
            this.#detectedLicenses = [];

            for (let i = this.results.length - 1; i >= 0; i -= 1) {
                const { summary: { licenses } } = this.results[i];
                this.#detectedLicenses.push(Array.from(licenses));
            }

            this.#detectedLicenses = new Set(this.#detectedLicenses.flat());
        }

        return this.#detectedLicenses;
    }
}

export default ScanResultContainer;
