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

/* eslint constructor-super: 0 */

import TextLocation from './TextLocation';
import { randomStringGenerator } from '../utils';

class WebAppScanFinding extends TextLocation {
    #license;

    #key;

    #provenance;

    #scanner;

    constructor(obj) {
        if (obj) {
            super(obj);

            if (obj.license) {
                this.#license = obj.license;
            }

            if (obj.provenance) {
                this.#provenance = obj.provenance;
            }

            if (obj.scanner) {
                this.#scanner = obj.scanner;
            }

            this.#key = randomStringGenerator(20);
        }
    }

    get key() {
        return this.#key;
    }

    get license() {
        return this.#license;
    }

    get provenance() {
        return this.#provenance;
    }

    get scanner() {
        return this.#scanner;
    }
}

export default WebAppScanFinding;
