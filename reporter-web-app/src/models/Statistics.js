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

import DependencyTreeStatistics from './DependencyTreeStatistics';
import IssueStatistics from './IssueStatistics';
import LicenseStatistics from './LicenseStatistics';

class Statistics {
    #dependencyTree;

    #openIssues;

    #openRuleViolations;

    #licenses;

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.dependency_tree || obj.dependencyTree) {
                const dependencyTree = obj.dependency_tree || obj.dependencyTree;
                this.#dependencyTree = new DependencyTreeStatistics(dependencyTree);
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

            if (obj.licenses) {
                this.#licenses = new LicenseStatistics(obj.licenses);
            }
        }
    }

    get dependencyTree() {
        return this.#dependencyTree;
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
}

export default Statistics;
