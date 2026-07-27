/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import Provenance from "@/models/Provenance";
import SnippetChoice from "@/models/SnippetChoice";
import type { EvaluatedModelWebAppSnippetChoices } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

class WebAppSnippetChoices {
    #_id: number | undefined;

    #choices: SnippetChoice[] = [];

    #provenance: Provenance | undefined;

    key: string;

    constructor(obj?: EvaluatedModelWebAppSnippetChoices) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.choices) {
                for (let i = 0, len = obj.choices.length; i < len; i++) {
                    const raw = obj.choices[i];
                    if (raw) {
                        this.#choices.push(new SnippetChoice(raw));
                    }
                }
            }

            if (obj.provenance) {
                this.#provenance = new Provenance(obj.provenance);
            }
        }

        this.key = randomStringGenerator(20);
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get choices(): readonly SnippetChoice[] {
        return this.#choices;
    }

    get provenance(): Provenance | undefined {
        return this.#provenance;
    }
}

export default WebAppSnippetChoices;
