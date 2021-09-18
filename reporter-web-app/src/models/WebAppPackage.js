/*
 * Copyright (C) 2019-2021 HERE Europe B.V.
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

import RemoteArtifact from './RemoteArtifact';
import VcsInfo from './VcsInfo';
import WebAppFinding from './WebAppFinding';
import { randomStringGenerator } from '../utils';

class WebAppPackage {
    #_id;

    #binaryArtifact;

    #concludedLicense;

    #curations = [];

    #declaredLicenses = new Set();

    #declaredLicensesIndexes = new Set();

    #declaredLicensesSpdxExpression;

    #declaredLicensesMapped = new Set();

    #declaredLicensesMappedIndexes = new Set();

    #declaredLicensesUnmapped = new Set();

    #declaredLicensesUnmappedIndexes = new Set();

    #definitionFilePath;

    #description;

    #detectedExcludedLicenses = new Set();

    #detectedExcludedLicensesIndexes = new Set();

    #detectedLicenses = new Set();

    #detectedLicensesIndexes = new Set();

    #detectedLicensesProcessed = new Set();
    
    #excludedFindings;

    #excludedFindingsIndexes = [];

    #findings = [];

    #homepageUrl;

    #id;

    #isExcluded = false;

    #isProject = false;

    #issues;

    #levels = new Set([]);

    #pathExcludes;

    #pathExcludeIndexes = new Set();

    #pathExcludeReasons;

    #paths;

    #pathIndexes = new Set();

    #projectIndexes;

    #purl;

    #scanResultsIndexes;

    #scanResults;

    #scopeExcludes;

    #scopeExcludeIndexes = new Set();

    #scopeExcludeReasons;

    #scopes;

    #scopeIndexes = new Set();

    #scopeNames;

    #sourceArtifact;

    #ruleViolations;

    #vcs = new VcsInfo();

    #vcsProcessed = new VcsInfo();

    #webAppOrtResult;

    constructor(obj, webAppOrtResult) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.binary_artifact || obj.binaryArtifact) {
                const binaryArtifact = obj.binary_artifact || obj.binaryArtifact;
                this.#binaryArtifact = new RemoteArtifact(binaryArtifact);
            }

            if (obj.concluded_license || obj.concludedLicense) {
                this.#concludedLicense = obj.concluded_license
                    || obj.concludedLicense;
            }

            if (obj.curations) {
                this.#curations = obj.curations;
            }

            if (obj.declared_licenses || obj.declaredLicenses) {
                const declaredLicensesIndexes = obj.declared_licenses
                    || obj.declaredLicenses;
                this.#declaredLicensesIndexes = new Set(declaredLicensesIndexes);
            }

            if (obj.declared_licenses_processed || obj.declaredLicensesProcessed) {
                const declaredLicensesProcessed = obj.declared_licenses_processed
                    || obj.declaredLicensesProcessed;

                if (declaredLicensesProcessed.mapped_licenses || declaredLicensesProcessed.mappedLicenses) {
                    const mappedLicenses = declaredLicensesProcessed.mapped_licenses
                        || declaredLicensesProcessed.mappedLicenses;
                    if (mappedLicenses && mappedLicenses.length > 0) {
                        this.#declaredLicensesMappedIndexes = new Set(mappedLicenses);
                    }
                }

                if (declaredLicensesProcessed.spdx_expression || declaredLicensesProcessed.spdxExpression) {
                    const spdxExpression = declaredLicensesProcessed.spdx_expression
                        || declaredLicensesProcessed.spdxExpression;
                    if (spdxExpression) {
                        this.#declaredLicensesSpdxExpression = spdxExpression;
                    }
                }

                if (declaredLicensesProcessed.unmapped_licenses || declaredLicensesProcessed.unmappedLicenses) {
                    const unmappedLicenses = declaredLicensesProcessed.unmapped_licenses
                        || declaredLicensesProcessed.unmappedLicenses;
                    if (unmappedLicenses && unmappedLicenses.length > 0) {
                        this.#declaredLicensesUnmappedIndexes = new Set(unmappedLicenses);
                    }
                }
            }

            if (obj.definition_file_path || obj.definitionFilePath) {
                this.#definitionFilePath = obj.definition_file_path
                    || obj.definitionFilePath;
            }

            if (obj.description) {
                this.#description = obj.description;
            }

            if (obj.detected_excluded_licenses || obj.detectedExcludedLicenses) {
                const detectedExcludedLicensesIndexes = obj.detected_excluded_licenses
                    || obj.detectedExcludedLicenses;
                this.#detectedExcludedLicensesIndexes = new Set(detectedExcludedLicensesIndexes);
            }

            if (obj.detected_licenses || obj.detectedLicenses) {
                const detectedLicensesIndexes = obj.detected_licenses
                    || obj.detectedLicenses;
                this.#detectedLicensesIndexes = new Set(detectedLicensesIndexes);
            }

            if (obj.homepage_url || obj.homepageUrl) {
                this.#homepageUrl = obj.homepage_url || obj.homepageUrl;
            }

            if (obj.id) {
                this.#id = obj.id;
            }

            if (obj.is_excluded || obj.isExcluded) {
                this.#isExcluded = obj.is_excluded || obj.isExcluded;
            }

            if (obj.findings && webAppOrtResult) {
                setTimeout(() => {
                    for (let i = 0, len = obj.findings.length; i < len; i++) {
                        if (obj.findings[i]['path_excludes'] || obj.findings[i]['pathExcludes']) {
                            this.#excludedFindingsIndexes.push(i);
                        }

                        this.#findings.push(new WebAppFinding(obj.findings[i], webAppOrtResult));
                    }
                }, 0);
            }

            if (obj.is_project || obj.isProject) {
                this.#isProject = obj.is_project || obj.isProject;
            }

            if (obj.levels) {
                this.#levels = new Set(obj.levels);
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

            if (webAppOrtResult) {
                this.#webAppOrtResult = webAppOrtResult;
                const getLicenseNames = (indexes) => {
                    const licenses = [];
                    indexes.forEach((index) => {
                        const webAppLicense = webAppOrtResult.getLicenseByIndex(index);
                        if (webAppLicense) {
                            const { id } = webAppLicense;
                            licenses.push(id);
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

                if (this.#detectedExcludedLicensesIndexes.size !== 0) {
                    this.#detectedExcludedLicenses = getLicenseNames(this.#detectedExcludedLicensesIndexes);

                    this.#detectedLicensesProcessed = getLicenseNames(new Set(
                        [...this.#detectedLicensesIndexes].filter(
                            (license) => !this.#detectedExcludedLicensesIndexes.has(license)
                        )
                    ));
                } else {
                    this.#detectedLicensesProcessed = this.#detectedLicenses;
                }
            }

            this.key = randomStringGenerator(20);
        }
    }

    get _id() {
        return this.#_id;
    }

    get binaryArtifact() {
        return this.#binaryArtifact;
    }

    get concludedLicense() {
        return this.#concludedLicense;
    }

    get curations() {
        return this.#curations;
    }

    get declaredLicenses() {
        return this.#declaredLicenses;
    }

    get declaredLicensesIndexes() {
        return this.#declaredLicensesIndexes;
    }

    get declaredLicensesMapped() {
        return this.#declaredLicensesMapped;
    }

    get declaredLicensesSpdxExpression() {
        return this.#declaredLicensesSpdxExpression;
    }

    get declaredLicensesUnmapped() {
        return this.#declaredLicensesUnmapped;
    }

    get definitionFilePath() {
        return this.#definitionFilePath;
    }

    get description() {
        return this.#description;
    }

    get detectedExcludedLicenses() {
        return this.#detectedExcludedLicenses;
    }

    get detectedExcludedLicensesIndexes() {
        return this.#detectedExcludedLicensesIndexes;
    }

    get detectedLicenses() {
        return this.#detectedLicenses;
    }

    get detectedLicensesIndexes() {
        return this.#detectedLicensesIndexes;
    }

    get detectedLicensesProcessed() {
        return this.#detectedLicensesProcessed;
    }

    get excludeReasons() {
        const { pathExcludeReasons, scopeExcludeReasons } = this;

        return new Set([...pathExcludeReasons, ...scopeExcludeReasons]);
    }

    get excludedFindings() {
        if (!this.#excludedFindings) {
            this.#excludedFindings = [];

            this.#excludedFindingsIndexes.forEach((index) => {
                this.#excludedFindings.push(this.#findings[index]);
            });
        }

        return this.#excludedFindings;
    }

    get excludedFindingsIndexes() {
        return this.#excludedFindingsIndexes;
    }

    get findings() {
        return this.#findings;
    }

    get homepageUrl() {
        return this.#homepageUrl;
    }

    get id() {
        return this.#id;
    }

    get isExcluded() {
        return this.#isExcluded;
    }

    get isProject() {
        return this.#isProject;
    }

    get issues() {
        if (!this.#issues && this.#webAppOrtResult) {
            this.#issues = this.#webAppOrtResult.getIssuesForPackageIndex(this.#_id);
        }

        return this.#issues;
    }

    get levels() {
        return this.#levels;
    }

    get packageIndex() {
        return this.#_id;
    }

    get pathExcludes() {
        if (!this.#pathExcludes && this.#webAppOrtResult) {
            this.#pathExcludes = [];
            this.#pathExcludeIndexes.forEach((index) => {
                const webAppPathExclude = this.#webAppOrtResult.getPathExcludeByIndex(index) || null;
                if (webAppPathExclude) {
                    this.#pathExcludes.push(webAppPathExclude);
                }
            });
        }

        return this.#pathExcludes;
    }

    get pathExcludeIndexes() {
        return this.#pathExcludeIndexes;
    }

    get pathExcludeReasons() {
        if (!this.#pathExcludeReasons && this.#webAppOrtResult) {
            this.#pathExcludeReasons = new Set();

            this.#pathExcludeIndexes.forEach((index) => {
                const webAppPathExclude = this.#webAppOrtResult.getPathExcludeByIndex(index) || null;
                if (webAppPathExclude && webAppPathExclude.reason) {
                    this.#pathExcludeReasons.add(webAppPathExclude.reason);
                }
            });
        }

        return this.#pathExcludeReasons;
    }

    get pathIndexes() {
        return this.#pathIndexes;
    }

    get paths() {
        if (!this.#paths && this.#webAppOrtResult) {
            this.#paths = [];
            this.#pathIndexes.forEach((index) => {
                const webAppPath = this.#webAppOrtResult.getPathByIndex(index) || null;
                if (webAppPath) {
                    this.#paths.push(webAppPath);
                }
            });
        }

        return this.#paths;
    }

    get projectIndexes() {
        if (!this.#projectIndexes && this.#webAppOrtResult) {
            this.#projectIndexes = new Set();
            if (this.#isProject) {
                this.#projectIndexes.add(this.#_id);
            } else {
                this.#pathIndexes.forEach((index) => {
                    const webAppPath = this.#webAppOrtResult.getPathByIndex(index) || null;
                    if (webAppPath && webAppPath.projectIndex) {
                        this.#projectIndexes.add(webAppPath.projectIndex);
                    }
                });
            }
        }

        return this.#projectIndexes;
    }

    get purl() {
        return this.#purl;
    }

    get ruleViolations() {
        if (!this.#ruleViolations && this.#webAppOrtResult) {
            this.#ruleViolations = this.#webAppOrtResult.getRuleViolationsForPackageIndex(this.#_id);
        }

        return this.#ruleViolations;
    }

    get scanResults() {
        if (!this.#scanResults) {
            this.#scanResults = [];

            for (let i = 0, len = this.#scanResultsIndexes.length; i < len; i++) {
                this.#scanResults.push(this.#webAppOrtResult.getScanResultByIndex(this.#scanResultsIndexes[i]));
            }
        }

        return this.#scanResults;
    }

    get scopeExcludes() {
        if (!this.#scopeExcludes && this.#webAppOrtResult) {
            this.#scopeExcludes = [];
            this.#scopeExcludeIndexes.forEach((index) => {
                const webAppScopeExclude = this.#webAppOrtResult.getScopeExcludeByIndex(index) || null;
                if (webAppScopeExclude) {
                    this.#scopeExcludes.push(webAppScopeExclude);
                }
            });
        }

        return this.#scopeExcludes;
    }

    get scopeExcludeIndexes() {
        return this.#scopeExcludeIndexes;
    }

    get scopeExcludeReasons() {
        if (!this.#scopeExcludeReasons) {
            this.#scopeExcludeReasons = new Set();

            this.#scopeExcludeIndexes.forEach((index) => {
                const webAppScopeExclude = this.#webAppOrtResult.getScopeExcludeByIndex(index) || null;
                if (webAppScopeExclude && webAppScopeExclude.reason) {
                    this.#scopeExcludeReasons.add(webAppScopeExclude.reason);
                }
            });
        }

        return this.#scopeExcludeReasons;
    }

    get scopeIndexes() {
        return this.#scopeIndexes;
    }

    get scopeNames() {
        if (!this.#scopeNames) {
            this.#scopeNames = new Set();

            this.#scopeIndexes.forEach((index) => {
                const webAppScope = this.#webAppOrtResult.getScopeByIndex(index) || null;
                if (webAppScope && webAppScope.name) {
                    this.#scopeNames.add(webAppScope.name);
                }
            });
        }

        return this.#scopeNames;
    }

    get scopes() {
        if (!this.#scopes && this.#webAppOrtResult) {
            this.#scopes = [];
            this.#scopeIndexes.forEach((index) => {
                const webAppScope = this.#webAppOrtResult.getScopeByIndex(index) || null;
                if (webAppScope) {
                    this.#scopes.add(webAppScope);
                }
            });
        }
        return this.#scopes;
    }

    get sourceArtifact() {
        return this.#sourceArtifact;
    }

    get vcs() {
        return this.#vcs;
    }

    get vcsProcessed() {
        return this.#vcsProcessed;
    }

    hasConcludedLicense() {
        return this.#concludedLicense
            && this.#concludedLicense.length !== 0;
    }

    hasDeclaredLicenses() {
        return this.#declaredLicenses.size !== 0;
    }

    hasDeclaredLicensesMapped() {
        return this.#declaredLicensesMapped
            && this.#declaredLicensesMapped.size !== 0;
    }

    hasDeclaredLicensesSpdxExpression() {
        return this.#declaredLicensesSpdxExpression
            && this.#declaredLicensesSpdxExpression.length !== 0;
    }

    hasDeclaredLicensesUnmapped() {
        return this.#declaredLicensesUnmapped
            && this.#declaredLicensesUnmapped.size !== 0;
    }

    hasDetectedLicenses() {
        return this.#detectedLicenses.size !== 0;
    }

    hasDetectedExcludedLicenses() {
        return this.#detectedExcludedLicenses.size !== 0;
    }

    hasExcludedFindings() {
        return this.#excludedFindingsIndexes.length > 0;
    }

    hasFindings() {
        return this.#findings.length > 0;
    }

    hasIssues() {
        return this.issues.length > 0;
    }

    hasLevel(val) {
        return this.#levels.has(val);
    }

    hasLicenses() {
        return this.declaredLicenses.size !== 0
            || this.detectedLicenses.size !== 0;
    }

    hasPathExcludes() {
        return this.pathExcludeIndexes.size !== 0;
    }

    hasPaths() {
        return this.paths && this.paths.length > 0;
    }

    hasScopeIndex(val) {
        return this.#scopeIndexes.has(val);
    }

    hasScopeExcludes() {
        return this.scopeExcludeIndexes.size !== 0;
    }

    hasScopes() {
        return this.scopes && this.scopes.length > 0;
    }

    hasRuleViolations() {
        return this.ruleViolations.length > 0;
    }
}

export default WebAppPackage;
