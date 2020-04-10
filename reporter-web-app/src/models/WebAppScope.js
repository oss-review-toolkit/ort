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

class WebAppScope {
    #_id;

    #excludeIndexes;

    #excludes;

    #isExcluded = false;

    #name;

    #webAppOrtResult;

    constructor(obj, webAppOrtResult) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.name) {
                this.#name = obj.name;
            }

            if (obj.excludes) {
                this.#excludeIndexes = new Set(obj.excludes);
                this.#isExcluded = true;
            }

            if (webAppOrtResult) {
                this.#webAppOrtResult = webAppOrtResult;
            }
        }
    }

    get _id() {
        return this.#_id;
    }

    get excludes() {
        if (!this.#excludes && this.#webAppOrtResult) {
            this.#excludes = [];
            this.#excludeIndexes.forEach((index) => {
                const webAppScopeExclude = this.#webAppOrtResult.getScopeExcludeByIndex(index) || null;
                if (webAppScopeExclude) {
                    this.#excludes.push(webAppScopeExclude);
                }
            });
        }

        return this.#excludes;
    }

    get excludeIndexes() {
        return this.#excludeIndexes;
    }

    get id() {
        return this.#_id;
    }

    get isExcluded() {
        return this.#isExcluded;
    }

    get name() {
        return this.#name;
    }
}

export default WebAppScope;
