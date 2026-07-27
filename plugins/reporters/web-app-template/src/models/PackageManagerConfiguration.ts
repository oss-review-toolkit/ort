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

import type { EvaluatedModelPackageManagerConfiguration } from "@/types/evaluatedModelData";

class PackageManagerConfiguration {
    #mustRunAfter: readonly string[] | undefined;

    #options: Record<string, string> | undefined;

    constructor(obj?: EvaluatedModelPackageManagerConfiguration) {
        if (obj) {
            if (obj.must_run_after || obj.mustRunAfter) {
                this.#mustRunAfter = obj.must_run_after || obj.mustRunAfter;
            }

            if (obj.options) {
                this.#options = obj.options;
            }
        }
    }

    get mustRunAfter(): readonly string[] | undefined {
        return this.#mustRunAfter;
    }

    get options(): Record<string, string> | undefined {
        return this.#options;
    }
}

export default PackageManagerConfiguration;
