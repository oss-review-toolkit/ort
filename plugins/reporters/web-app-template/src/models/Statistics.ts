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

import DependencyTreeStatistics from "@/models/DependencyTreeStatistics";
import IssueStatistics from "@/models/IssueStatistics";
import LicenseStatistics from "@/models/LicenseStatistics";
import RepositoryConfigurationStatistics from "@/models/RepositoryConfigurationStatistics";
import ResolvedConfigurationStatistics from "@/models/ResolvedConfigurationStatistics";
import type { EvaluatedModelStatistics } from "@/types/evaluatedModelData";

class Statistics {
    #dependencyTree: DependencyTreeStatistics = new DependencyTreeStatistics();

    #executionDurationInSeconds: number = 0;

    #openIssues: IssueStatistics = new IssueStatistics();

    #openRuleViolations: IssueStatistics = new IssueStatistics();

    #openVulnerabilities: number = 0;

    #licenses: LicenseStatistics = new LicenseStatistics();

    #repositoryConfiguration: RepositoryConfigurationStatistics = new RepositoryConfigurationStatistics();

    #resolvedConfiguration: ResolvedConfigurationStatistics = new ResolvedConfigurationStatistics();

    constructor(obj?: EvaluatedModelStatistics) {
        if (obj) {
            if (obj.dependency_tree || obj.dependencyTree) {
                const dependencyTree = obj.dependency_tree || obj.dependencyTree;
                this.#dependencyTree = new DependencyTreeStatistics(dependencyTree);
            }

            if (obj.execution_duration_in_seconds || obj.executionDurationInSeconds) {
                this.#executionDurationInSeconds =
                    (obj.execution_duration_in_seconds || obj.executionDurationInSeconds) ?? 0;
            }

            if (obj.licenses) {
                this.#licenses = new LicenseStatistics(obj.licenses);
            }

            if (obj.open_issues || obj.openIssues) {
                const openIssues = obj.open_issues || obj.openIssues;
                this.#openIssues = new IssueStatistics(openIssues);
            }

            if (obj.open_rule_violations || obj.openRuleViolations) {
                const openRuleViolations = obj.open_rule_violations || obj.openRuleViolations;
                this.#openRuleViolations = new IssueStatistics(openRuleViolations);
            }

            if (obj.open_vulnerabilities || obj.openVulnerabilities) {
                this.#openVulnerabilities = (obj.open_vulnerabilities || obj.openVulnerabilities) ?? 0;
            }

            if (obj.repositoryConfiguration || obj.repository_configuration) {
                const repositoryConfiguration = obj.repositoryConfiguration || obj.repository_configuration;
                this.#repositoryConfiguration = new RepositoryConfigurationStatistics(repositoryConfiguration);
            }

            if (obj.resolvedConfiguration || obj.resolved_configuration) {
                const resolvedConfiguration = obj.resolvedConfiguration || obj.resolved_configuration;
                this.#resolvedConfiguration = new ResolvedConfigurationStatistics(resolvedConfiguration);
            }
        }
    }

    get dependencyTree(): DependencyTreeStatistics {
        return this.#dependencyTree;
    }

    get executionDurationInSeconds(): number {
        return this.#executionDurationInSeconds;
    }

    get licenses(): LicenseStatistics {
        return this.#licenses;
    }

    get openIssues(): IssueStatistics {
        return this.#openIssues;
    }

    get openRuleViolations(): IssueStatistics {
        return this.#openRuleViolations;
    }

    get openVulnerabilities(): number {
        return this.#openVulnerabilities;
    }

    get repositoryConfiguration(): RepositoryConfigurationStatistics {
        return this.#repositoryConfiguration;
    }

    get resolvedConfiguration(): ResolvedConfigurationStatistics {
        return this.#resolvedConfiguration;
    }
}

export default Statistics;
