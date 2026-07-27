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

import type { EvaluatedModelChoice } from "@/types/evaluatedModelData";

class Choice {
    #comment: string | undefined;

    #purl: string | undefined;

    #reason: string | undefined;

    constructor(obj?: EvaluatedModelChoice) {
        if (obj) {
            if (obj.comment) {
                this.#comment = obj.comment;
            }

            if (obj.purl) {
                this.#purl = obj.purl;
            }

            if (obj.reason) {
                this.#reason = obj.reason;
            }
        }
    }

    get comment(): string | undefined {
        return this.#comment;
    }

    get purl(): string | undefined {
        return this.#purl;
    }

    get reason(): string | undefined {
        return this.#reason;
    }
}

export default Choice;
