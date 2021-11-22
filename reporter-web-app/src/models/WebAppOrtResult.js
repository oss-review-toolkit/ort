/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
 * Copyright (C) 2021 Bosch.IO GmbH
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

import MetaData from './MetaData';
import Repository from './Repository';
import Statistics from './Statistics';
import WebAppCopyright from './WebAppCopyright';
import WebAppLicense from './WebAppLicense';
import WebAppOrtIssue from './WebAppOrtIssue';
import WebAppPackage from './WebAppPackage';
import WebAppPath from './WebAppPath';
import WebAppPathExclude from './WebAppPathExclude';
import WebAppScanResult from './WebAppScanResult';
import WebAppScope from './WebAppScope';
import WebAppScopeExclude from './WebAppScopeExclude';
import WebAppTreeNode from './WebAppTreeNode';
import WebAppRuleViolation from './WebAppRuleViolation';
import WebAppResolution from './WebAppResolution';
import WebAppVulnerability from './WebAppVulnerability';

class WebAppOrtResult {
    #concludedLicensePackages = [];

    #copyrights = [];

    #declaredLicenses = [];

    #declaredLicensesProcessed = [];

    #dependencyTrees = [];

    #treeNodesByPackageIndexMap;

    #treeNodesByKeyMap;

    #detectedLicenses = [];

    #detectedLicensesProcessed = [];

    #issues = [];

    #issuesByPackageIndexMap = new Map();

    #issueResolutions = [];

    #labels = {};

    #levels = [];

    #licenses = [];

    #licensesIndexesByNameMap = new Map();

    #metaData = {};

    #packages = [];

    #packageIndexesByPackageNameMap = new Map();

    #pathExcludes = [];

    #paths = [];

    #projects = [];

    #scanResults = [];

    #scopes = [];

    #scopeExcludes = [];

    #scopesByNameMap = new Map();

    #statistics = {};

    #repository;

    #repositoryConfiguration;

    #ruleViolations = [];

    #ruleViolationsByPackageIndexMap = new Map();

    #ruleViolationResolutions = [];

    #vulnerabilities = [];

    #vulnerabilitiesByPackageIndexMap = new Map();

    #vulnerabilityResolutions = [];

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.copyrights) {
                for (let i = 0, len = obj.copyrights.length; i < len; i++) {
                    this.#copyrights.push(new WebAppCopyright(obj.copyrights[i]));
                }
            }

            if (obj.labels) {
                this.#labels = obj.labels;
            }

            if (obj.licenses) {
                const { licenses } = obj;
                this.#licensesIndexesByNameMap.clear();

                for (let i = 0, len = licenses.length; i < len; i++) {
                    this.#licensesIndexesByNameMap.set(licenses[i].id, i);
                    this.#licenses.push(new WebAppLicense(licenses[i]));
                }
            }

            if (obj.meta_data || obj.metaData) {
                this.#metaData = new MetaData(obj.meta_data || obj.metaData);
            }

            if (obj.packages) {
                const { packages } = obj;

                for (let i = 0, len = packages.length; i < len; i++) {
                    const webAppPackage = new WebAppPackage(packages[i], this);
                    this.#packages.push(webAppPackage);

                    if (webAppPackage.isProject) {
                        this.#projects.push(webAppPackage);
                    }

                    if (webAppPackage.concludedLicense
                        && webAppPackage.concludedLicense.length > 0) {
                        this.#concludedLicensePackages.push(webAppPackage);
                    }

                    const declaredLicenses = new Set([
                        ...this.#declaredLicenses,
                        ...webAppPackage.declaredLicenses
                    ]);
                    this.#declaredLicenses = Array.from(declaredLicenses).sort();

                    const detectedLicenses = new Set([
                        ...this.#detectedLicenses,
                        ...webAppPackage.detectedLicenses
                    ]);
                    this.#detectedLicenses = Array.from(detectedLicenses).sort();
                }
            }

            if (obj.path_excludes || obj.pathExcludes) {
                const pathExcludes = obj.path_excludes || obj.pathExcludes;

                for (let i = 0, len = pathExcludes.length; i < len; i++) {
                    this.#pathExcludes.push(new WebAppPathExclude(pathExcludes[i]));
                }
            }

            if (obj.paths) {
                const { paths } = obj;
                for (let i = 0, len = paths.length; i < len; i++) {
                    this.#paths.push(new WebAppPath(paths[i], this));
                }
            }

            if (obj.scan_results || obj.scanResults) {
                const scanResults = obj.scan_results || obj.scanResults;

                setTimeout(() => {
                    for (let i = 0, len = scanResults.length; i < len; i++) {
                        this.#scanResults.push(new WebAppScanResult(scanResults[i]));
                    }
                }, 0);
            }

            if (obj.repository) {
                this.#repository = new Repository(obj.repository);
            }

            if (obj.repository_configuration || obj.repositoryConfiguration) {
                this.#repositoryConfiguration = obj.repository_configuration
                    || obj.repositoryConfiguration;
            }

            if (obj.scope_excludes || obj.scopeExcludes) {
                const scopeExcludes = obj.scope_excludes || obj.scopeExcludes;
                for (let i = 0, len = scopeExcludes.length; i < len; i++) {
                    this.#scopeExcludes.push(new WebAppScopeExclude(scopeExcludes[i]));
                }
            }

            if (obj.scopes) {
                const { scopes } = obj;
                for (let i = 0, len = scopes.length; i < len; i++) {
                    const webAppScope = new WebAppScope(scopes[i], this);
                    this.#scopes.push(webAppScope);
                    this.#scopesByNameMap.set(webAppScope.name, webAppScope);
                }
            }

            if (obj.statistics) {
                const { statistics } = obj;
                this.#statistics = new Statistics(statistics);
                const {
                    dependencyTree: {
                        totalTreeDepth
                    },
                    licenses: {
                        declared,
                        detected
                    }
                } = this.#statistics;

                if (declared) {
                    this.#declaredLicensesProcessed = [...declared.keys()];
                }

                if (detected) {
                    this.#detectedLicensesProcessed = [...detected.keys()];
                }

                if (totalTreeDepth) {
                    for (let i = 0, len = totalTreeDepth; i < len; i++) {
                        this.#levels.push(i);
                    }
                }
            }

            if (obj.issues) {
                this.#issuesByPackageIndexMap.clear();

                for (let i = 0, len = obj.issues.length; i < len; i++) {
                    const webAppOrtIssue = new WebAppOrtIssue(obj.issues[i], this);
                    const { packageIndex } = webAppOrtIssue;
                    this.#issues.push(webAppOrtIssue);

                    if (!this.#issuesByPackageIndexMap.has(packageIndex)) {
                        this.#issuesByPackageIndexMap.set(packageIndex, [webAppOrtIssue]);
                    } else {
                        this.#issuesByPackageIndexMap.get(packageIndex).push(webAppOrtIssue);
                    }
                }
            }

            if (obj.issue_resolutions || obj.issueResolutions) {
                const issueResolutions = obj.issue_resolutions
                    || obj.issueResolutions;

                for (let i = 0, len = issueResolutions.length; i < len; i++) {
                    this.#issueResolutions.push(new WebAppResolution(issueResolutions[i]));
                }
            }

            if (obj.rule_violations || obj.ruleViolations) {
                const ruleViolations = obj.rule_violations
                    || obj.ruleViolations;
                this.#ruleViolationsByPackageIndexMap.clear();

                for (let i = 0, len = ruleViolations.length; i < len; i++) {
                    const webAppRuleViolation = new WebAppRuleViolation(ruleViolations[i], this);
                    const { packageIndex } = webAppRuleViolation;
                    this.#ruleViolations.push(webAppRuleViolation);

                    if (!this.#ruleViolationsByPackageIndexMap.has(packageIndex)) {
                        this.#ruleViolationsByPackageIndexMap.set(packageIndex, [webAppRuleViolation]);
                    } else {
                        this.#ruleViolationsByPackageIndexMap.get(packageIndex).push(webAppRuleViolation);
                    }
                }
            }

            if (obj.rule_violation_resolutions || obj.ruleViolationResolutions) {
                const ruleViolationResolutions = obj.rule_violation_resolutions
                    || obj.ruleViolationResolutions;

                for (let i = 0, len = ruleViolationResolutions.length; i < len; i++) {
                    this.#ruleViolationResolutions.push(new WebAppResolution(ruleViolationResolutions[i]));
                }
            }

            if (obj.vulnerabilities) {
                const vulnerabilities = obj.vulnerabilities;
                this.#vulnerabilitiesByPackageIndexMap.clear();

                for (let i = 0, len = vulnerabilities.length; i < len; i++) {
                    const webAppVulnerability = new WebAppVulnerability(vulnerabilities[i], this);
                    const { packageIndex } = webAppVulnerability;
                    this.#vulnerabilities.push(webAppVulnerability);

                    if (!this.#vulnerabilitiesByPackageIndexMap.has(packageIndex)) {
                        this.#vulnerabilitiesByPackageIndexMap.set(packageIndex, [webAppVulnerability]);
                    } else {
                        this.#vulnerabilitiesByPackageIndexMap.get(packageIndex).push(webAppVulnerability);
                    }
                }
            }

            if (obj.vulnerabilities_resolutions || obj.vulnerabilitiesResolutions) {
                const vulnerabilityResolutions = obj.vulnerabilities_resolutions
                    || obj.vulnerabilitiesResolutions;

                for (let i = 0, len = vulnerabilityResolutions.length; i < len; i++) {
                    this.#vulnerabilityResolutions.push(new WebAppResolution(vulnerabilityResolutions[i]));
                }
            }

            if (obj.dependency_trees || obj.dependencyTrees) {
                const dependencyTrees = obj.dependency_trees || obj.dependencyTrees;
                const treeNodesByPackageIndexMap = new Map();
                const treeNodesByKeyMap = new Map();
                const callback = (webAppTreeNode) => {
                    const { packageIndex, key } = webAppTreeNode;
                    const parentKey = webAppTreeNode.parent ? webAppTreeNode.parent.key : webAppTreeNode.packageIndex;

                    treeNodesByKeyMap.set(key, webAppTreeNode);

                    if (!treeNodesByPackageIndexMap.has(packageIndex)) {
                        treeNodesByPackageIndexMap.set(
                            packageIndex,
                            {
                                keys: new Set([key]),
                                parentKeys: new Set([parentKey])
                            }
                        );
                    } else {
                        treeNodesByPackageIndexMap.get(packageIndex).keys.add(key);
                        treeNodesByPackageIndexMap.get(packageIndex).parentKeys.add(parentKey);
                    }
                };

                for (let i = 0, len = dependencyTrees.length; i < len; i++) {
                    setTimeout(() => {
                        this.#dependencyTrees.push(
                            new WebAppTreeNode(
                                dependencyTrees[i],
                                this,
                                callback
                            )
                        );
                    }, 0);
                }

                this.#treeNodesByPackageIndexMap = treeNodesByPackageIndexMap;
                this.#treeNodesByKeyMap = treeNodesByKeyMap;
            }
        }
    }

    get concludedLicensePackages() {
        return this.#concludedLicensePackages;
    }

    get copyrights() {
        return this.#copyrights;
    }

    get declaredLicenses() {
        return this.#declaredLicenses;
    }

    get declaredLicensesProcessed() {
        return this.#declaredLicensesProcessed;
    }

    get dependencyTrees() {
        return this.#dependencyTrees;
    }

    get treeNodesByPackageIndexMap() {
        return this.#treeNodesByPackageIndexMap;
    }

    get treeNodesByKeyMap() {
        return this.#treeNodesByKeyMap;
    }

    get detectedLicenses() {
        return this.#detectedLicenses;
    }

    get detectedLicensesProcessed() {
        return this.#detectedLicensesProcessed;
    }

    get issues() {
        return this.#issues;
    }

    get issueResolutions() {
        return this.#issueResolutions;
    }

    get labels() {
        return this.#labels;
    }

    get levels() {
        return this.#levels;
    }

    get licenses() {
        return this.#licenses;
    }

    get metaData() {
        return this.#metaData;
    }

    get packages() {
        return this.#packages;
    }

    get pathExcludes() {
        return this.#pathExcludes;
    }

    get paths() {
        return this.#paths;
    }

    get projects() {
        return this.#projects;
    }

    get repository() {
        return this.#repository;
    }

    get repositoryConfiguration() {
        return this.#repositoryConfiguration;
    }

    get ruleViolations() {
        return this.#ruleViolations;
    }

    get ruleViolationResolutions() {
        return this.#ruleViolationResolutions;
    }

    get scanResults() {
        return this.#scanResults;
    }

    get scopeExcludes() {
        return this.#scopeExcludes;
    }

    get scopes() {
        return this.#scopes;
    }

    get statistics() {
        return this.#statistics;
    }

    get vulnerabilities() {
        return this.#vulnerabilities;
    }

    get vulnerabilityResolutions() {
        return this.#ruleViolationResolutions;
    }

    getCopyrightByIndex(val) {
        return this.#copyrights[val] || null;
    }

    getLicenseByIndex(val) {
        return this.#licenses[val] || null;
    }

    getLicenseByName(val) {
        return this.#licenses[this.#licensesIndexesByNameMap.get(val)] || null;
    }

    getPackageByIndex(val) {
        return this.#packages[val] || null;
    }

    getPathByIndex(val) {
        return this.#paths[val] || null;
    }

    getIssuesForPackageIndex(val) {
        return this.#issuesByPackageIndexMap.get(val) || [];
    }

    getIssueResolutionByIndex(val) {
        return this.#issueResolutions[val] || null;
    }

    getPathExcludeByIndex(val) {
        return this.#pathExcludes[val] || null;
    }

    getRuleViolationResolutionByIndex(val) {
        return this.#ruleViolationResolutions[val] || null;
    }

    getScanResultByIndex(val) {
        return this.#scanResults[val] || null;
    }

    getScopeByIndex(val) {
        return this.#scopes[val] || null;
    }

    getScopeByName(val) {
        return this.#scopesByNameMap.get(val) || null;
    }

    getScopeExcludeByIndex(val) {
        return this.#scopeExcludes[val] || null;
    }

    getTreeNodeByKey(val) {
        return this.#treeNodesByKeyMap.get(val.toString()) || null;
    }

    getTreeNodeParentKeysByIndex(val) {
        return this.#treeNodesByPackageIndexMap.get(val) || null;
    }

    getRuleViolationsForPackageIndex(val) {
        return this.#ruleViolationsByPackageIndexMap.get(val) || [];
    }

    getVulnerabilitiesForPackageIndex(val) {
        return this.#vulnerabilitiesByPackageIndexMap.get(val) || [];
    }

    getVulnerabilityResolutionByIndex(val) {
        return this.#vulnerabilityResolutions[val] || null;
    }

    hasConcludedLicenses() {
        return this.#concludedLicensePackages.length > 0;
    }

    hasDeclaredLicenses() {
        return this.#declaredLicenses.length > 0;
    }

    hasDeclaredLicensesProcessed() {
        return this.#declaredLicensesProcessed.length > 0;
    }

    hasDetectedLicenses() {
        return this.#detectedLicenses.length > 0;
    }

    hasDetectedLicensesProcessed() {
        return this.#detectedLicensesProcessed.length > 0;
    }

    hasExcludes() {
        if (this.#pathExcludes.length > 0
            || this.#scopeExcludes.length > 0) {
            return true;
        }

        return false;
    }

    hasIssues() {
        const {
            openIssues: {
                errors,
                hints,
                warnings
            }
        } = this.#statistics;

        return errors > 0 || hints > 0 || warnings > 0;
    }

    hasIssuesForPackageIndex(val) {
        return this.#issuesByPackageIndexMap.has(val);
    }

    hasLabels() {
        return Object.keys(this.#labels).length > 0;
    }

    hasLevels() {
        return this.#levels.length > 0;
    }

    hasPathExcludes() {
        return this.#pathExcludes.length > 0;
    }

    hasRepositoryConfiguration() {
        if (this.#repositoryConfiguration
            && this.#repositoryConfiguration.replace(/(\r\n|\n|\r)/gm, '') !== '--- {}') {
            return true;
        }

        return false;
    }

    hasRuleViolations() {
        const {
            openRuleViolations: {
                errors,
                hints,
                warnings
            }
        } = this.#statistics;

        return errors > 0 || hints > 0 || warnings > 0;
    }

    hasRuleViolationsForPackageIndex(val) {
        return this.#ruleViolationsByPackageIndexMap.has(val);
    }

    hasScopes() {
        return this.#scopes.length > 0;
    }

    hasScopeExcludes() {
        return this.#scopeExcludes.length > 0;
    }

    hasVulnerabilities() {
        return this.#vulnerabilities.length > 0;
    }
}

export default WebAppOrtResult;
