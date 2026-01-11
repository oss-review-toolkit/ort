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

class RepositoryConfigurationStatistics {
    #issueResolutions = 0;

    #licenseChoices = 0;

    #licenseFindingCurations = 0;

    #pathExcludes = 0;

    #ruleViolationResolutions = 0;

    #scopeExcludes = 0;

    #vulnerabilityResolutions = 0;

    constructor(obj) {
        if (obj instanceof Object) {
            if (Number.isInteger(obj.issue_resolutions) || Number.isInteger(obj.issueResolutions)) {
                this.#issueResolutions = obj.issue_resolutions || obj.issueResolutions;
            }

            if (Number.isInteger(obj.license_choices) || Number.isInteger(obj.licenseChoices)) {
                this.#licenseChoices = obj.license_choices || obj.licenseChoices;
            }

            if (Number.isInteger(obj.license_finding_curations) || Number.isInteger(obj.licenseFindingCurations)) {
                this.#licenseFindingCurations = obj.license_finding_curations || obj.licenseFindingCurations;
            }

            if (Number.isInteger(obj.pathExcludes) || Number.isInteger(obj.path_excludes)) {
                this.#pathExcludes = obj.path_excludes || obj.pathExcludes;
            }

            if (Number.isInteger(obj.rule_violation_resolutions) || Number.isInteger(obj.ruleViolationResolutions)) {
                this.#ruleViolationResolutions = obj.rule_violation_resolutions || obj.ruleViolationResolutions;
            }

            if (Number.isInteger(obj.scopeExcludes) || Number.isInteger(obj.scope_excludes)) {
                this.#scopeExcludes = obj.scope_excludes || obj.scopeExcludes;
            }

            if (Number.isInteger(obj.vulnerability_resolutions) || Number.isInteger(obj.vulnerabilityResolutions)) {
                this.#vulnerabilityResolutions = obj.vulnerability_resolutions || obj.vulnerabilityResolutions;
            }
        }
    }

    get issueResolutions() {
        return this.#issueResolutions;
    }

    get licenseChoices() {
        return this.#licenseChoices;
    }

    get licenseFindingCurations() {
        return this.#licenseFindingCurations;
    }

    get pathExcludes() {
        return this.#pathExcludes;
    }

    get ruleViolationResolutions() {
        return this.#ruleViolationResolutions;
    }

    get scopeExcludes() {
        return this.#scopeExcludes;
    }

    get vulnerabilityResolutions() {
        return this.#vulnerabilityResolutions;
    }
}

export default RepositoryConfigurationStatistics;
