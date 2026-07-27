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

import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import type WebAppScopeExclude from "@/models/WebAppScopeExclude";
import type { EvaluatedModelScope } from "@/types/evaluatedModelData";

class WebAppScope {
    #_id: number | undefined;

    #excludeIndexes: Set<number> | undefined;

    #excludes: WebAppScopeExclude[] | undefined;

    #isExcluded: boolean = false;

    #name: string | undefined;

    #webAppEvaluatedModel: WebAppEvaluatedModel | undefined;

    constructor(obj?: EvaluatedModelScope, webAppEvaluatedModel?: WebAppEvaluatedModel) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.name) {
                this.#name = obj.name;
            }

            // A present-but-empty excludes array must not mark the scope excluded ([] is truthy), matching
            // WebAppFinding's guard.
            if (Array.isArray(obj.excludes) && obj.excludes.length > 0) {
                this.#excludeIndexes = new Set(obj.excludes);
                this.#isExcluded = true;
            }

            if (webAppEvaluatedModel) {
                this.#webAppEvaluatedModel = webAppEvaluatedModel;
            }
        }
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get excludes(): WebAppScopeExclude[] | undefined {
        if (!this.#excludes && this.#webAppEvaluatedModel && this.#excludeIndexes) {
            this.#excludes = [];
            this.#excludeIndexes.forEach((index) => {
                const webAppScopeExclude = this.#webAppEvaluatedModel?.getScopeExcludeByIndex(index) || null;
                if (webAppScopeExclude) {
                    this.#excludes?.push(webAppScopeExclude);
                }
            });
        }

        return this.#excludes;
    }

    get excludeIndexes(): ReadonlySet<number> | undefined {
        return this.#excludeIndexes;
    }

    get id(): number | undefined {
        return this.#_id;
    }

    get isExcluded(): boolean {
        return this.#isExcluded;
    }

    get name(): string | undefined {
        return this.#name;
    }
}

export default WebAppScope;
