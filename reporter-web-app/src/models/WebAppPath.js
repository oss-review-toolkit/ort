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

class WebAppPath {
    #_id;

    #package;

    #packageIndex;

    #project;

    #projectIndex;

    #scope;

    #scopeIndex;

    #path;

    #pathIndexes;

    #webAppOrtResult;

    constructor(obj, webAppOrtResult) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (Number.isInteger(obj.pkg)) {
                this.#packageIndex = obj.pkg;
            }

            if (Number.isInteger(obj.project)) {
                this.#projectIndex = obj.project;
            }

            if (Number.isInteger(obj.scope)) {
                this.#scopeIndex = obj.scope;
            }

            if (obj.path) {
                this.#pathIndexes = new Set(obj.path);
            }

            if (webAppOrtResult) {
                this.#webAppOrtResult = webAppOrtResult;
            }
        }
    }

    get _id() {
        return this.#_id;
    }

    get package() {
        if (!this.#package && this.#webAppOrtResult) {
            const webAppPackage = this.#webAppOrtResult.getPackageByIndex(this.#packageIndex);
            if (webAppPackage) {
                this.#package = webAppPackage;
            }
        }

        return this.#package;
    }

    get packageName() {
        return this.package.id;
    }

    get project() {
        if (!this.#project && this.#webAppOrtResult) {
            const webAppPackage = this.#webAppOrtResult.getPackageByIndex(this.#projectIndex);
            if (webAppPackage) {
                this.#project = webAppPackage;
            }
        }

        return this.#project;
    }

    get projectIndex() {
        return this.#projectIndex;
    }

    get projectName() {
        return this.project.id;
    }

    get scope() {
        if (!this.#scope && this.#webAppOrtResult) {
            const webAppScope = this.#webAppOrtResult.getScopeByIndex(this.#scopeIndex);
            if (webAppScope) {
                this.#scope = webAppScope;
            }
        }

        return this.#scope;
    }

    get scopeName() {
        return this.scope.name;
    }

    get path() {
        if (!this.#path && this.#pathIndexes && this.#webAppOrtResult) {
            this.#path = new Set();
            this.#pathIndexes.forEach((index) => {
                const webAppPackage = this.#webAppOrtResult.getPackageByIndex(index);
                if (webAppPackage) {
                    this.#path.add(webAppPackage);
                }
            });
        }

        return this.#path;
    }
}

export default WebAppPath;
