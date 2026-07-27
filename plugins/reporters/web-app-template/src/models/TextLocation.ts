/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import type { EvaluatedModelTextLocation } from "@/types/evaluatedModelData";

class TextLocation {
    #endLine: number | undefined;

    #path: string | undefined;

    #startLine: number | undefined;

    constructor(obj?: EvaluatedModelTextLocation) {
        if (obj) {
            if (Number.isInteger(obj.end_line) || Number.isInteger(obj.endLine)) {
                this.#endLine = obj.end_line ?? obj.endLine;
            }

            if (obj.path) {
                this.#path = obj.path;
            }

            if (Number.isInteger(obj.start_line) || Number.isInteger(obj.startLine)) {
                this.#startLine = obj.start_line ?? obj.startLine;
            }
        }
    }

    get endLine(): number | undefined {
        return this.#endLine;
    }

    get path(): string | undefined {
        return this.#path;
    }

    get startLine(): number | undefined {
        return this.#startLine;
    }
}

export default TextLocation;
