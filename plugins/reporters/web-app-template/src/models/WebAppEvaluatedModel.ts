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

import YAML from "yaml";

import type IssueStatistics from "@/models/IssueStatistics";
import Statistics from "@/models/Statistics";
import ToolsMetadata from "@/models/ToolsMetadata";
import WebAppCopyright from "@/models/WebAppCopyright";
import WebAppLicense from "@/models/WebAppLicense";
import WebAppLicenseFindingCuration from "@/models/WebAppLicenseFindingCuration";
import WebAppOrtIssue from "@/models/WebAppOrtIssue";
import WebAppPackage from "@/models/WebAppPackage";
import WebAppPackageConfiguration from "@/models/WebAppPackageConfiguration";
import WebAppPackageCuration from "@/models/WebAppPackageCuration";
import WebAppPath from "@/models/WebAppPath";
import WebAppPathExclude from "@/models/WebAppPathExclude";
import WebAppRepository from "@/models/WebAppRepository";
import WebAppResolution from "@/models/WebAppResolution";
import WebAppRuleViolation from "@/models/WebAppRuleViolation";
import WebAppScanResult from "@/models/WebAppScanResult";
import WebAppScope from "@/models/WebAppScope";
import WebAppScopeExclude from "@/models/WebAppScopeExclude";
import WebAppTreeNode from "@/models/WebAppTreeNode";
import WebAppVulnerability from "@/models/WebAppVulnerability";
import WebAppVulnerabilityResolution from "@/models/WebAppVulnerabilityResolution";
import type { EvaluatedModel } from "@/types/evaluatedModelData";

type PlainJsObject = Record<string, unknown>;

interface TreeNodeBucket {
    keys: Set<string>;
    parentKeys: Set<string | number | undefined>;
}

// ORT flags an issue/violation as "severe" when its severity is at or above the run's configured
// threshold (ERROR is the most severe, then WARNING, then HINT). Counts only the open items at or above
// that threshold: ERROR counts open errors, WARNING adds warnings, and a HINT or absent/unknown threshold
// counts every open item (the plain total).
function countAtOrAboveThreshold(stats: IssueStatistics, threshold: string | undefined): number {
    switch (threshold?.toUpperCase()) {
        case "ERROR":
            return stats.errors;
        case "WARNING":
            return stats.errors + stats.warnings;
        default:
            return stats.total;
    }
}

class WebAppEvaluatedModel {
    #concludedLicensePackages: WebAppPackage[] = [];

    #copyrights: WebAppCopyright[] = [];

    #declaredLicenses: string[] = [];

    #declaredLicensesProcessed: string[] = [];

    #dependencyTrees: WebAppTreeNode[] = [];

    // The dependency trees are built across many deferred (setTimeout) tasks so a large report never blocks the
    // main thread. #pendingTreeBuilds counts the outstanding tasks (roots and the child tasks they spawn);
    // #dependencyTreesReady flips to false while any are in flight and back to true once they all drain, at
    // which point #treesReadyListeners fire so the view can render the finished forest instead of a partial one.
    #dependencyTreesReady: boolean = true;

    #pendingTreeBuilds: number = 0;

    #treesReadyListeners: Set<() => void> = new Set();

    #effectiveLicenses: string[] = [];

    #treeNodesByPackageIndexMap: Map<number | undefined, TreeNodeBucket> | undefined;

    #treeNodesByKeyMap: Map<string, WebAppTreeNode> | undefined;

    #detectedLicenses: string[] = [];

    #detectedLicensesProcessed: string[] = [];

    #effectiveLicensePackages: WebAppPackage[] = [];

    #issues: WebAppOrtIssue[] = [];

    #issuesByPackageIndexMap = new Map<number | undefined, WebAppOrtIssue[]>();

    #issueResolutions: WebAppResolution[] = [];

    #labels: Record<string, string> = {};

    #levels: number[] = [];

    #licenseFindingCurations: WebAppLicenseFindingCuration[] = [];

    #licenses: WebAppLicense[] = [];

    #licensesIndexesByNameMap = new Map<string, number>();

    #packages: WebAppPackage[] = [];

    #packagesByKeyMap = new Map<string | undefined, WebAppPackage>();

    #packageConfigurations: WebAppPackageConfiguration[] = [];

    #packageConfigurationsAsPlainJsObject: PlainJsObject[] | undefined;

    #packageCurations: WebAppPackageCuration[] = [];

    #packageCurationsAsPlainJsObject: PlainJsObject[] | undefined;

    #packagesIdtoKeyMap = new Map<string | undefined, string | undefined>();

    #pathExcludes: WebAppPathExclude[] = [];

    #paths: WebAppPath[] = [];

    #projects: WebAppPackage[] = [];

    #scanResults: WebAppScanResult[] = [];

    #scopes: WebAppScope[] = [];

    #scopeExcludes: WebAppScopeExclude[] = [];

    #scopesByNameMap = new Map<string | undefined, WebAppScope>();

    #severeIssueThreshold: string | undefined;

    #severeRuleViolationThreshold: string | undefined;

    #statistics: Statistics = new Statistics();

    #toolsMetadata: ToolsMetadata = new ToolsMetadata();

    #repository: WebAppRepository | undefined;

    #repositoryConfiguration: string | undefined;

    #ruleViolations: WebAppRuleViolation[] = [];

    #ruleViolationsByPackageIndexMap = new Map<number, WebAppRuleViolation[]>();

    #ruleViolationResolutions: WebAppResolution[] = [];

    #vulnerabilities: WebAppVulnerability[] = [];

    #vulnerabilitiesByPackageIndexMap = new Map<number, WebAppVulnerability[]>();

    #vulnerabilityResolutions: WebAppVulnerabilityResolution[] = [];

    constructor(obj?: EvaluatedModel) {
        if (obj) {
            if (obj.copyrights) {
                for (let i = 0, len = obj.copyrights.length; i < len; i++) {
                    const raw = obj.copyrights[i];
                    if (raw) {
                        this.#copyrights.push(new WebAppCopyright(raw));
                    }
                }
            }

            if (obj.labels) {
                this.#labels = obj.labels;
            }

            if (obj.license_finding_curations || obj.licenseFindingCurations) {
                const licenseFindingCurations = obj.license_finding_curations || obj.licenseFindingCurations || [];

                for (let i = 0, len = licenseFindingCurations.length; i < len; i++) {
                    const raw = licenseFindingCurations[i];
                    if (raw) {
                        this.#licenseFindingCurations.push(new WebAppLicenseFindingCuration(raw));
                    }
                }
            }

            if (obj.licenses) {
                const { licenses } = obj;
                this.#licensesIndexesByNameMap.clear();

                for (let i = 0, len = licenses.length; i < len; i++) {
                    const raw = licenses[i];
                    if (raw) {
                        if (raw.id !== undefined) {
                            this.#licensesIndexesByNameMap.set(raw.id, i);
                        }
                        this.#licenses.push(new WebAppLicense(raw));
                    }
                }
            }

            if (obj.tools_metadata || obj.toolsMetadata) {
                this.#toolsMetadata = new ToolsMetadata(obj.tools_metadata || obj.toolsMetadata);
            }

            if (obj.package_configurations || obj.packageConfigurations) {
                const licenseFindingCurationsRaw =
                    (obj.license_finding_curations || obj.licenseFindingCurations)?.map(({ _id, ...rest }) => {
                        void _id;
                        return rest as PlainJsObject;
                    }) || [];
                const packageConfigurations = obj.package_configurations || obj.packageConfigurations || [];
                const pathExcludesRaw = (obj.path_excludes || obj.pathExcludes || []).map(({ _id, ...rest }) => {
                    void _id;
                    return rest as PlainJsObject;
                });

                this.#packageConfigurationsAsPlainJsObject = packageConfigurations.map(({ _id, ...rest }) => {
                    void _id;
                    return rest as PlainJsObject;
                });

                for (let i = 0, len = packageConfigurations.length; i < len; i++) {
                    const raw = packageConfigurations[i];
                    if (!raw) {
                        continue;
                    }
                    const webAppPackageConfiguration = new WebAppPackageConfiguration(raw);
                    this.#packageConfigurations.push(webAppPackageConfiguration);

                    const { pathExcludeIndexes } = webAppPackageConfiguration;
                    const plain = this.#packageConfigurationsAsPlainJsObject[i];
                    if (plain && pathExcludeIndexes.length !== 0) {
                        const collected: PlainJsObject[] = [];
                        for (let j = 0, jlen = pathExcludeIndexes.length; j < jlen; j++) {
                            const idx = pathExcludeIndexes[j];
                            if (idx !== undefined) {
                                const item = pathExcludesRaw[idx];
                                if (item) {
                                    collected.push(item);
                                }
                            }
                        }
                        plain.path_excludes = collected;
                    }

                    const { licenseFindingCurationIndexes } = webAppPackageConfiguration;
                    if (plain && licenseFindingCurationIndexes.length !== 0) {
                        const collected: PlainJsObject[] = [];
                        for (let j = 0, jlen = licenseFindingCurationIndexes.length; j < jlen; j++) {
                            const idx = licenseFindingCurationIndexes[j];
                            if (idx !== undefined) {
                                const item = licenseFindingCurationsRaw[idx];
                                if (item) {
                                    collected.push(item);
                                }
                            }
                        }
                        plain.license_finding_curations = collected;
                    }
                }
            }

            if (obj.package_curations || obj.packageCurations) {
                const packageCurations = obj.package_curations || obj.packageCurations || [];
                this.#packageCurationsAsPlainJsObject = packageCurations.map(({ _id, ...rest }) => {
                    void _id;
                    return rest as PlainJsObject;
                });

                for (let i = 0, len = packageCurations.length; i < len; i++) {
                    const raw = packageCurations[i];
                    if (raw) {
                        this.#packageCurations.push(new WebAppPackageCuration(raw));
                    }
                }
            }

            if (obj.packages) {
                const { packages } = obj;
                // Accumulate every package's declared/detected licenses into one Set each and sort once
                // after the loop, rather than rebuilding and re-sorting the growing arrays on each package
                // (which made construction quadratic in the number of packages).
                const declaredLicenseNames = new Set<string>();
                const detectedLicenseNames = new Set<string>();

                for (let i = 0, len = packages.length; i < len; i++) {
                    const raw = packages[i];
                    if (!raw) {
                        continue;
                    }
                    const webAppPackage = new WebAppPackage(raw, this);
                    this.#packages.push(webAppPackage);
                    this.#packagesByKeyMap.set(webAppPackage.key, webAppPackage);
                    this.#packagesIdtoKeyMap.set(webAppPackage.id, webAppPackage.key);

                    if (webAppPackage.isProject) {
                        this.#projects.push(webAppPackage);
                    }

                    if (webAppPackage.concludedLicense && webAppPackage.concludedLicense.length > 0) {
                        this.#concludedLicensePackages.push(webAppPackage);
                    }

                    for (const license of webAppPackage.declaredLicenses) {
                        declaredLicenseNames.add(license);
                    }
                    for (const license of webAppPackage.detectedLicenses) {
                        detectedLicenseNames.add(license);
                    }

                    if (webAppPackage.effectiveLicense && webAppPackage.effectiveLicense.length > 0) {
                        this.#effectiveLicensePackages.push(webAppPackage);
                    }
                }

                this.#declaredLicenses = Array.from(declaredLicenseNames).sort();
                this.#detectedLicenses = Array.from(detectedLicenseNames).sort();
            }

            if (obj.path_excludes || obj.pathExcludes) {
                const pathExcludes = obj.path_excludes || obj.pathExcludes || [];

                for (let i = 0, len = pathExcludes.length; i < len; i++) {
                    const raw = pathExcludes[i];
                    if (raw) {
                        this.#pathExcludes.push(new WebAppPathExclude(raw));
                    }
                }
            }

            if (obj.paths) {
                const { paths } = obj;
                for (let i = 0, len = paths.length; i < len; i++) {
                    const raw = paths[i];
                    if (raw) {
                        this.#paths.push(new WebAppPath(raw, this));
                    }
                }
            }

            if (obj.scan_results || obj.scanResults) {
                const scanResults = obj.scan_results || obj.scanResults || [];

                setTimeout(() => {
                    for (let i = 0, len = scanResults.length; i < len; i++) {
                        const raw = scanResults[i];
                        if (raw) {
                            this.#scanResults.push(new WebAppScanResult(raw));
                        }
                    }
                }, 0);
            }

            if (obj.repository) {
                this.#repository = new WebAppRepository(obj.repository, this);
            }

            if (obj.repository_configuration || obj.repositoryConfiguration) {
                this.#repositoryConfiguration = obj.repository_configuration || obj.repositoryConfiguration;
            }

            if (obj.scope_excludes || obj.scopeExcludes) {
                const scopeExcludes = obj.scope_excludes || obj.scopeExcludes || [];
                for (let i = 0, len = scopeExcludes.length; i < len; i++) {
                    const raw = scopeExcludes[i];
                    if (raw) {
                        this.#scopeExcludes.push(new WebAppScopeExclude(raw));
                    }
                }
            }

            if (obj.scopes) {
                const { scopes } = obj;
                for (let i = 0, len = scopes.length; i < len; i++) {
                    const raw = scopes[i];
                    if (raw) {
                        const webAppScope = new WebAppScope(raw, this);
                        this.#scopes.push(webAppScope);
                        this.#scopesByNameMap.set(webAppScope.name, webAppScope);
                    }
                }
            }

            if (obj.severe_issue_threshold || obj.severeIssueThreshold) {
                this.#severeIssueThreshold = obj.severe_issue_threshold || obj.severeIssueThreshold;
            }

            if (obj.severe_rule_violation_threshold || obj.severeRuleViolationThreshold) {
                this.#severeRuleViolationThreshold =
                    obj.severe_rule_violation_threshold || obj.severeRuleViolationThreshold;
            }

            if (obj.statistics) {
                const { statistics } = obj;
                this.#statistics = new Statistics(statistics);
                const {
                    dependencyTree: { totalTreeDepth },
                    licenses: { declared, detected, effective },
                } = this.#statistics;

                if (declared) {
                    this.#declaredLicensesProcessed = [...declared.keys()];
                }

                if (detected) {
                    this.#detectedLicensesProcessed = [...detected.keys()];
                }

                if (effective) {
                    this.#effectiveLicenses = [...effective.keys()];
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
                    const raw = obj.issues[i];
                    if (!raw) {
                        continue;
                    }
                    const webAppOrtIssue = new WebAppOrtIssue(raw, this);
                    const { packageIndex } = webAppOrtIssue;
                    this.#issues.push(webAppOrtIssue);

                    const existing = this.#issuesByPackageIndexMap.get(packageIndex);
                    if (!existing) {
                        this.#issuesByPackageIndexMap.set(packageIndex, [webAppOrtIssue]);
                    } else {
                        existing.push(webAppOrtIssue);
                    }
                }
            }

            if (obj.issue_resolutions || obj.issueResolutions) {
                const issueResolutions = obj.issue_resolutions || obj.issueResolutions || [];

                for (let i = 0, len = issueResolutions.length; i < len; i++) {
                    const raw = issueResolutions[i];
                    if (raw) {
                        this.#issueResolutions.push(new WebAppResolution(raw));
                    }
                }
            }

            if (obj.rule_violations || obj.ruleViolations) {
                const ruleViolations = obj.rule_violations || obj.ruleViolations || [];
                this.#ruleViolationsByPackageIndexMap.clear();

                for (let i = 0, len = ruleViolations.length; i < len; i++) {
                    const raw = ruleViolations[i];
                    if (!raw) {
                        continue;
                    }
                    const webAppRuleViolation = new WebAppRuleViolation(raw, this);
                    const { packageIndex } = webAppRuleViolation;
                    this.#ruleViolations.push(webAppRuleViolation);

                    const existing = this.#ruleViolationsByPackageIndexMap.get(packageIndex);
                    if (!existing) {
                        this.#ruleViolationsByPackageIndexMap.set(packageIndex, [webAppRuleViolation]);
                    } else {
                        existing.push(webAppRuleViolation);
                    }
                }
            }

            if (obj.rule_violation_resolutions || obj.ruleViolationResolutions) {
                const ruleViolationResolutions = obj.rule_violation_resolutions || obj.ruleViolationResolutions || [];

                for (let i = 0, len = ruleViolationResolutions.length; i < len; i++) {
                    const raw = ruleViolationResolutions[i];
                    if (raw) {
                        this.#ruleViolationResolutions.push(new WebAppResolution(raw));
                    }
                }
            }

            if (obj.vulnerabilities) {
                const vulnerabilities = obj.vulnerabilities;
                this.#vulnerabilitiesByPackageIndexMap.clear();

                for (let i = 0, len = vulnerabilities.length; i < len; i++) {
                    const raw = vulnerabilities[i];
                    if (!raw) {
                        continue;
                    }
                    const webAppVulnerability = new WebAppVulnerability(raw, this);
                    const { packageIndex } = webAppVulnerability;
                    this.#vulnerabilities.push(webAppVulnerability);

                    const existing = this.#vulnerabilitiesByPackageIndexMap.get(packageIndex);
                    if (!existing) {
                        this.#vulnerabilitiesByPackageIndexMap.set(packageIndex, [webAppVulnerability]);
                    } else {
                        existing.push(webAppVulnerability);
                    }
                }
            }

            if (obj.vulnerabilities_resolutions || obj.vulnerabilitiesResolutions) {
                const vulnerabilityResolutions =
                    obj.vulnerabilities_resolutions || obj.vulnerabilitiesResolutions || [];

                for (let i = 0, len = vulnerabilityResolutions.length; i < len; i++) {
                    const raw = vulnerabilityResolutions[i];
                    if (raw) {
                        this.#vulnerabilityResolutions.push(new WebAppVulnerabilityResolution(raw));
                    }
                }
            }

            if (obj.dependency_trees || obj.dependencyTrees) {
                const dependencyTrees = obj.dependency_trees || obj.dependencyTrees || [];
                const treeNodesByPackageIndexMap = new Map<number | undefined, TreeNodeBucket>();
                const treeNodesByKeyMap = new Map<string, WebAppTreeNode>();
                const callback = (webAppTreeNode: WebAppTreeNode): void => {
                    const { key, packageIndex } = webAppTreeNode;
                    if (key === undefined) {
                        return;
                    }
                    const parentKey = webAppTreeNode.parent ? webAppTreeNode.parent.key : webAppTreeNode.packageIndex;

                    treeNodesByKeyMap.set(key, webAppTreeNode);

                    const existing = treeNodesByPackageIndexMap.get(packageIndex);
                    if (!existing) {
                        treeNodesByPackageIndexMap.set(packageIndex, {
                            keys: new Set([key]),
                            parentKeys: new Set([parentKey]),
                        });
                    } else {
                        existing.keys.add(key);
                        existing.parentKeys.add(parentKey);
                    }
                };

                // Build each dependency tree in its own deferred task so a large report does not block the main
                // thread. scheduleTreeBuild tracks these tasks (and the child tasks they spawn) so consumers can
                // wait for the whole forest via onDependencyTreesReady instead of rendering it half-built.
                for (let i = 0, len = dependencyTrees.length; i < len; i++) {
                    this.scheduleTreeBuild(() => {
                        const raw = dependencyTrees[i];
                        if (raw) {
                            this.#dependencyTrees.push(new WebAppTreeNode(raw, this, callback));
                        }
                    });
                }

                this.#treeNodesByPackageIndexMap = treeNodesByPackageIndexMap;
                this.#treeNodesByKeyMap = treeNodesByKeyMap;
            }
        }
    }

    get concludedLicensePackages(): readonly WebAppPackage[] {
        return this.#concludedLicensePackages;
    }

    get copyrights(): readonly WebAppCopyright[] {
        return this.#copyrights;
    }

    get declaredLicenses(): readonly string[] {
        return this.#declaredLicenses;
    }

    get declaredLicensesProcessed(): readonly string[] {
        return this.#declaredLicensesProcessed;
    }

    get dependencyTrees(): readonly WebAppTreeNode[] {
        return this.#dependencyTrees;
    }

    get dependencyTreesReady(): boolean {
        return this.#dependencyTreesReady;
    }

    get detectedLicenses(): readonly string[] {
        return this.#detectedLicenses;
    }

    get detectedLicensesProcessed(): readonly string[] {
        return this.#detectedLicensesProcessed;
    }

    get effectiveLicensePackages(): readonly WebAppPackage[] {
        return this.#effectiveLicensePackages;
    }

    get effectiveLicenses(): readonly string[] {
        return this.#effectiveLicenses;
    }

    get issues(): readonly WebAppOrtIssue[] {
        return this.#issues;
    }

    get issueResolutions(): readonly WebAppResolution[] {
        return this.#issueResolutions;
    }

    get labels(): Record<string, string> {
        return this.#labels;
    }

    get levels(): readonly number[] {
        return this.#levels;
    }

    get licenseFindingCurations(): readonly WebAppLicenseFindingCuration[] {
        return this.#licenseFindingCurations;
    }

    get licenses(): readonly WebAppLicense[] {
        return this.#licenses;
    }

    get licensesIndexesByNameMap(): ReadonlyMap<string, number> | null {
        return this.#licensesIndexesByNameMap || null;
    }

    get packageConfigurations(): readonly WebAppPackageConfiguration[] {
        return this.#packageConfigurations;
    }

    get packageCurations(): readonly WebAppPackageCuration[] {
        return this.#packageCurations;
    }

    get packages(): readonly WebAppPackage[] {
        return this.#packages;
    }

    get pathExcludes(): readonly WebAppPathExclude[] {
        return this.#pathExcludes;
    }

    get paths(): readonly WebAppPath[] {
        return this.#paths;
    }

    get projects(): readonly WebAppPackage[] {
        return this.#projects;
    }

    get repository(): WebAppRepository | undefined {
        return this.#repository;
    }

    get repositoryConfiguration(): string | undefined {
        return this.#repositoryConfiguration;
    }

    get ruleViolations(): readonly WebAppRuleViolation[] {
        return this.#ruleViolations;
    }

    get ruleViolationResolutions(): readonly WebAppResolution[] {
        return this.#ruleViolationResolutions;
    }

    get scanResults(): readonly WebAppScanResult[] {
        return this.#scanResults;
    }

    get scopeExcludes(): readonly WebAppScopeExclude[] {
        return this.#scopeExcludes;
    }

    get scopes(): readonly WebAppScope[] {
        return this.#scopes;
    }

    get severeIssueThreshold(): string | undefined {
        return this.#severeIssueThreshold;
    }

    // Open (unresolved) issues at or above the severe-issue threshold — the count that must reach zero
    // for the run to pass (an absent threshold falls back to every open issue).
    get severeOpenIssuesCount(): number {
        return countAtOrAboveThreshold(this.#statistics.openIssues, this.#severeIssueThreshold);
    }

    // Open (unresolved) rule violations at or above the severe-rule-violation threshold.
    get severeOpenRuleViolationsCount(): number {
        return countAtOrAboveThreshold(this.#statistics.openRuleViolations, this.#severeRuleViolationThreshold);
    }

    get severeRuleViolationThreshold(): string | undefined {
        return this.#severeRuleViolationThreshold;
    }

    get statistics(): Statistics {
        return this.#statistics;
    }

    get toolsMetadata(): ToolsMetadata {
        return this.#toolsMetadata;
    }

    get treeNodesByPackageIndexMap(): ReadonlyMap<number | undefined, TreeNodeBucket> | undefined {
        return this.#treeNodesByPackageIndexMap;
    }

    get treeNodesByKeyMap(): ReadonlyMap<string, WebAppTreeNode> | undefined {
        return this.#treeNodesByKeyMap;
    }

    get vulnerabilities(): readonly WebAppVulnerability[] {
        return this.#vulnerabilities;
    }

    get vulnerabilityResolutions(): readonly WebAppVulnerabilityResolution[] {
        return this.#vulnerabilityResolutions;
    }

    getCopyrightByIndex(val: number): WebAppCopyright | null {
        return this.#copyrights[val] || null;
    }

    getLicenseByIndex(val: number): WebAppLicense | null {
        return this.#licenses[val] || null;
    }

    getLicenseByName(val: string): WebAppLicense | null {
        const index = this.#licensesIndexesByNameMap.get(val);
        if (index === undefined) {
            return null;
        }
        return this.#licenses[index] || null;
    }

    getLicenseIndexByName(val: string): number | undefined {
        return this.#licensesIndexesByNameMap.get(val);
    }

    // Returns the package for an id, or an empty array when no package has that id (mirroring
    // getPackageByKey). Never null: getPackageByKey already returns [] for a missing key.
    getPackageById(val: string): WebAppPackage | [] {
        return this.getPackageByKey(this.#packagesIdtoKeyMap.get(val));
    }

    getPackageByIndex(val: number): WebAppPackage | null {
        return this.#packages[val] || null;
    }

    getPackageByKey(val: string | undefined): WebAppPackage | [] {
        if (val === undefined) {
            return [];
        }
        return this.#packagesByKeyMap.get(val) || [];
    }

    getPackageConfigurationsAsYaml(): string {
        return YAML.stringify(this.#packageConfigurationsAsPlainJsObject, {
            aliasDuplicateObjects: false,
        });
    }

    getPackageCurationByIndex(val: number): WebAppPackageCuration | null {
        return this.#packageCurations[val] || null;
    }

    getPackageCurationsAsYamlFromIndexes(values: Iterable<number> | undefined): string | null {
        const packageCurations: PlainJsObject[] = [];
        if (values) {
            for (const index of values) {
                const packageCuration = this.#packageCurationsAsPlainJsObject?.[index] || null;
                if (packageCuration) {
                    packageCurations.push(packageCuration);
                }
            }
        }

        return (
            YAML.stringify(packageCurations, {
                aliasDuplicateObjects: false,
            }) || null
        );
    }

    getPackageCurationsAsYaml(): string {
        return YAML.stringify(this.#packageCurationsAsPlainJsObject, {
            aliasDuplicateObjects: false,
        });
    }

    hasResolutions(): boolean {
        return (
            this.#issueResolutions.length > 0 ||
            this.#ruleViolationResolutions.length > 0 ||
            this.#vulnerabilityResolutions.length > 0
        );
    }

    getResolutionsAsYaml(): string {
        const messageResolution = (resolution: WebAppResolution): PlainJsObject => {
            const entry: PlainJsObject = { message: resolution.message, reason: resolution.reason };
            if (resolution.comment !== undefined) entry.comment = resolution.comment;
            return entry;
        };
        const resolutions: PlainJsObject = {};
        const vulnerabilityResolution = (resolution: WebAppVulnerabilityResolution): PlainJsObject => {
            const entry: PlainJsObject = { id: resolution.id, reason: resolution.reason };
            if (resolution.comment !== undefined) entry.comment = resolution.comment;
            return entry;
        };

        if (this.#issueResolutions.length > 0) {
            resolutions.issues = this.#issueResolutions.map(messageResolution);
        }

        if (this.#ruleViolationResolutions.length > 0) {
            resolutions.rule_violations = this.#ruleViolationResolutions.map(messageResolution);
        }

        if (this.#vulnerabilityResolutions.length > 0) {
            resolutions.vulnerabilities = this.#vulnerabilityResolutions.map(vulnerabilityResolution);
        }

        return YAML.stringify({ resolutions }, { aliasDuplicateObjects: false });
    }

    getPackageConfigurationByIndex(val: number): WebAppPackageConfiguration | null {
        return this.#packageConfigurations[val] || null;
    }

    getPackageConfigurationsAsYamlFromIndexes(values: Iterable<number> | undefined): string | null {
        const packageConfigurations: PlainJsObject[] = [];
        if (values) {
            for (const index of values) {
                const packageConfiguration = this.#packageConfigurationsAsPlainJsObject?.[index] || null;
                if (packageConfiguration) {
                    packageConfigurations.push(packageConfiguration);
                }
            }
        }

        return (
            YAML.stringify(packageConfigurations, {
                aliasDuplicateObjects: false,
            }) || null
        );
    }

    getPathByIndex(val: number): WebAppPath | null {
        return this.#paths[val] || null;
    }

    getIssuesForPackageIndex(val: number): WebAppOrtIssue[] {
        return this.#issuesByPackageIndexMap.get(val) || [];
    }

    getIssueResolutionByIndex(val: number): WebAppResolution | null {
        return this.#issueResolutions[val] || null;
    }

    getPathExcludeByIndex(val: number): WebAppPathExclude | null {
        return this.#pathExcludes[val] || null;
    }

    getRuleViolationResolutionByIndex(val: number): WebAppResolution | null {
        return this.#ruleViolationResolutions[val] || null;
    }

    getScanResultByIndex(val: number): WebAppScanResult | null {
        return this.#scanResults[val] || null;
    }

    getScopeByIndex(val: number): WebAppScope | null {
        return this.#scopes[val] || null;
    }

    getScopeByName(val: string): WebAppScope | null {
        return this.#scopesByNameMap.get(val) || null;
    }

    getScopeExcludeByIndex(val: number): WebAppScopeExclude | null {
        return this.#scopeExcludes[val] || null;
    }

    getTreeNodeByKey(val: string | number): WebAppTreeNode | null {
        const key = val.toString();
        return (key && this.#treeNodesByKeyMap?.get(key)) || null;
    }

    getTreeNodeParentKeysByIndex(val: number): TreeNodeBucket | null {
        return this.#treeNodesByPackageIndexMap?.get(val) || null;
    }

    /**
     * Subscribe to the completion of the asynchronous dependency-tree construction. The listener runs once,
     * either immediately (when the trees are already built or there are none) or when the last deferred build
     * task drains. Returns an unsubscribe function so a caller that unmounts first can detach.
     */
    onDependencyTreesReady(listener: () => void): () => void {
        if (this.#dependencyTreesReady) {
            listener();
            return () => {};
        }

        this.#treesReadyListeners.add(listener);
        return () => {
            this.#treesReadyListeners.delete(listener);
        };
    }

    /**
     * Run a unit of dependency-tree construction on a later task so a large tree does not block the main
     * thread, tracking it so onDependencyTreesReady can tell when the whole forest is done. Child tasks are
     * scheduled (and counted) while a parent task runs, so the count only reaches zero once every node is
     * built.
     */
    scheduleTreeBuild(build: () => void): void {
        if (this.#pendingTreeBuilds === 0) {
            this.#dependencyTreesReady = false;
        }

        this.#pendingTreeBuilds += 1;
        setTimeout(() => {
            try {
                build();
            } finally {
                this.#pendingTreeBuilds -= 1;
                if (this.#pendingTreeBuilds === 0) {
                    this.#dependencyTreesReady = true;
                    const listeners = Array.from(this.#treesReadyListeners);
                    this.#treesReadyListeners.clear();
                    for (const listener of listeners) {
                        listener();
                    }
                }
            }
        }, 0);
    }

    getRuleViolationsForPackageIndex(val: number): WebAppRuleViolation[] {
        return this.#ruleViolationsByPackageIndexMap.get(val) || [];
    }

    getVulnerabilitiesForPackageIndex(val: number): WebAppVulnerability[] {
        return this.#vulnerabilitiesByPackageIndexMap.get(val) || [];
    }

    getVulnerabilityResolutionByIndex(val: number): WebAppVulnerabilityResolution | null {
        return this.#vulnerabilityResolutions[val] || null;
    }

    hasConcludedLicenses(): boolean {
        return this.#concludedLicensePackages.length > 0;
    }

    hasDeclaredLicenses(): boolean {
        return this.#declaredLicenses.length > 0;
    }

    hasDeclaredLicensesProcessed(): boolean {
        return this.#declaredLicensesProcessed.length > 0;
    }

    hasDetectedLicenses(): boolean {
        return this.#detectedLicenses.length > 0;
    }

    hasDetectedLicensesProcessed(): boolean {
        return this.#detectedLicensesProcessed.length > 0;
    }

    hasEffectiveLicenses(): boolean {
        return this.#effectiveLicenses.length > 0;
    }

    hasExcludes(): boolean {
        if (this.#pathExcludes.length > 0 || this.#scopeExcludes.length > 0) {
            return true;
        }

        return false;
    }

    hasIssues(): boolean {
        const {
            openIssues: { errors, hints, warnings },
        } = this.#statistics;

        return errors > 0 || hints > 0 || warnings > 0;
    }

    hasIssuesForPackageIndex(val: number): boolean {
        return this.#issuesByPackageIndexMap.has(val);
    }

    hasLabels(): boolean {
        return Object.keys(this.#labels).length > 0;
    }

    hasLevels(): boolean {
        return this.#levels.length > 0;
    }

    hasPackageConfigurations(): boolean {
        return this.#packageConfigurations.length > 0;
    }

    hasPackageCurations(): boolean {
        return this.#packageCurations.length > 0;
    }

    hasPathExcludes(): boolean {
        return this.#pathExcludes.length > 0;
    }

    hasRepositoryConfiguration(): boolean {
        if (this.#repositoryConfiguration && this.#repositoryConfiguration.replace(/(\r\n|\n|\r)/gm, "") !== "--- {}") {
            return true;
        }

        return false;
    }

    hasRuleViolations(): boolean {
        const {
            openRuleViolations: { errors, hints, warnings },
        } = this.#statistics;

        return errors > 0 || hints > 0 || warnings > 0;
    }

    hasRuleViolationsForPackageIndex(val: number): boolean {
        return this.#ruleViolationsByPackageIndexMap.has(val);
    }

    hasScopes(): boolean {
        return this.#scopes.length > 0;
    }

    hasScopeExcludes(): boolean {
        return this.#scopeExcludes.length > 0;
    }

    hasVulnerabilities(): boolean {
        return this.#vulnerabilities.length > 0;
    }
}

export default WebAppEvaluatedModel;
