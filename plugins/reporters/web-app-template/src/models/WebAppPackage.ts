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

import { parseSpdxLicenseExpression, type SpdxSimpleLicenseExpression } from "@/lib/spdx-license-expressions";
import RemoteArtifact from "@/models/RemoteArtifact";
import VcsInfo from "@/models/VcsInfo";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppFinding from "@/models/WebAppFinding";
import type WebAppOrtIssue from "@/models/WebAppOrtIssue";
import type WebAppPackageConfiguration from "@/models/WebAppPackageConfiguration";
import type WebAppPackageCuration from "@/models/WebAppPackageCuration";
import type WebAppPath from "@/models/WebAppPath";
import type WebAppPathExclude from "@/models/WebAppPathExclude";
import type WebAppRuleViolation from "@/models/WebAppRuleViolation";
import type WebAppScanResult from "@/models/WebAppScanResult";
import type WebAppScope from "@/models/WebAppScope";
import type WebAppScopeExclude from "@/models/WebAppScopeExclude";
import type { EvaluatedModelPackage } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

// Collect the distinct SPDX simple expressions from a set of license expressions, breaking any
// composite expression (e.g. "Apache-2.0 AND MIT") into its individual licenses so each is filterable.
function collectSpdxSimpleExpressions(expressions: Iterable<string>): Set<SpdxSimpleLicenseExpression> {
    const simpleExpressions = new Set<SpdxSimpleLicenseExpression>();
    for (const expression of expressions) {
        for (const simpleExpression of parseSpdxLicenseExpression(expression).simpleExpressions) {
            simpleExpressions.add(simpleExpression);
        }
    }
    return simpleExpressions;
}

// Severity rank so a finding can be tested against the run's severe threshold (ERROR is the most severe,
// then WARNING, then HINT). An absent or unknown threshold has nothing below it, so every finding counts.
const SEVERITY_RANK: Record<string, number> = { ERROR: 3, WARNING: 2, HINT: 1 };

// Counts the open (unresolved) findings whose severity is at or above the given severe threshold — the ones
// that gate the run. Per-package counterpart of WebAppEvaluatedModel's report-wide countAtOrAboveThreshold.
function countSevereOpenFindings(
    findings: readonly { readonly isResolved: boolean; readonly severity: string | undefined }[],
    threshold: string | undefined,
): number {
    const cutoff = threshold ? (SEVERITY_RANK[threshold.toUpperCase()] ?? 0) : 0;
    return findings.filter((finding) => {
        if (finding.isResolved) return false;
        const rank = finding.severity ? (SEVERITY_RANK[finding.severity.toUpperCase()] ?? 0) : 0;
        return rank >= cutoff;
    }).length;
}

class WebAppPackage {
    #_id: number | undefined;

    #authors: Set<string> = new Set();

    #binaryArtifact: RemoteArtifact | undefined;

    #concludedLicense: string | undefined;

    #concludedSpdxSimpleExpressions: ReadonlySet<SpdxSimpleLicenseExpression> | undefined;

    #curations: WebAppPackageCuration[] | undefined;

    #curationsAsYaml: string | null | undefined;

    #curationIndexes: Set<number> = new Set();

    #declaredLicenses: Set<string> = new Set();

    #declaredLicensesIndexes: Set<number> = new Set();

    #declaredLicensesSpdxExpression: string | undefined;

    #declaredLicensesMapped: Set<string> = new Set();

    #declaredLicensesMappedIndexes: Set<number> = new Set();

    #declaredLicensesUnmapped: Set<string> = new Set();

    #declaredLicensesUnmappedIndexes: Set<number> = new Set();

    #declaredSpdxSimpleExpressions: ReadonlySet<SpdxSimpleLicenseExpression> | undefined;

    #definitionFilePath: string | undefined;

    #description: string | undefined;

    #detectedExcludedLicenses: Set<string> = new Set();

    #detectedExcludedLicensesIndexes: Set<number> = new Set();

    #detectedLicenses: Set<string> = new Set();

    #detectedLicensesIndexes: Set<number> = new Set();

    #detectedLicensesProcessed: Set<string> = new Set();

    #detectedLicensesProcessedIndexes: Set<number> = new Set();

    #detectedSpdxSimpleExpressions: ReadonlySet<SpdxSimpleLicenseExpression> | undefined;

    #effectiveLicense: string | undefined;

    #effectiveSpdxSimpleExpressions: ReadonlySet<SpdxSimpleLicenseExpression> | undefined;

    #excludedFindingsIndexes: number[] = [];

    #findings: WebAppFinding[] = [];

    #homepageUrl: string | undefined;

    #id: string | undefined;

    #isExcluded: boolean = false;

    #isProject: boolean = false;

    #issues: WebAppOrtIssue[] | undefined;

    #levels: Set<number> = new Set();

    #labels: Map<string, string> = new Map();

    #packageConfigurations: WebAppPackageConfiguration[] | undefined;

    #packageConfigurationsAsYaml: string | null | undefined;

    #packageConfigurationIndexes: Set<number> = new Set();

    #pathExcludes: WebAppPathExclude[] | undefined;

    #pathExcludeIndexes: Set<number> = new Set();

    #pathExcludeReasons: Set<string> | undefined;

    #paths: WebAppPath[] | undefined;

    #pathIndexes: ReadonlyArray<number> | Set<number> = new Set();

    #projectIndexes: Set<number> | undefined;

    #purl: string | undefined;

    #scanResultsIndexes: ReadonlyArray<number> | undefined;

    #scopeExcludes: WebAppScopeExclude[] | undefined;

    #scopeExcludeIndexes: Set<number> = new Set();

    #scopeExcludeReasons: Set<string> | undefined;

    #scopes: WebAppScope[] | undefined;

    #scopeIndexes: Set<number> = new Set();

    #scopeNames: Set<string> | undefined;

    #sourceArtifact: RemoteArtifact | undefined;

    #ruleViolations: WebAppRuleViolation[] | undefined;

    #vcs: VcsInfo = new VcsInfo();

    #vcsProcessed: VcsInfo = new VcsInfo();

    #webAppEvaluatedModel: WebAppEvaluatedModel | undefined;

    key: string | undefined;

    constructor(obj?: EvaluatedModelPackage, webAppEvaluatedModel?: WebAppEvaluatedModel) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.authors) {
                this.#authors = new Set(obj.authors);
            }

            if (obj.binary_artifact || obj.binaryArtifact) {
                const binaryArtifact = obj.binary_artifact || obj.binaryArtifact;
                this.#binaryArtifact = new RemoteArtifact(binaryArtifact);
            }

            if (obj.concluded_license || obj.concludedLicense) {
                this.#concludedLicense = obj.concluded_license || obj.concludedLicense;
            }

            if (obj.curations) {
                this.#curationIndexes = new Set(obj.curations);
            }

            if (obj.declared_licenses || obj.declaredLicenses) {
                const declaredLicensesIndexes = obj.declared_licenses || obj.declaredLicenses;
                this.#declaredLicensesIndexes = new Set(declaredLicensesIndexes);
            }

            if (obj.declared_licenses_processed || obj.declaredLicensesProcessed) {
                const declaredLicensesProcessed = obj.declared_licenses_processed || obj.declaredLicensesProcessed;

                if (declaredLicensesProcessed) {
                    if (declaredLicensesProcessed.mapped_licenses || declaredLicensesProcessed.mappedLicenses) {
                        const mappedLicenses =
                            declaredLicensesProcessed.mapped_licenses || declaredLicensesProcessed.mappedLicenses;
                        if (mappedLicenses && mappedLicenses.length > 0) {
                            this.#declaredLicensesMappedIndexes = new Set(mappedLicenses);
                        }
                    }

                    if (declaredLicensesProcessed.spdx_expression || declaredLicensesProcessed.spdxExpression) {
                        const spdxExpression =
                            declaredLicensesProcessed.spdx_expression || declaredLicensesProcessed.spdxExpression;
                        if (spdxExpression) {
                            this.#declaredLicensesSpdxExpression = spdxExpression;
                        }
                    }

                    if (declaredLicensesProcessed.unmapped_licenses || declaredLicensesProcessed.unmappedLicenses) {
                        const unmappedLicenses =
                            declaredLicensesProcessed.unmapped_licenses || declaredLicensesProcessed.unmappedLicenses;
                        if (unmappedLicenses && unmappedLicenses.length > 0) {
                            this.#declaredLicensesUnmappedIndexes = new Set(unmappedLicenses);
                        }
                    }
                }
            }

            if (obj.definition_file_path || obj.definitionFilePath) {
                this.#definitionFilePath = obj.definition_file_path || obj.definitionFilePath;
            }

            if (obj.description) {
                this.#description = obj.description;
            }

            if (obj.detected_excluded_licenses || obj.detectedExcludedLicenses) {
                const detectedExcludedLicensesIndexes = obj.detected_excluded_licenses || obj.detectedExcludedLicenses;
                this.#detectedExcludedLicensesIndexes = new Set(detectedExcludedLicensesIndexes);
            }

            if (obj.detected_licenses || obj.detectedLicenses) {
                const detectedLicensesIndexes = obj.detected_licenses || obj.detectedLicenses;
                this.#detectedLicensesIndexes = new Set(detectedLicensesIndexes);
            }

            if (obj.effective_license || obj.effectiveLicense) {
                this.#effectiveLicense = obj.effective_license || obj.effectiveLicense;
            }

            if (obj.homepage_url || obj.homepageUrl) {
                this.#homepageUrl = obj.homepage_url || obj.homepageUrl;
            }

            if (obj.id) {
                this.#id = obj.id;
            }

            if (obj.is_excluded || obj.isExcluded) {
                this.#isExcluded = (obj.is_excluded || obj.isExcluded) ?? false;
            }

            if (obj.findings && webAppEvaluatedModel) {
                const findings = obj.findings;
                setTimeout(() => {
                    for (let i = 0, len = findings.length; i < len; i++) {
                        const finding = findings[i];
                        if (!finding) {
                            continue;
                        }
                        if (finding.path_excludes || finding.pathExcludes) {
                            this.#excludedFindingsIndexes.push(i);
                        }

                        this.#findings.push(new WebAppFinding(finding, webAppEvaluatedModel));
                    }
                }, 0);
            }

            if (obj.is_project || obj.isProject) {
                this.#isProject = (obj.is_project || obj.isProject) ?? false;
            }

            if (obj.labels) {
                Object.entries(obj.labels).forEach(([key, value]) => {
                    this.#labels.set(key, value);
                });
            }

            if (obj.levels) {
                this.#levels = new Set(obj.levels);
            }

            if (obj.package_configurations || obj.packageConfigurations) {
                this.#packageConfigurationIndexes = new Set(obj.package_configurations || obj.packageConfigurations);
            }

            if (obj.path_excludes || obj.pathExcludes) {
                const pathExcludeIndexes = obj.path_excludes || obj.pathExcludes;
                this.#pathExcludeIndexes = new Set(pathExcludeIndexes);
            }

            if (obj.paths) {
                this.#pathIndexes = obj.paths;
            }

            if (obj.purl) {
                this.#purl = obj.purl;
            }

            if (obj.scan_results || obj.scanResults) {
                this.#scanResultsIndexes = obj.scan_results || obj.scanResults;
            }

            if (obj.scope_excludes || obj.scopeExcludes) {
                const scopeExcludesIndexes = obj.scope_excludes || obj.scopeExcludes;
                this.#scopeExcludeIndexes = new Set(scopeExcludesIndexes);
            }

            if (obj.scopes) {
                this.#scopeIndexes = new Set(obj.scopes);
            }

            if (obj.source_artifact || obj.sourceArtifact) {
                const sourceArtifact = obj.source_artifact || obj.sourceArtifact;
                this.#sourceArtifact = new RemoteArtifact(sourceArtifact);
            }

            if (obj.vcs) {
                this.#vcs = new VcsInfo(obj.vcs);
            }

            if (obj.vcs_processed || obj.vcsProcessed) {
                const vcsProcessed = obj.vcs_processed || obj.vcsProcessed;
                this.#vcsProcessed = new VcsInfo(vcsProcessed);
            }

            if (webAppEvaluatedModel) {
                this.#webAppEvaluatedModel = webAppEvaluatedModel;
                const getLicenseNames = (indexes: Set<number>): Set<string> => {
                    const licenses: string[] = [];
                    indexes.forEach((index) => {
                        const webAppLicense = webAppEvaluatedModel.getLicenseByIndex(index);
                        if (webAppLicense?.id) {
                            licenses.push(webAppLicense.id);
                        }
                    });

                    return new Set(licenses.sort());
                };

                if (this.#declaredLicensesIndexes.size !== 0) {
                    this.#declaredLicenses = getLicenseNames(this.#declaredLicensesIndexes);
                }

                if (this.#declaredLicensesMappedIndexes.size !== 0) {
                    this.#declaredLicensesMapped = getLicenseNames(this.#declaredLicensesMappedIndexes);
                }

                if (this.#declaredLicensesUnmappedIndexes.size !== 0) {
                    this.#declaredLicensesUnmapped = getLicenseNames(this.#declaredLicensesUnmappedIndexes);
                }

                if (this.#detectedLicensesIndexes.size !== 0) {
                    this.#detectedLicenses = getLicenseNames(this.#detectedLicensesIndexes);
                }

                this.#detectedLicensesProcessedIndexes = new Set(this.#detectedLicensesIndexes);

                if (this.#detectedExcludedLicensesIndexes.size !== 0) {
                    this.#detectedExcludedLicensesIndexes.forEach((value) => {
                        this.#detectedLicensesProcessedIndexes.delete(value);
                    });

                    this.#detectedLicensesProcessed = getLicenseNames(this.#detectedLicensesProcessedIndexes);
                } else {
                    this.#detectedLicensesProcessed = this.#detectedLicenses;
                }
            }

            this.key = randomStringGenerator(20);
        }
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get authors(): ReadonlySet<string> {
        return this.#authors;
    }

    get binaryArtifact(): RemoteArtifact | undefined {
        return this.#binaryArtifact;
    }

    get concludedLicense(): string | undefined {
        return this.#concludedLicense;
    }

    /** The concluded license decomposed into its individual SPDX simple expressions (for filtering). */
    get concludedSpdxSimpleExpressions(): ReadonlySet<SpdxSimpleLicenseExpression> {
        if (this.#concludedSpdxSimpleExpressions === undefined) {
            this.#concludedSpdxSimpleExpressions = collectSpdxSimpleExpressions(
                this.#concludedLicense ? [this.#concludedLicense] : [],
            );
        }
        return this.#concludedSpdxSimpleExpressions;
    }

    get curations(): WebAppPackageCuration[] | undefined {
        if (!this.#curations && this.#webAppEvaluatedModel) {
            this.#curations = [];
            this.#curationIndexes.forEach((index) => {
                const webAppPackageCuration = this.#webAppEvaluatedModel?.getPackageCurationByIndex(index) || null;
                if (webAppPackageCuration) {
                    this.#curations?.push(webAppPackageCuration);
                }
            });
        }

        return this.#curations;
    }

    get curationsAsYaml(): string | null | undefined {
        if (!this.#curationsAsYaml && this.#webAppEvaluatedModel) {
            this.#curationsAsYaml = this.#webAppEvaluatedModel.getPackageCurationsAsYamlFromIndexes(
                this.curationIndexes,
            );
        }

        return this.#curationsAsYaml;
    }

    get curationIndexes(): ReadonlySet<number> {
        return this.#curationIndexes;
    }

    get declaredLicenses(): ReadonlySet<string> {
        return this.#declaredLicenses;
    }

    get declaredLicensesIndexes(): ReadonlySet<number> {
        return this.#declaredLicensesIndexes;
    }

    get declaredLicensesMapped(): ReadonlySet<string> {
        return this.#declaredLicensesMapped;
    }

    get declaredLicensesSpdxExpression(): string | undefined {
        return this.#declaredLicensesSpdxExpression;
    }

    get declaredLicensesUnmapped(): ReadonlySet<string> {
        return this.#declaredLicensesUnmapped;
    }

    /** The mapped declared licenses decomposed into their individual SPDX simple expressions (for filtering). */
    get declaredSpdxSimpleExpressions(): ReadonlySet<SpdxSimpleLicenseExpression> {
        if (this.#declaredSpdxSimpleExpressions === undefined) {
            this.#declaredSpdxSimpleExpressions = collectSpdxSimpleExpressions(this.#declaredLicensesMapped);
        }
        return this.#declaredSpdxSimpleExpressions;
    }

    get definitionFilePath(): string | undefined {
        return this.#definitionFilePath;
    }

    get description(): string | undefined {
        return this.#description;
    }

    get detectedExcludedLicenses(): ReadonlySet<string> {
        return this.#detectedExcludedLicenses;
    }

    get detectedExcludedLicensesIndexes(): ReadonlySet<number> {
        return this.#detectedExcludedLicensesIndexes;
    }

    get detectedLicenses(): ReadonlySet<string> {
        return this.#detectedLicenses;
    }

    get detectedLicensesIndexes(): ReadonlySet<number> {
        return this.#detectedLicensesIndexes;
    }

    get detectedLicensesProcessed(): ReadonlySet<string> {
        return this.#detectedLicensesProcessed;
    }

    get detectedLicensesProcessedIndexes(): ReadonlySet<number> {
        return this.#detectedLicensesProcessedIndexes;
    }

    /** The detected licenses decomposed into their individual SPDX simple expressions (for filtering). */
    get detectedSpdxSimpleExpressions(): ReadonlySet<SpdxSimpleLicenseExpression> {
        if (this.#detectedSpdxSimpleExpressions === undefined) {
            this.#detectedSpdxSimpleExpressions = collectSpdxSimpleExpressions(this.#detectedLicensesProcessed);
        }
        return this.#detectedSpdxSimpleExpressions;
    }

    get effectiveLicense(): string | undefined {
        return this.#effectiveLicense;
    }

    /** The effective license decomposed into its individual SPDX simple expressions (for filtering). */
    get effectiveSpdxSimpleExpressions(): ReadonlySet<SpdxSimpleLicenseExpression> {
        if (this.#effectiveSpdxSimpleExpressions === undefined) {
            this.#effectiveSpdxSimpleExpressions = collectSpdxSimpleExpressions(
                this.#effectiveLicense ? [this.#effectiveLicense] : [],
            );
        }
        return this.#effectiveSpdxSimpleExpressions;
    }

    get excludeReasons(): Set<string> {
        const { pathExcludeReasons, scopeExcludeReasons } = this;

        return new Set([...(pathExcludeReasons ?? []), ...(scopeExcludeReasons ?? [])]);
    }

    get excludedFindings(): WebAppFinding[] {
        // Resolve fresh on each read: #findings and #excludedFindingsIndexes are filled asynchronously
        // (setTimeout in the constructor), so a memoised empty first read would persist.
        const excludedFindings: WebAppFinding[] = [];
        this.#excludedFindingsIndexes.forEach((index) => {
            const finding = this.#findings[index];
            if (finding) {
                excludedFindings.push(finding);
            }
        });

        return excludedFindings;
    }

    get excludedFindingsIndexes(): readonly number[] {
        return this.#excludedFindingsIndexes;
    }

    get findings(): readonly WebAppFinding[] {
        return this.#findings;
    }

    get homepageUrl(): string | undefined {
        return this.#homepageUrl;
    }

    get id(): string | undefined {
        return this.#id;
    }

    get isExcluded(): boolean {
        return this.#isExcluded;
    }

    get isProject(): boolean {
        return this.#isProject;
    }

    get issues(): WebAppOrtIssue[] {
        if (!this.#issues && this.#webAppEvaluatedModel && this.#_id !== undefined) {
            this.#issues = this.#webAppEvaluatedModel.getIssuesForPackageIndex(this.#_id);
        }

        return this.#issues ?? [];
    }

    get labels(): ReadonlyMap<string, string> {
        return this.#labels;
    }

    get levels(): ReadonlySet<number> {
        return this.#levels;
    }

    // Open (unresolved) technical issues in this package.
    get openIssuesCount(): number {
        return this.issues.filter((issue) => !issue.isResolved).length;
    }

    // Open (unresolved) policy violations in this package.
    get openRuleViolationsCount(): number {
        return this.ruleViolations.filter((violation) => !violation.isResolved).length;
    }

    // Open (unresolved) vulnerabilities affecting this package.
    get openVulnerabilitiesCount(): number {
        if (this.#_id === undefined || !this.#webAppEvaluatedModel) {
            return 0;
        }
        return this.#webAppEvaluatedModel
            .getVulnerabilitiesForPackageIndex(this.#_id)
            .filter((vulnerability) => !vulnerability.isResolved).length;
    }

    get packageIndex(): number | undefined {
        return this.#_id;
    }

    get packageConfigurations(): WebAppPackageConfiguration[] | undefined {
        if (!this.#packageConfigurations && this.#webAppEvaluatedModel) {
            this.#packageConfigurations = [];
            this.#packageConfigurationIndexes.forEach((index) => {
                const webAppPackageConfiguration =
                    this.#webAppEvaluatedModel?.getPackageConfigurationByIndex(index) || null;
                if (webAppPackageConfiguration) {
                    this.#packageConfigurations?.push(webAppPackageConfiguration);
                }
            });
        }

        return this.#packageConfigurations;
    }

    get packageConfigurationsAsYaml(): string | null | undefined {
        if (!this.#packageConfigurationsAsYaml && this.#webAppEvaluatedModel) {
            this.#packageConfigurationsAsYaml = this.#webAppEvaluatedModel.getPackageConfigurationsAsYamlFromIndexes(
                this.packageConfigurationIndexes,
            );
        }

        return this.#packageConfigurationsAsYaml;
    }

    get packageConfigurationIndexes(): ReadonlySet<number> {
        return this.#packageConfigurationIndexes;
    }

    get pathExcludes(): WebAppPathExclude[] | undefined {
        if (!this.#pathExcludes && this.#webAppEvaluatedModel) {
            this.#pathExcludes = [];
            this.#pathExcludeIndexes.forEach((index) => {
                const webAppPathExclude = this.#webAppEvaluatedModel?.getPathExcludeByIndex(index) || null;
                if (webAppPathExclude) {
                    this.#pathExcludes?.push(webAppPathExclude);
                }
            });
        }

        return this.#pathExcludes;
    }

    get pathExcludeIndexes(): ReadonlySet<number> {
        return this.#pathExcludeIndexes;
    }

    get pathExcludeReasons(): Set<string> | undefined {
        if (!this.#pathExcludeReasons && this.#webAppEvaluatedModel) {
            this.#pathExcludeReasons = new Set();

            this.#pathExcludeIndexes.forEach((index) => {
                const webAppPathExclude = this.#webAppEvaluatedModel?.getPathExcludeByIndex(index) || null;
                if (webAppPathExclude?.reason) {
                    this.#pathExcludeReasons?.add(webAppPathExclude.reason);
                }
            });
        }

        return this.#pathExcludeReasons;
    }

    get pathIndexes(): ReadonlyArray<number> | Set<number> {
        return this.#pathIndexes;
    }

    get paths(): WebAppPath[] | undefined {
        if (!this.#paths && this.#webAppEvaluatedModel) {
            this.#paths = [];
            this.#pathIndexes.forEach((index) => {
                const webAppPath = this.#webAppEvaluatedModel?.getPathByIndex(index) || null;
                if (webAppPath) {
                    this.#paths?.push(webAppPath);
                }
            });
        }

        return this.#paths;
    }

    get projectIndexes(): Set<number> | undefined {
        if (!this.#projectIndexes && this.#webAppEvaluatedModel) {
            this.#projectIndexes = new Set();
            if (this.#isProject) {
                if (this.#_id !== undefined) {
                    this.#projectIndexes.add(this.#_id);
                }
            } else {
                this.#pathIndexes.forEach((index) => {
                    const webAppPath = this.#webAppEvaluatedModel?.getPathByIndex(index) || null;
                    if (webAppPath && webAppPath.projectIndex !== undefined) {
                        this.#projectIndexes?.add(webAppPath.projectIndex);
                    }
                });
            }
        }

        return this.#projectIndexes;
    }

    get purl(): string | undefined {
        return this.#purl;
    }

    get ruleViolations(): WebAppRuleViolation[] {
        if (!this.#ruleViolations && this.#webAppEvaluatedModel && this.#_id !== undefined) {
            this.#ruleViolations = this.#webAppEvaluatedModel.getRuleViolationsForPackageIndex(this.#_id);
        }

        return this.#ruleViolations ?? [];
    }

    get scanResults(): Array<WebAppScanResult | null> {
        // Resolve fresh on each read rather than memoising: the model populates its scan results
        // asynchronously (setTimeout), so a first read taken before they load would otherwise pin an
        // all-null array for the package's lifetime.
        const scanResults: Array<WebAppScanResult | null> = [];
        const indexes = this.#scanResultsIndexes ?? [];
        for (let i = 0, len = indexes.length; i < len; i++) {
            const index = indexes[i];
            if (index !== undefined) {
                scanResults.push(this.#webAppEvaluatedModel?.getScanResultByIndex(index) ?? null);
            }
        }

        return scanResults;
    }

    get scopeExcludes(): WebAppScopeExclude[] | undefined {
        if (!this.#scopeExcludes && this.#webAppEvaluatedModel) {
            this.#scopeExcludes = [];
            this.#scopeExcludeIndexes.forEach((index) => {
                const webAppScopeExclude = this.#webAppEvaluatedModel?.getScopeExcludeByIndex(index) || null;
                if (webAppScopeExclude) {
                    this.#scopeExcludes?.push(webAppScopeExclude);
                }
            });
        }

        return this.#scopeExcludes;
    }

    get scopeExcludeIndexes(): ReadonlySet<number> {
        return this.#scopeExcludeIndexes;
    }

    get scopeExcludeReasons(): Set<string> | undefined {
        if (!this.#scopeExcludeReasons) {
            this.#scopeExcludeReasons = new Set();

            this.#scopeExcludeIndexes.forEach((index) => {
                const webAppScopeExclude = this.#webAppEvaluatedModel?.getScopeExcludeByIndex(index) || null;
                if (webAppScopeExclude?.reason) {
                    this.#scopeExcludeReasons?.add(webAppScopeExclude.reason);
                }
            });
        }

        return this.#scopeExcludeReasons;
    }

    get scopeIndexes(): ReadonlySet<number> {
        return this.#scopeIndexes;
    }

    get scopeNames(): Set<string> | undefined {
        if (!this.#scopeNames) {
            this.#scopeNames = new Set();

            this.#scopeIndexes.forEach((index) => {
                const webAppScope = this.#webAppEvaluatedModel?.getScopeByIndex(index) || null;
                if (webAppScope?.name) {
                    this.#scopeNames?.add(webAppScope.name);
                }
            });
        }

        return this.#scopeNames;
    }

    get scopes(): WebAppScope[] | undefined {
        if (!this.#scopes && this.#webAppEvaluatedModel) {
            this.#scopes = [];
            this.#scopeIndexes.forEach((index) => {
                const webAppScope = this.#webAppEvaluatedModel?.getScopeByIndex(index) || null;
                if (webAppScope) {
                    this.#scopes?.push(webAppScope);
                }
            });
        }
        return this.#scopes;
    }

    // Open technical issues in this package at or above the run's severe issue threshold.
    get severeOpenIssuesCount(): number {
        return countSevereOpenFindings(this.issues, this.#webAppEvaluatedModel?.severeIssueThreshold);
    }

    // Open policy violations in this package at or above the run's severe rule-violation threshold.
    get severeOpenRuleViolationsCount(): number {
        return countSevereOpenFindings(this.ruleViolations, this.#webAppEvaluatedModel?.severeRuleViolationThreshold);
    }

    get sourceArtifact(): RemoteArtifact | undefined {
        return this.#sourceArtifact;
    }

    get vcs(): VcsInfo {
        return this.#vcs;
    }

    get vcsProcessed(): VcsInfo {
        return this.#vcsProcessed;
    }

    hasAuthors(): boolean {
        return this.#authors.size !== 0;
    }

    hasConcludedLicense(): boolean {
        return !!this.#concludedLicense && this.#concludedLicense.length !== 0;
    }

    hasCurations(): boolean {
        return this.#curationIndexes.size !== 0;
    }

    hasDeclaredLicenses(): boolean {
        return this.#declaredLicenses.size !== 0;
    }

    hasDeclaredLicensesMapped(): boolean {
        return !!this.#declaredLicensesMapped && this.#declaredLicensesMapped.size !== 0;
    }

    hasDeclaredLicensesSpdxExpression(): boolean {
        return !!this.#declaredLicensesSpdxExpression && this.#declaredLicensesSpdxExpression.length !== 0;
    }

    hasDeclaredLicensesUnmapped(): boolean {
        return !!this.#declaredLicensesUnmapped && this.#declaredLicensesUnmapped.size !== 0;
    }

    hasDetectedLicenses(): boolean {
        return this.#detectedLicenses.size !== 0;
    }

    hasDetectedExcludedLicenses(): boolean {
        return this.#detectedExcludedLicenses.size !== 0;
    }

    hasEffectiveLicense(): boolean {
        return !!this.#effectiveLicense && this.#effectiveLicense.length !== 0;
    }

    hasExcludedFindings(): boolean {
        return this.#excludedFindingsIndexes.length > 0;
    }

    hasFindings(): boolean {
        return this.#findings.length > 0;
    }

    hasIssues(): boolean {
        return this.issues.length > 0;
    }

    hasLabels(): boolean {
        return this.#labels.size > 0;
    }

    hasLevel(val: number): boolean {
        return this.#levels.has(val);
    }

    hasLicenses(): boolean {
        return this.declaredLicenses.size !== 0 || this.detectedLicenses.size !== 0;
    }

    hasPackageConfigurations(): boolean {
        return this.#packageConfigurationIndexes.size !== 0;
    }

    hasPathExcludes(): boolean {
        return this.pathExcludeIndexes.size !== 0;
    }

    hasPaths(): boolean {
        return !!this.paths && this.paths.length > 0;
    }

    hasScopeIndex(val: number): boolean {
        return this.#scopeIndexes.has(val);
    }

    hasScopeExcludes(): boolean {
        return this.scopeExcludeIndexes.size !== 0;
    }

    hasScopes(): boolean {
        return !!this.scopes && this.scopes.length > 0;
    }

    hasRuleViolations(): boolean {
        return this.ruleViolations.length > 0;
    }
}

export default WebAppPackage;
