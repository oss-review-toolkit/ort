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

import DependencyTreeStatistics from './DependencyTreeStatistics';
import IssueStatistics from './IssueStatistics';
import LicenseStatistics from './LicenseStatistics';
import RepositoryConfigurationStatistics from './RepositoryConfigurationStatistics';

class Statistics {
    #dependencyTree = new DependencyTreeStatistics();

    #executionDurationInSeconds = 0;

    #openIssues = new IssueStatistics();

    #openRuleViolations = new IssueStatistics();

    #openVulnerabilities = 0;

    #licenses = new LicenseStatistics();

    #repositoryConfiguration = new RepositoryConfigurationStatistics();

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.dependency_tree || obj.dependencyTree) {
                const dependencyTree = obj.dependency_tree || obj.dependencyTree;
                this.#dependencyTree = new DependencyTreeStatistics(dependencyTree);
            }

            if (obj.execution_duration_in_seconds || obj.executionDurationInSeconds) {
                this.#executionDurationInSeconds = obj.execution_duration_in_seconds
                    || obj.executionDurationInSeconds;
            }

            if (obj.licenses) {
                this.#licenses = new LicenseStatistics(obj.licenses);
            }

            if (obj.open_issues || obj.openIssues) {
                const openIssues = obj.open_issues || obj.openIssues;
                this.#openIssues = new IssueStatistics(openIssues);
            }

            if (obj.open_rule_violations || obj.openRuleViolations) {
                const openRuleViolations = obj.open_rule_violations
                    || obj.openRuleViolations;
                this.#openRuleViolations = new IssueStatistics(openRuleViolations);
            }

            if (obj.open_vulnerabilities || obj.openVulnerabilities) {
                this.#openVulnerabilities = obj.open_vulnerabilities
                    || obj.openVulnerabilities;
            }

            if (obj.repositoryConfiguration || obj.repository_configuration) {
                const repositoryConfiguration = obj.repositoryConfiguration || obj.repository_configuration;
                this.#repositoryConfiguration = new RepositoryConfigurationStatistics(repositoryConfiguration);
            }
        }
    }

    get dependencyTree() {
        return this.#dependencyTree;
    }

    get executionDurationInSeconds() {
        return this.#executionDurationInSeconds;
    }

    get licenses() {
        return this.#licenses;
    }

    get openIssues() {
        return this.#openIssues;
    }

    get openRuleViolations() {
        return this.#openRuleViolations;
    }

    get openVulnerabilities() {
        return this.#openVulnerabilities;
    }

    get repositoryConfiguration() {
        return this.#repositoryConfiguration;
    }
}

export default Statistics;
