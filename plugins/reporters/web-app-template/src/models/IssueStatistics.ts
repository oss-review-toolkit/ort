/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import type { EvaluatedModelIssueStatistics } from "@/types/evaluatedModelData";

class IssueStatistics {
    #errors: number = 0;

    #hints: number = 0;

    #warnings: number = 0;

    constructor(obj?: EvaluatedModelIssueStatistics) {
        if (obj) {
            if (obj.errors !== null && obj.errors !== undefined) {
                this.#errors = obj.errors;
            }

            if (obj.hints !== null && obj.hints !== undefined) {
                this.#hints = obj.hints;
            }

            if (obj.warnings !== null && obj.warnings !== undefined) {
                this.#warnings = obj.warnings;
            }
        }
    }

    get errors(): number {
        return this.#errors;
    }

    get hints(): number {
        return this.#hints;
    }

    get warnings(): number {
        return this.#warnings;
    }

    get total(): number {
        return this.#errors + this.#hints + this.#warnings;
    }
}

export default IssueStatistics;
