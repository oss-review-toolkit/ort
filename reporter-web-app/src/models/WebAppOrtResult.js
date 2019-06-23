/*
 * Copyright (C) 2019 HERE Europe B.V.
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

/* eslint constructor-super: 0 */

import CuratedPackage from './CuratedPackage';
import OrtResult from './OrtResult';
import WebAppOrtIssueAnalyzer from './WebAppOrtIssueAnalyzer';
import WebAppOrtIssueScanner from './WebAppOrtIssueScanner';
import WebAppPackage from './WebAppPackage';
import WebAppRuleViolation from './WebAppRuleViolation';

class WebAppOrtResult extends OrtResult {
    #declaredLicenses = new Set();

    #detectedLicenses;

    #errors = [];

    #levels = new Set();

    #packagesMap = new Map();

    #packagesTreeArray= [];

    #packagesTreeFlatArray = [];

    #packagesTreeNodesArray = [];

    #projectsMap = new Map();

    #scanResultContainerIndex = new Map();

    #scannersUsed = new Set([]);

    #scopes;

    #violations;

    constructor(obj) {
        super(obj);

        if (obj) {
            // Populate #packagesMap and #projectsMap with packages Analyzer result
            const { result: { packages, projects } } = this.analyzer;

            for (let i = packages.length - 1; i >= 0; i -= 1) {
                const curatedPackage = packages[i];

                if (curatedPackage instanceof CuratedPackage) {
                    const webAppPackage = new WebAppPackage(curatedPackage.pkg);
                    webAppPackage.curations = curatedPackage.curations;

                    this.#packagesMap.set(
                        curatedPackage.pkg.id,
                        webAppPackage
                    );

                    webAppPackage.declaredLicenses.forEach((license) => {
                        this.#declaredLicenses.add(license);
                    });
                }
            }

            for (let i = projects.length - 1; i >= 0; i -= 1) {
                this.#projectsMap.set(projects[i].id, projects[i]);
            }

            // Populate #scanResultContainerIndex with Scannner result for each package
            const { scanResults } = this.scanner.results;
            const scannersIndex = new Map();

            for (let i = 0, lenScanResults = scanResults.length; i < lenScanResults; i++) {
                const { id, results: scanResultContainer } = scanResults[i];

                for (let j = 0, lenScanResultContainer = scanResultContainer.length; j < lenScanResultContainer; j++) {
                    const scanResult = scanResultContainer[j];
                    const { scanner, summary } = scanResult;
                    const { errors } = summary;
                    const scannerId = `${scanner.name}-${scanner.version}`;

                    if (!this.#scannersUsed.has(scannerId)) {
                        this.#scannersUsed.add(scannerId);
                        scannersIndex.set(scannerId, j);
                    }

                    if (errors !== 0) {
                        for (let k = 0, len = errors.length; k < len; k++) {
                            const webAppOrtIssue = new WebAppOrtIssueScanner(errors[k]);
                            webAppOrtIssue.pkg = id;

                            this.#errors.push(webAppOrtIssue);
                        }
                    }
                }

                this.#scanResultContainerIndex.set(
                    id,
                    {
                        index: i,
                        scanners: scannersIndex
                    }
                );
            }
        }
    }

    get declaredLicenses() {
        return Array.from(this.#declaredLicenses).sort();
    }

    set declaredLicenses(set) {
        this.#declaredLicenses = set;
    }

    get detectedLicenses() {
        if (!this.#detectedLicenses) {
            if (this.#packagesMap.size === 0) {
                return [];
            }

            this.#detectedLicenses = new Set();

            this.getPackages().forEach((pkg) => {
                if (pkg.detectedLicenses.length > 0) {
                    pkg.detectedLicenses.forEach((license) => {
                        this.#detectedLicenses.add(license);
                    });
                }
            });
        }

        return Array.from(this.#detectedLicenses).sort();
    }

    set detectedLicenses(set) {
        this.#detectedLicenses = set;
    }

    get errors() {
        return this.#errors;
    }

    get levels() {
        return Array.from(this.#levels).sort((a, b) => a - b);
    }

    get packagesMap() {
        return this.#packagesMap;
    }

    set packagesMap(map) {
        this.#packagesMap = map;
    }

    get packagesTreeArray() {
        return this.#packagesTreeArray;
    }

    set packagesTreeArray(map) {
        this.#packagesTreeArray = map;
    }

    get packagesTreeFlatArray() {
        return this.#packagesTreeFlatArray;
    }

    set packagesTreeFlatArray(arr) {
        this.#packagesTreeFlatArray = arr;
    }

    get packagesTreeNodesArray() {
        return this.#packagesTreeNodesArray;
    }

    set packagesTreeNodesArray(arr) {
        this.#packagesTreeNodesArray = arr;
    }

    get projectsMap() {
        return this.#projectsMap;
    }

    get scopes() {
        const scopes = new Set([]);
        const { results: { scannedScopes } } = this.scanner;

        if (!this.#scopes && scannedScopes) {
            for (let i = 0, len = scannedScopes.length; i < len; i++) {
                const projectScanScopes = [
                    ...Object.values(scannedScopes[i].scannedScopes),
                    ...Object.values(scannedScopes[i].ignoredScopes)
                ];

                Object.values(projectScanScopes).forEach(
                    (scope) => {
                        if (scope && !scopes.has(scope)) {
                            scopes.add(scope);
                        }
                    }
                );
            }

            this.#scopes = scopes;
        }

        return Array.from(this.#scopes);
    }

    get violations() {
        const { violations } = this.evaluator;

        if (!this.#violations && violations) {
            this.#violations = [];

            for (let i = 0, len = violations.length; i < len; i++) {
                const violation = violations[i];
                const webAppRuleViolation = new WebAppRuleViolation(violation);
                this.#violations.push(webAppRuleViolation);
            }
        }

        return this.#violations;
    }

    addError(err) {
        if (err instanceof WebAppOrtIssueAnalyzer || err instanceof WebAppOrtIssueScanner) {
            this.#errors.push(err);
        }
    }

    addLevel(level) {
        if (Number.isInteger(level)) {
            return this.#levels.add(level);
        }

        return null;
    }

    addPackageToPackagesTreeFlatArray(pkg, prepend = false) {
        if (prepend) {
            this.#packagesTreeFlatArray.unshift(pkg);
        } else {
            this.#packagesTreeFlatArray.push(pkg);
        }
    }

    addPackageToTreeNodesArray(pkg, prepend = false) {
        if (pkg) {
            if (prepend) {
                this.#packagesTreeNodesArray.unshift(pkg);
            } else {
                this.#packagesTreeNodesArray.push(pkg);
            }
        }
    }

    addPackage(id, pkg) {
        if (id && pkg) {
            return this.#packagesMap.set(id, pkg);
        }

        return null;
    }

    getOrtVersion() {
        const {
            environment: {
                ortVersion
            }
        } = this.analyzer;

        return ortVersion;
    }

    getPackageById(id) {
        return this.#packagesMap.get(id);
    }

    getPackages() {
        return Array.from(this.#packagesMap.values());
    }

    getPackageIds() {
        return this.#packagesMap.keys();
    }

    hasPackageId(id) {
        return this.#packagesMap.has(id);
    }

    getProjectById(id) {
        return this.#projectsMap.get(id);
    }

    getProjectPackageById(id) {
        if (this.hasPackageId(id)) {
            const pkg = this.getPackageById(id);
            const { projectKey } = pkg;
            const projects = this.getProjects();

            if (projectKey && projects) {
                return projects[projectKey];
            }
        }

        return null;
    }

    getProjectByIndex(index) {
        const projects = this.getProjects();

        if (projects[index]) {
            return projects[index];
        }

        return null;
    }

    getProjects() {
        return this.analyzer.result.projects;
    }

    hasErrors() {
        return this.errors.length > 0;
    }

    hasProjectId(id) {
        return this.#projectsMap.has(id);
    }

    hasViolations() {
        return this.violations.length > 0;
    }

    getScannersUsed() {
        return this.#scannersUsed;
    }

    getScanSummaryByPackageIdAndByScannerId(id, scannerId) {
        if (id) {
            const { results: { scanResults } } = this.scanner;

            if (this.#scanResultContainerIndex.has(id)) {
                const { index, scanners } = this.#scanResultContainerIndex.get(id);
                if (scanners.has(scannerId)) {
                    return scanResults[index].results[scanners.get(scannerId)];
                }
            }
        }

        return null;
    }

    getScanResultContainerForPackageId(id) {
        if (id) {
            const { results: { scanResults } } = this.scanner;

            if (this.#scanResultContainerIndex.has(id)) {
                const { index } = this.#scanResultContainerIndex.get(id);
                return scanResults[index];
            }
        }

        return null;
    }

    hasScanResultContainerForPackageId(id) {
        const scanResultContainer = this.getScanResultContainerForPackageId(id);

        if (scanResultContainer) {
            return true;
        }

        return false;
    }
}

export default WebAppOrtResult;
