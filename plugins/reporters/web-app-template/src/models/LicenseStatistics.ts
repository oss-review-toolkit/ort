/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

import type { EvaluatedModelLicenseStatistics } from "@/types/evaluatedModelData";

class LicenseStatistics {
    #declared = new Map<string, number>();

    #detected = new Map<string, number>();

    #effective = new Map<string, number>();

    constructor(obj?: EvaluatedModelLicenseStatistics) {
        if (obj) {
            if (obj.declared) {
                Object.entries(obj.declared).forEach(([name, nrPackages]) => {
                    this.#declared.set(name, nrPackages);
                });
            }

            if (obj.detected) {
                Object.entries(obj.detected).forEach(([name, nrPackages]) => {
                    this.#detected.set(name, nrPackages);
                });
            }

            if (obj.effective) {
                Object.entries(obj.effective).forEach(([name, nrPackages]) => {
                    this.#effective.set(name, nrPackages);
                });
            }
        }
    }

    get declared(): ReadonlyMap<string, number> {
        return this.#declared;
    }

    get detected(): ReadonlyMap<string, number> {
        return this.#detected;
    }

    get effective(): ReadonlyMap<string, number> {
        return this.#effective;
    }
}

export default LicenseStatistics;
