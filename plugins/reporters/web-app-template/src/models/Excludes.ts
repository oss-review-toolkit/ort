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

import WebAppPathExclude from "@/models/WebAppPathExclude";
import WebAppScopeExclude from "@/models/WebAppScopeExclude";
import type { EvaluatedModelExcludes } from "@/types/evaluatedModelData";

class Excludes {
    #paths: WebAppPathExclude[] = [];

    #scopes: WebAppScopeExclude[] = [];

    constructor(obj?: EvaluatedModelExcludes) {
        if (obj) {
            if (obj.paths) {
                for (let i = 0, len = obj.paths.length; i < len; i++) {
                    const raw = obj.paths[i];
                    if (raw) {
                        this.#paths.push(new WebAppPathExclude(raw));
                    }
                }
            }

            if (obj.scopes) {
                for (let i = 0, len = obj.scopes.length; i < len; i++) {
                    const raw = obj.scopes[i];
                    if (raw) {
                        this.#scopes.push(new WebAppScopeExclude(raw));
                    }
                }
            }
        }
    }

    get paths(): readonly WebAppPathExclude[] {
        return this.#paths;
    }

    get scopes(): readonly WebAppScopeExclude[] {
        return this.#scopes;
    }
}

export default Excludes;
