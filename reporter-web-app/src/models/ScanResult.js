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

import Provenance from './Provenance';
import ScannerDetails from './ScannerDetails';
import ScanSummary from './ScanSummary';

class ScanResult {
    #provenance;

    #scanner;

    #summary;

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.provenance) {
                this.#provenance = new Provenance(obj.provenance);
            }

            if (obj.scanner) {
                this.#scanner = new ScannerDetails(obj.scanner);
            }

            if (obj.summary) {
                this.#summary = new ScanSummary(obj.summary);
            }
        }
    }

    get provenance() {
        return this.#provenance;
    }

    get scanner() {
        return this.#scanner;
    }

    get summary() {
        return this.#summary;
    }
}

export default ScanResult;
