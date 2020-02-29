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

class LicenseStatistics {
    #declared = new Map();

    #detected = new Map();

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.declared !== null && obj.declared instanceof Object) {
                Object.entries(obj.declared).forEach(
                    ([name, nrPackages]) => this.#declared.set(name, nrPackages)
                );
            }

            if (obj.detected !== null && obj.detected instanceof Object) {
                Object.entries(obj.detected).forEach(
                    ([name, nrPackages]) => this.#detected.set(name, nrPackages)
                );
            }
        }
    }

    get declared() {
        return this.#declared;
    }

    get detected() {
        return this.#detected;
    }
}

export default LicenseStatistics;
