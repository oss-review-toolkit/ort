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

import type { EvaluatedModelResolvedConfigurationStatistics } from "@/types/evaluatedModelData";

class ResolvedConfigurationStatistics {
    #issueResolutions: number = 0;

    #licenseChoices: number = 0;

    #packageConfigurations: number = 0;

    #packageCurations: number = 0;

    #ruleViolationResolutions: number = 0;

    #vulnerabilityResolutions: number = 0;

    constructor(obj?: EvaluatedModelResolvedConfigurationStatistics) {
        if (obj) {
            if (Number.isInteger(obj.issue_resolutions) || Number.isInteger(obj.issueResolutions)) {
                this.#issueResolutions = (obj.issue_resolutions || obj.issueResolutions) ?? 0;
            }

            if (Number.isInteger(obj.license_choices) || Number.isInteger(obj.licenseChoices)) {
                this.#licenseChoices = (obj.license_choices || obj.licenseChoices) ?? 0;
            }

            if (Number.isInteger(obj.package_configurations) || Number.isInteger(obj.packageConfigurations)) {
                this.#packageConfigurations = (obj.package_configurations || obj.packageConfigurations) ?? 0;
            }

            if (Number.isInteger(obj.package_curations) || Number.isInteger(obj.packageCurations)) {
                this.#packageCurations = (obj.package_curations || obj.packageCurations) ?? 0;
            }

            if (Number.isInteger(obj.rule_violation_resolutions) || Number.isInteger(obj.ruleViolationResolutions)) {
                this.#ruleViolationResolutions = (obj.rule_violation_resolutions || obj.ruleViolationResolutions) ?? 0;
            }

            if (Number.isInteger(obj.vulnerability_resolutions) || Number.isInteger(obj.vulnerabilityResolutions)) {
                this.#vulnerabilityResolutions = (obj.vulnerability_resolutions || obj.vulnerabilityResolutions) ?? 0;
            }
        }
    }

    get issueResolutions(): number {
        return this.#issueResolutions;
    }

    get licenseChoices(): number {
        return this.#licenseChoices;
    }

    get packageConfigurations(): number {
        return this.#packageConfigurations;
    }

    get packageCurations(): number {
        return this.#packageCurations;
    }

    // Total resolutions applied to the run (issue + rule-violation + vulnerability resolutions).
    get resolutions(): number {
        return this.#issueResolutions + this.#ruleViolationResolutions + this.#vulnerabilityResolutions;
    }

    get ruleViolationResolutions(): number {
        return this.#ruleViolationResolutions;
    }

    get vulnerabilityResolutions(): number {
        return this.#vulnerabilityResolutions;
    }
}

export default ResolvedConfigurationStatistics;
