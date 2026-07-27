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

import type { EvaluatedModelDependencyTreeStatistics } from "@/types/evaluatedModelData";

class DependencyTreeStatistics {
    #excludedPackages: number = 0;

    #excludedProjects: number = 0;

    #excludedScopes: readonly string[] = [];

    #includedPackages: number = 0;

    #includedProjects: number = 0;

    #includedScopes: readonly string[] = [];

    #includedTreeDepth: number = 0;

    #totalTreeDepth: number = 0;

    constructor(obj?: EvaluatedModelDependencyTreeStatistics) {
        if (obj) {
            if (Number.isInteger(obj.excluded_packages) || Number.isInteger(obj.excludedPackages)) {
                this.#excludedPackages = (obj.excluded_packages || obj.excludedPackages) ?? 0;
            }

            if (Number.isInteger(obj.excluded_projects) || Number.isInteger(obj.excludedProjects)) {
                this.#excludedProjects = (obj.excluded_projects || obj.excludedProjects) ?? 0;
            }

            if (Array.isArray(obj.excluded_scopes) || Array.isArray(obj.excludedScopes)) {
                this.#excludedScopes = obj.excluded_scopes || obj.excludedScopes || [];
            }

            if (Number.isInteger(obj.included_packages) || Number.isInteger(obj.includedPackages)) {
                this.#includedPackages = (obj.included_packages || obj.includedPackages) ?? 0;
            }

            if (Number.isInteger(obj.included_projects) || Number.isInteger(obj.includedProjects)) {
                this.#includedProjects = (obj.included_projects || obj.includedProjects) ?? 0;
            }

            if (Array.isArray(obj.included_scopes) || Array.isArray(obj.includedScopes)) {
                this.#includedScopes = obj.included_scopes || obj.includedScopes || [];
            }

            if (Number.isInteger(obj.included_tree_depth) || Number.isInteger(obj.includedTreeDepth)) {
                this.#includedTreeDepth = (obj.included_tree_depth || obj.includedTreeDepth) ?? 0;
            }

            if (Number.isInteger(obj.total_tree_depth) || Number.isInteger(obj.totalTreeDepth)) {
                this.#totalTreeDepth = (obj.total_tree_depth || obj.totalTreeDepth) ?? 0;
            }
        }
    }

    get excludedPackages(): number {
        return this.#excludedPackages;
    }

    get excludedProjects(): number {
        return this.#excludedProjects;
    }

    get excludedScopes(): readonly string[] {
        return this.#excludedScopes;
    }

    get includedPackages(): number {
        return this.#includedPackages;
    }

    get includedProjects(): number {
        return this.#includedProjects;
    }

    get includedScopes(): readonly string[] {
        return this.#includedScopes;
    }

    get includedTreeDepth(): number {
        return this.#includedTreeDepth;
    }

    get totalTreeDepth(): number {
        return this.#totalTreeDepth;
    }
}

export default DependencyTreeStatistics;
