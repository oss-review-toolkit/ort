/*
 * Copyright (c) 2018 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
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

// Utility boolean function to determine if input is a number
export function isNumeric(n) {
    return !isNaN(parseFloat(n)) && isFinite(n);
}

export function convertToRenderFormat(reportData) {
    if (!reportData
        || !reportData.analyzer.result
        || !reportData.analyzer.result.projects
        || !reportData.analyzer.result.packages
        || !reportData.repository) {
        return {};
    }

    const projects = {};
    const declaredLicensesFromAnalyzer = {};
    const detectedLicensesFromScanner = {};
    const reportDataOpenErrors = {};
    const reportDataResolvedErrors = {};
    let reportDataLevels = new Set([]);
    let reportDataScopes = new Set([]);

    const addErrorsToPackage = (projectIndex, pkgObj, analyzerErrors) => {
        const project = projects[projectIndex];
        let errors;
        let errorsAnalyzer = [];
        let errorsScanner = [];

        const createErrorObj = (type, error) => {
            return {
                id: project.id,
                code: hashCode(project.id) + 'x' + hashCode(pkgObj.id) + error.message.length,
                source: error.source,
                timestamp: error.timestamp,
                type: type,
                package: {
                    id: pkgObj.id,
                    path: pkgObj.path,
                    level: pkgObj.level,
                    scope: pkgObj.scope
                },
                file: project.definition_file_path,
                message: error.message
            };
        };
        const packageFromScanner = packagesFromScanner[pkgObj.id] || false;

        if (analyzerErrors && project) {
            errorsAnalyzer = analyzerErrors.map((error) => {
                return createErrorObj('ANALYZER_PACKAGE_ERROR', error);
            });
        }

        if (packageErrorsFromAnalyzer && project) {
            if (packageErrorsFromAnalyzer[pkgObj.id]) {
                errorsAnalyzer = [
                    ...errorsAnalyzer,
                    ...packageErrorsFromAnalyzer[pkgObj.id].map(
                        (error) => {
                            return createErrorObj('ANALYZER_PACKAGE_ERROR', error);
                        }
                    )
                ];
            }
        }

        if (packageFromScanner) {
            errors = packageFromScanner.reduce((accumulator, scanResult) => {
                if (!scanResult.errors) {
                    return accumulator;
                }

                return accumulator.concat(scanResult.errors);
            }, []);

            errorsScanner = errors.map((error) => {
                return createErrorObj('SCANNER_PACKAGE_ERROR', error);
            });
        }

        errors = [...errorsAnalyzer, ...errorsScanner];

        if (errors.length !== 0) {
            pkgObj.errors = errors;

            addErrorsToReportDataReportData(projectIndex, pkgObj.errors);
        }

        return pkgObj;
    };
    const addErrorsToReportDataReportData = (projectIndex, errors) => {
        if (Array.isArray(errors) && errors.length !== 0) {
            if (!reportDataOpenErrors[projectIndex]) {
                reportDataOpenErrors[projectIndex] = [];
            }

            reportDataOpenErrors[projectIndex] = [...reportDataOpenErrors[projectIndex], ...errors];
        }
    };
    // Helper function to add license results
    // from Analyzer and Scanner to a package
    const addLicensesToPackage = (projectIndex, pkgObj) => {
        const packageFromAnalyzer = packagesFromAnalyzer[pkgObj.id] || false;
        const packageFromScanner = packagesFromScanner[pkgObj.id] || false;

        if (pkgObj.id === projects[projectIndex].id) {
            // If package is a project then declared licenses
            // are in projects found by Analyzer
            pkgObj.declared_licenses = projectsFromAnalyzer[projectIndex].declared_licenses;
        } else if (packageFromAnalyzer) {
            pkgObj.declared_licenses = packageFromAnalyzer.declared_licenses;
        }

        addPackageLicensesToProject(
            projectIndex,
            'declared',
            pkgObj.declared_licenses
        );

        addPackageLicensesToReportData(
            declaredLicensesFromAnalyzer,
            projectIndex,
            pkgObj,
            pkgObj.declared_licenses
        );

        if (packageFromScanner) {
            pkgObj.results = packageFromScanner;

            pkgObj.license_findings = packageFromScanner.reduce((accumulator, scanResult) =>
                accumulator.concat(scanResult.summary.license_findings), []);

            pkgObj.detected_licenses = removeDuplicatesInArray(
                pkgObj.license_findings.map(finding => finding.license)
            );

            addPackageLicensesToProject(
                projectIndex,
                'detected',
                pkgObj.detected_licenses
            );

            addPackageLicensesToReportData(
                detectedLicensesFromScanner,
                projectIndex,
                pkgObj,
                pkgObj.detected_licenses
            );
        } else {
            console.error('Package' + pkgObj.id + 'was detected by Analyzer but not scanned');
        }

        return pkgObj;
    };
    /* Helper function to add the level and the scope for package
     * to the project that introduced the dependency.
     *  Needed to visualize or filter a project by
     * package level(s) or scope(s)
     */
    const addPackageLevelAndScopeToProject = (projectIndex, level, scope) => {
        const project = projects[projectIndex];
        let projectLevels;
        let projectScopes;

        if (project) {
            projectLevels = project.packages.levels;
            projectScopes = project.packages.scopes;

            if (level !== undefined && !projectLevels.has(level)) {
                projectLevels.add(level);
            }

            if (scope && scope !== '' && !projectScopes.has(scope)) {
                projectScopes.add(scope);
            }
        }
    };
    const addPackageLicensesToProject = (projectIndex, licenseType, licenses) => {
        const project = projects[projectIndex];
        let projectLicenses;

        if (project && licenseType && licenses) {
            projectLicenses = project.packages.licenses;

            if (projectLicenses[licenseType]) {
                projectLicenses[licenseType] = Array.from(
                    new Set([...projectLicenses[licenseType], ...licenses])
                );
            }
        }
    };
    /* Helper function to add declared and detected licenses objects
     * to the report data object
     *
     * Example of object this function creates:
     *
     * licenses.declared[1]: {
     *    'Eclipse Public License 1.0': {
     *        id: 'Maven:com.google.protobuf:protobuf-lite:3.0.0',
     *        definition_file_path: './java/lite/pom.xml',
     *        package: {
     *            id: 'Maven:junit:junit:4.12'
     *        }
     *    }
     * }
     */
    const addPackageLicensesToReportData = (reportDataLicenses, projectIndex, pkgObj, licenses) => {
        if (Array.isArray(licenses)) {
            for (let i = licenses.length - 1; i >= 0; i -= 1) {
                const license = licenses[i];
                const project = projects[projectIndex];
                let licenseOccurance = [];
                let licenseOccurances;

                if (!Object.prototype.hasOwnProperty.call(reportDataLicenses, projectIndex)) {
                    reportDataLicenses[projectIndex] = {};
                }

                if (!reportDataLicenses[projectIndex][license]) {
                    reportDataLicenses[projectIndex][license] = new Map();
                }

                if (project && project.id && pkgObj && pkgObj.id) {
                    licenseOccurances = reportDataLicenses[projectIndex][license];

                    if (licenseOccurances.has(pkgObj.id)) {
                        licenseOccurance = licenseOccurances.get(pkgObj.id);
                    }

                    reportDataLicenses[projectIndex][license].set(
                        pkgObj.id,
                        [
                            ...licenseOccurance,
                            {
                                id: project.id,
                                definition_file_path: project.definition_file_path,
                                package: {
                                    id: pkgObj.id,
                                    level: pkgObj.level,
                                    path: pkgObj.path,
                                    scope: pkgObj.scope
                                },
                                type: 'PACKAGE'
                            }
                        ]
                    );
                }
            }
        }
    };
    /* Helper function is called by recursivePackageAnalyzer
     * for each package. Designed to flatten tree of dependencies
     * into a single object so tree can be rendered as a table
     */
    const addPackageToProjectList = (projectIndex, pkgObj) => {
        let projectsListPkg;

        projectsListPkg = projects[projectIndex].packages.list[pkgObj.id];

        if (!projectsListPkg) {
            pkgObj.levels = [pkgObj.level];
            pkgObj.paths = [];
            pkgObj.scopes = [];
            if (pkgObj.scope !== '') {
                pkgObj.scopes.push(pkgObj.scope);
            }

            projectsListPkg = pkgObj;
            projects[projectIndex].packages.list[pkgObj.id] = pkgObj;
            projects[projectIndex].packages.total = ++projects[projectIndex].packages.total;
        } else {
            // Ensure each level only occurs once
            if (!projectsListPkg.levels.includes(pkgObj.level)) {
                projectsListPkg.levels.push(pkgObj.level);
            }

            if (pkgObj.scope !== '') {
                // Ensure each scope only occurs once
                if (!projectsListPkg.scopes.includes(pkgObj.scope)) {
                    projectsListPkg.scopes.push(pkgObj.scope);
                }
            }
        }

        if (pkgObj.scope !== '' && pkgObj.path.length !== 0) {
            projectsListPkg.paths.push({
                scope: pkgObj.scope,
                path: pkgObj.path
            });
        }

        addPackageLevelAndScopeToProject(projectIndex, pkgObj.level, pkgObj.scope);

        delete pkgObj.children;
        delete pkgObj.path;
        delete pkgObj.level;
        delete pkgObj.scope;

        return pkgObj;
    };
    const addProjectLevelsToReportDataReportData = (projectIndex) => {
        const project = projects[projectIndex];

        if (project
            && project.packages
            && project.packages.levels
            && project.packages.levels.size !== 0) {
            reportDataLevels = new Set([...reportDataLevels, ...project.packages.levels]);
        }
    };
    const addProjectLicensesToReportData = (projectIndex) => {
        const addLicensesToReportData = (licenses, projectIndex, project, projectLicenses) => {
            if (Array.isArray(projectLicenses)) {
                for (let i = projectLicenses.length - 1; i >= 0; i -= 1) {
                    const license = projectLicenses[i];
                    const project = projects[projectIndex];
                    let licenseOccurance = [];
                    let licenseOccurances;

                    if (!Object.prototype.hasOwnProperty.call(licenses, projectIndex)) {
                        licenses[projectIndex] = {};
                    }

                    if (!licenses[projectIndex][license]) {
                        licenses[projectIndex][license] = new Map();
                    }

                    if (project && project.id) {
                        licenseOccurances = licenses[projectIndex][license];

                        if (licenseOccurances.has(project.id)) {
                            licenseOccurance = licenseOccurances.get(project.id);
                        }

                        licenses[projectIndex][license].set(
                            project.id,
                            [
                                ...licenseOccurance,
                                {
                                    id: project.id,
                                    definition_file_path: project.definition_file_path,
                                    type: 'PROJECT'
                                }
                            ]
                        );
                    }
                }
            }
        };
        const project = projects[projectIndex];

        if (project) {
            addLicensesToReportData(
                declaredLicensesFromAnalyzer,
                projectIndex,
                project,
                project.declared_licenses
            );
            addLicensesToReportData(
                detectedLicensesFromScanner,
                projectIndex,
                project,
                project.detected_licenses
            );
        }
    };
    const addProjectScopesToReportDataReportData = (projectIndex) => {
        const project = projects[projectIndex];

        if (project
            && project.packages
            && project.packages.scopes
            && project.packages.scopes.size !== 0) {
            reportDataScopes = new Set([...reportDataScopes, ...project.packages.scopes]);
        }
    };
    // Helper function to add results from Scanner to a project
    const addScanResultsToProject = (project) => {
        const projectId = project.id;
        const projectFromScanner = packagesFromScanner[projectId] || false;

        if (projectId && projectFromScanner) {
            project.results = projectFromScanner;

            project.packages.licenses.findings = projectFromScanner.reduce(
                (accumulator, scanResult) => accumulator.concat(
                    scanResult.summary.license_findings
                ),
                []
            );

            project.packages.licenses.detected = removeDuplicatesInArray(
                project.packages.licenses.findings.map(finding => finding.license)
            );
        }

        return project;
    };
    const calculateNrPackagesLicenses = (projectsLicenses) => {
        return Object.values(projectsLicenses).reduce((accumulator, projectLicenses) => {
            for (const license in projectLicenses) {
                const licenseMap = projectLicenses[license];

                if (!accumulator[license]) {
                    accumulator[license] = 0;
                }

                accumulator[license] += licenseMap.size;
            }
            return accumulator;
        }, {});
    };
    const calculateReportDataTotalLicenses = (projectsLicenses) => {
        const licensesSet = new Set([]);

        return Object.values(projectsLicenses).reduce((accumulator, projectLicenses) => {
            for (const license in projectLicenses) {
                accumulator.add(license);
            }
            return accumulator;
        }, licensesSet).size || undefined;
    };
    const calculateReportDataTotalErrors = () => {
        let errorsArr;
        let reportDataTotalErrors;

        if (reportDataOpenErrors.length !== 0) {
            reportDataTotalErrors = 0;
            errorsArr = Object.values(reportDataOpenErrors);

            for (let i = errorsArr.length - 1; i >= 0; i -= 1) {
                reportDataTotalErrors += errorsArr[i].length;
            }

            return reportDataTotalErrors;
        }

        return undefined;
    };
    const calculateReportDataTotalLevels = () => {
        if (reportDataLevels && reportDataLevels.size) {
            return reportDataLevels.size;
        }

        return undefined;
    };
    const calculateReportDataTotalPackages = () => {
        if (packagesFromAnalyzer) {
            return Object.keys(packagesFromAnalyzer).length;
        }

        return undefined;
    };
    const calculatReportDataTotalProjects = () => {
        return Object.keys(projects).length;
    };
    const calculateReportDataTotalScopes = () => {
        if (reportDataScopes && reportDataScopes.size) {
            return reportDataScopes.size;
        }

        return undefined;
    };
    // Using ES6 Proxy extend pkgObj with info from Analyzer's packages
    const packageProxyHandler = {
        get: (pkgObj, prop) => {
            const packageFromAnalyzer = packagesFromAnalyzer[pkgObj.id];

            if (Object.prototype.hasOwnProperty.call(pkgObj, prop)) {
                return pkgObj[prop];
            }

            if (packageFromAnalyzer) {
                if (Object.prototype.hasOwnProperty.call(packageFromAnalyzer, prop)) {
                    return packageFromAnalyzer[prop];
                }
            }

            return undefined;
        }
    };
    // Transform Analyer results to be indexed by package Id for faster lookups
    const packagesFromAnalyzer = ((dataArr) => {
        const tmp = {};

        for (let i = dataArr.length - 1; i >= 0; i -= 1) {
            tmp[dataArr[i].package.id] = {
                ...dataArr[i].package,
                curations: dataArr[i].curations
            };
        }

        return tmp;
    })(reportData.analyzer.result.packages || []);
    // Transform Scanner results to be indexed by package Id for faster lookups
    const packagesFromScanner = ((dataArr) => {
        const tmp = {};

        for (let i = dataArr.length - 1; i >= 0; i -= 1) {
            tmp[dataArr[i].id] = dataArr[i].results;
        }

        return tmp;
    })(reportData.scanner.results.scan_results || []);
    const packageErrorsFromAnalyzer = reportData.analyzer.result.errors;
    const projectsFromAnalyzer = reportData.analyzer.result.projects;
    /* Helper function to recursive traverse over the packages
     * found by the Analyzer so they can be transformed
     * into a format that suitable for use in the WebApp
     */
    const recursivePackageAnalyzer = (projectIndex, pkg, dependencyPathFromRoot = [], scp = '', delivered) => {
        const children = Object.entries(pkg).reduce((accumulator, [key, value]) => {
            // Only recursively traverse objects which can hold packages
            if (key === 'dependencies') {
                const depsChildren = value.map((dep) => recursivePackageAnalyzer(
                    projectIndex,
                    dep,
                    [...dependencyPathFromRoot, pkg.id || pkg.name],
                    scp, delivered
                ));
                accumulator.push(...depsChildren);
            }

            if (key === 'scopes') {
                const scopeChildren = value.map((scope) => {
                    return scope.dependencies.map((dep) => recursivePackageAnalyzer(
                        projectIndex,
                        dep,
                        [...dependencyPathFromRoot, pkg.name || pkg.id],
                        scope.name,
                        scope.delivered
                    ));
                }).reduce((acc, scopeDeps) => [...acc, ...scopeDeps], []);
                // Abve reduces removes empty arrays resulting from scopes without dependencies

                accumulator.push(...scopeChildren);
            }

            return accumulator;
        }, []);
        let pkgObj = {
            id: pkg.id || pkg.name,
            children,
            errors: pkg.errors || [],
            level: dependencyPathFromRoot.length,
            path: dependencyPathFromRoot,
            scope: scp
        };

        pkgObj = addLicensesToPackage(projectIndex, pkgObj);

        if (delivered) {
            pkgObj.delivered = delivered;
        }

        // Copy over relevant info for a package that is a project
        // For regular packages this info is looked up
        // via an ES6 Proxy, see packageProxyHandler
        if (pkg.definition_file_path) {
            pkgObj.definition_file_path = pkg.definition_file_path;
            pkgObj.homepage_url = pkg.homepage_url;
            pkgObj.vcs = pkg.vcs;
            pkgObj.vcs_processed = pkg.vcs_processed;
        }

        pkgObj = addErrorsToPackage(projectIndex, pkgObj, pkg.errors || []);

        // Project list is merges multiple occurrances of the same package
        // into same listing modifying pkgObj therefore we make a copy
        addPackageToProjectList(projectIndex, new Proxy({...pkgObj}, packageProxyHandler));

        return new Proxy(pkgObj, packageProxyHandler);
    };

    // Traverse over projects
    for (let i = projectsFromAnalyzer.length - 1; i >= 0; i -= 1) {
        const project = projectsFromAnalyzer[i];
        const projectIndex = i;
        let projectFile = project.definition_file_path;

        // Add ./ so we never have empty string
        projectFile = './' + projectFile;
        project.definition_file_path = projectFile;

        if (!Object.prototype.hasOwnProperty.call(projects, projectIndex)) {
            projects[projectIndex] = addScanResultsToProject({
                id: project.id,
                index: i,
                definition_file_path: project.definition_file_path,
                homepage_url: project.homepage_url,
                packages: {
                    levels: new Set([]),
                    licenses: {
                        declared: project.declared_licenses || [],
                        detected: []
                    },
                    list: {},
                    scopes: new Set([]),
                    total: 0,
                    tree: []
                },
                vcs: project.vcs,
                vcs_processed: project.vcs_processed
            });
        }

        projects[projectIndex].packages.tree = recursivePackageAnalyzer(projectIndex, project);

        addProjectLicensesToReportData(projectIndex);
        addProjectLevelsToReportDataReportData(projectIndex);
        addProjectScopesToReportDataReportData(projectIndex);

        // As packages are added recursively to get an array
        // with the right order we need to reverse it
        projects[projectIndex].packages.list = Object.values(
            projects[projectIndex].packages.list
        ).reverse();
    }

    return reportData = {
        hasErrors: reportData.has_errors || false,
        errors: {
            data: {
                addressed: reportDataResolvedErrors,
                open: reportDataOpenErrors
            },
            total: {
                addressed: 0,
                open: calculateReportDataTotalErrors()
            }
        },
        levels: {
            data: reportDataLevels,
            total: calculateReportDataTotalLevels()
        },
        licenses: {
            data: {
                declared: calculateNrPackagesLicenses(declaredLicensesFromAnalyzer),
                detected: calculateNrPackagesLicenses(detectedLicensesFromScanner)
            },
            total: {
                declared: calculateReportDataTotalLicenses(declaredLicensesFromAnalyzer),
                detected: calculateReportDataTotalLicenses(detectedLicensesFromScanner)
            }
        },
        packages: {
            data: {
                analyzer: packagesFromAnalyzer || {},
                scanner: packagesFromScanner || {}
            },
            total: calculateReportDataTotalPackages()
        },
        projects: {
            data: projects,
            total: calculatReportDataTotalProjects()
        },
        scopes: {
            data: reportDataScopes,
            total: calculateReportDataTotalScopes()
        },
        vcs: reportData.repository.vcs || {},
        vcs_processed: reportData.repository.vcs_processed || {}
    };
}

// Utility function to remove duplicates from Array
// https://codehandbook.org/how-to-remove-duplicates-from-javascript-array/
export function removeDuplicatesInArray(arr) {
    return Array.from(new Set(arr));
}

// SPDX-License-Identifier: MIT
// Author KimKha
// https://stackoverflow.com/questions/194846/is-there-any-kind-of-hash-code-function-in-javascript#8076436
export function hashCode(str) {
    let hash = 0;

    for (let i = 0; i < str.length; i++) {
        const character = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + character;
        // Convert to 32bit integer
        hash = hash & hash;
    }
    return hash;
}
