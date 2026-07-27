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

import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import type WebAppPackage from "@/models/WebAppPackage";
import type WebAppScope from "@/models/WebAppScope";
import type { EvaluatedModelPath } from "@/types/evaluatedModelData";

class WebAppPath {
    #_id: number | undefined;

    #package: WebAppPackage | undefined;

    #packageIndex: number | undefined;

    #project: WebAppPackage | undefined;

    #projectIndex: number | undefined;

    #scope: WebAppScope | undefined;

    #scopeIndex: number | undefined;

    #path: Set<WebAppPackage> | undefined;

    #pathIndexes: Set<number> | undefined;

    #webAppEvaluatedModel: WebAppEvaluatedModel | undefined;

    constructor(obj?: EvaluatedModelPath, webAppEvaluatedModel?: WebAppEvaluatedModel) {
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

            if (webAppEvaluatedModel) {
                this.#webAppEvaluatedModel = webAppEvaluatedModel;
            }
        }
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get package(): WebAppPackage | undefined {
        if (!this.#package && this.#webAppEvaluatedModel && this.#packageIndex !== undefined) {
            const webAppPackage = this.#webAppEvaluatedModel.getPackageByIndex(this.#packageIndex);
            if (webAppPackage) {
                this.#package = webAppPackage;
            }
        }

        return this.#package;
    }

    get packageId(): string | undefined {
        return this.package?.id;
    }

    get project(): WebAppPackage | undefined {
        if (!this.#project && this.#webAppEvaluatedModel && this.#projectIndex !== undefined) {
            const webAppPackage = this.#webAppEvaluatedModel.getPackageByIndex(this.#projectIndex);
            if (webAppPackage) {
                this.#project = webAppPackage;
            }
        }

        return this.#project;
    }

    get projectIndex(): number | undefined {
        return this.#projectIndex;
    }

    get projectName(): string | undefined {
        return this.project?.id;
    }

    get scope(): WebAppScope | undefined {
        if (!this.#scope && this.#webAppEvaluatedModel && this.#scopeIndex !== undefined) {
            const webAppScope = this.#webAppEvaluatedModel.getScopeByIndex(this.#scopeIndex);
            if (webAppScope) {
                this.#scope = webAppScope;
            }
        }

        return this.#scope;
    }

    get scopeName(): string | undefined {
        return this.scope?.name;
    }

    get path(): Set<WebAppPackage> | undefined {
        if (!this.#path && this.#pathIndexes && this.#webAppEvaluatedModel) {
            this.#path = new Set();
            this.#pathIndexes.forEach((index) => {
                const webAppPackage = this.#webAppEvaluatedModel?.getPackageByIndex(index);
                if (webAppPackage) {
                    this.#path?.add(webAppPackage);
                }
            });
        }

        return this.#path;
    }
}

export default WebAppPath;
