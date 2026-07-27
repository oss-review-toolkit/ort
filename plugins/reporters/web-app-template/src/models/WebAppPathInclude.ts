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

import type { EvaluatedModelPathInclude } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

class WebAppPathInclude {
    #_id: number | undefined;

    #comment: string | undefined;

    #pattern: string | undefined;

    #reason: string | undefined;

    key: string;

    constructor(obj?: EvaluatedModelPathInclude) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.comment) {
                this.#comment = obj.comment;
            }

            if (obj.pattern) {
                this.#pattern = obj.pattern;
            }

            if (obj.reason) {
                this.#reason = obj.reason;
            }
        }

        this.key = randomStringGenerator(20);
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get comment(): string | undefined {
        return this.#comment;
    }

    get pattern(): string | undefined {
        return this.#pattern;
    }

    get reason(): string | undefined {
        return this.#reason;
    }
}

export default WebAppPathInclude;
