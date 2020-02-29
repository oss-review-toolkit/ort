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

class DependencyTreeStatistics {
    #excludedPackages = 0;

    #excludedProjects = 0;

    #excludedScopes = [];

    #includedPackages = 0;

    #includedProjects = 0;

    #includedScopes = [];

    #includedTreeDepth = 0;

    #totalTreeDepth = 0;

    constructor(obj) {
        if (obj instanceof Object) {
            if (Number.isInteger(obj.excluded_packages)
                || Number.isInteger(obj.excludedPackages)) {
                this.#excludedPackages = obj.excluded_packages || obj.excludedPackages;
            }

            if (Number.isInteger(obj.excluded_projects)
                || Number.isInteger(obj.excludedProjects)) {
                this.#excludedProjects = obj.excluded_projects || obj.excludedProjects;
            }

            if (Array.isArray(obj.excluded_scopes)
                || Array.isArray(obj.excludedScopes)) {
                this.#excludedScopes = obj.excluded_scopes || obj.excludedScopes;
            }

            if (Number.isInteger(obj.included_packages)
                || Number.isInteger(obj.includedPackages)) {
                this.#includedPackages = obj.included_packages || obj.includedPackages;
            }

            if (Number.isInteger(obj.included_projects)
                || Number.isInteger(obj.includedProjects)) {
                this.#includedProjects = obj.included_projects || obj.includedProjects;
            }

            if (Array.isArray(obj.included_scopes)
                || Array.isArray(obj.includedScopes)) {
                this.#includedScopes = obj.included_scopes || obj.includedScopes;
            }

            if (Number.isInteger(obj.included_tree_depth)
                || Number.isInteger(obj.includedTreeDepth)) {
                this.#includedTreeDepth = obj.included_tree_depth || obj.includedTreeDepth;
            }

            if (Number.isInteger(obj.total_tree_depth)
                || Number.isInteger(obj.totalTreeDepth)) {
                this.#totalTreeDepth = obj.total_tree_depth || obj.totalTreeDepth;
            }
        }
    }

    get excludedPackages() {
        return this.#excludedPackages;
    }

    get excludedProjects() {
        return this.#excludedProjects;
    }

    get excludedScopes() {
        return this.#excludedScopes;
    }

    get includedPackages() {
        return this.#includedPackages;
    }

    get includedProjects() {
        return this.#includedProjects;
    }

    get includedScopes() {
        return this.#includedScopes;
    }

    get includedTreeDepth() {
        return this.#includedTreeDepth;
    }

    get totalTreeDepth() {
        return this.#totalTreeDepth;
    }
}

export default DependencyTreeStatistics;
