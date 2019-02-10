/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

import { delay, put, select } from 'redux-saga/effects';
import { getReportData } from '../reducers/selectors';
import { hashCode, removeDuplicatesInArray } from '../utils';

function* convertReportData() {
    const reportData = yield select(getReportData);
    yield delay(200);

    if (Object.keys(reportData).length === 0
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
    const reportDataAddressedErrors = {};
    let reportDataLevels = new Set([]);
    let reportDataScopes = new Set([]);

    // Add `key` to prevent warning rendering table with two children with same key
    const addKeyToArrayItems = arr => arr.map((item, index) => {
        const tmp = item;
        tmp.key = index;
        return tmp;
    });
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
    const addErrorsToReportDataReportData = (pkgObj) => {
        const pkg = pkgObj;
        const { errors } = pkg;
        let summaryPkgErrorObj;
        let summaryPkgWarningObj;

        if (Array.isArray(errors) && errors.length !== 0) {
            for (let i = errors.length - 1; i >= 0; i -= 1) {
                const error = errors[i];

                if (error.severity === 'ERROR') {
                    if (!reportDataOpenErrors[`${pkg.id}-errors`]) {
                        summaryPkgErrorObj = {
                            source: pkg.id,
                            severity: 'ERROR',
                            files: new Set([]),
                            message: new Set([])
                        };
                    } else {
                        summaryPkgErrorObj = reportDataOpenErrors[`${pkg.id}-errors`];
                    }

                    summaryPkgErrorObj.files.add(error.file);
                    summaryPkgErrorObj.message.add(error.message);

                    reportDataOpenErrors[`${pkg.id}-errors`] = summaryPkgErrorObj;
                } else {
                    if (!reportDataOpenErrors[`${pkg.id}-warnings`]) {
                        summaryPkgWarningObj = {
                            source: pkg.id,
                            severity: 'WARNING',
                            files: new Set([]),
                            message: new Set([])
                        };
                    } else {
                        summaryPkgWarningObj = reportDataOpenErrors[`${pkg.id}-warnings`];
                    }

                    summaryPkgWarningObj.files.add(error.file);
                    summaryPkgWarningObj.message.add(error.message);

                    reportDataOpenErrors[`${pkg.id}-warnings`] = summaryPkgWarningObj;
                }
            }
        }
    };
    const addErrorsToPackage = (projectIndex, pkgObj, analyzerErrors) => {
        const pkg = pkgObj;
        const project = projects[projectIndex];
        let errors;
        let errorsAnalyzer = [];
        let errorsScanner = [];

        const createErrorObj = (type, error) => ({
            id: project.id,
            code: `${hashCode(project.id)}x${hashCode(pkg.id)}${error.message.length}`,
            severity: error.severity,
            source: error.source,
            timestamp: error.timestamp,
            type,
            package: {
                id: pkg.id,
                path: pkg.path,
                level: pkg.level,
                scope: pkg.scope
            },
            file: project.definition_file_path,
            message: error.message
        });
        const packageFromScanner = packagesFromScanner[pkg.id] || false;

        if (analyzerErrors && project) {
            errorsAnalyzer = analyzerErrors.map(error => createErrorObj('ANALYZER_PACKAGE_ERROR', error));
        }

        if (packageErrorsFromAnalyzer && project) {
            if (packageErrorsFromAnalyzer[pkg.id]) {
                errorsAnalyzer = [
                    ...errorsAnalyzer,
                    ...packageErrorsFromAnalyzer[pkg.id].map(
                        error => createErrorObj('ANALYZER_PACKAGE_ERROR', error)
                    )
                ];
            }
        }

        if (packageFromScanner) {
            errors = packageFromScanner.reduce((accumulator, scanResult) => {
                if (!scanResult.summary.errors) {
                    return accumulator;
                }

                return accumulator.concat(scanResult.summary.errors);
            }, []);

            errorsScanner = errors.map(error => createErrorObj('SCANNER_PACKAGE_ERROR', error));
        }

        errors = [...errorsAnalyzer, ...errorsScanner];

        if (errors.length !== 0) {
            pkg.errors = errors;

            addErrorsToReportDataReportData(pkg);
        }

        return pkg;
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
    const addPackageLicensesToReportData = (reportDataLicenses, projectIndex, pkgObj, pkgLicenses) => {
        if (Array.isArray(pkgLicenses)) {
            for (let i = pkgLicenses.length - 1; i >= 0; i -= 1) {
                const license = pkgLicenses[i];
                const project = projects[projectIndex];
                let licenseOccurance = [];
                let licenseOccurances;

                if (!Object.prototype.hasOwnProperty.call(reportDataLicenses, projectIndex)) {
                    reportDataLicenses[projectIndex] = {}; // eslint-disable-line
                }

                if (!reportDataLicenses[projectIndex][license]) {
                    reportDataLicenses[projectIndex][license] = new Map(); // eslint-disable-line
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
    // Helper function to add license results
    // from Analyzer and Scanner to a package
    const addLicensesToPackage = (projectIndex, pkgObj) => {
        const pkg = pkgObj;
        const packageFromAnalyzer = packagesFromAnalyzer[pkg.id] || false;
        const packageFromScanner = packagesFromScanner[pkg.id] || false;

        if (pkg.id === projects[projectIndex].id) {
            // If package is a project then declared licenses
            // are in projects found by Analyzer
            pkg.declared_licenses = projectsFromAnalyzer[projectIndex].declared_licenses;
        } else if (packageFromAnalyzer) {
            pkg.declared_licenses = packageFromAnalyzer.declared_licenses;
        } else {
            pkg.declared_licenses = [];
            console.error(`Package ${pkg.id} can not be found in Analyzer results`); // eslint-disable-line
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
            pkg.results = packageFromScanner;

            pkg.license_findings = packageFromScanner
                .reduce((accumulator, scanResult) => accumulator.concat(scanResult.summary.license_findings), []);

            // Merge scan results from different scanners into array of license names
            pkg.detected_licenses = pkgObj.license_findings.reduce((accumulator, finding) => {
                accumulator.push(finding.license);
                return accumulator;
            }, []);

            // Remove duplicate license names after merging
            pkg.detected_licenses = removeDuplicatesInArray(pkgObj.detected_licenses);

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
            pkg.results = [];
            pkg.license_findings = [];
            pkg.detected_licenses = [];
            console.error(`Package ${pkg.id} was detected by Analyzer but not scanned`); // eslint-disable-line
        }

        return pkg;
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
    /* Helper function is called by recursivePackageAnalyzer
     * for each package. Designed to flatten tree of dependencies
     * into a single object so tree can be rendered as a table
     */
    const addPackageToProjectList = (projectIndex, pkgObj) => {
        let projectsListPkg;
        const pkg = pkgObj;

        projectsListPkg = projects[projectIndex].packages.list[pkg.id];

        if (!projectsListPkg) {
            pkg.levels = [pkgObj.level];
            pkg.paths = [];
            pkg.scopes = [];
            if (pkg.scope !== '') {
                pkg.scopes.push(pkg.scope);
            }

            projectsListPkg = pkg;
            projects[projectIndex].packages.list[pkg.id] = pkgObj;
            projects[projectIndex].packages.total += 1;
        } else {
            // Ensure each level only occurs once
            if (!projectsListPkg.levels.includes(pkg.level)) {
                projectsListPkg.levels.push(pkgObj.level);
            }

            if (pkg.scope !== '') {
                // Ensure each scope only occurs once
                if (!projectsListPkg.scopes.includes(pkg.scope)) {
                    projectsListPkg.scopes.push(pkg.scope);
                }
            }
        }

        if (pkg.scope !== '' && pkg.path.length !== 0) {
            projectsListPkg.paths.push({
                scope: pkg.scope,
                path: pkg.path
            });
        }

        addPackageLevelAndScopeToProject(projectIndex, pkgObj.level, pkg.scope);

        delete pkg.children;
        delete pkg.path;
        delete pkg.level;
        delete pkg.scope;

        return pkgObj;
    };
    const addPackageToProjectPackagesTreeNodes = (projectIndex, pkgObj) => {
        const project = projects[projectIndex];

        project.packages.treeNodes.push({
            id: pkgObj.id,
            key: pkgObj.key
        });
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
        const project = projects[projectIndex];
        const addLicensesToReportData = (reportDatalicenses, projectLicenses) => {
            const licenses = reportDatalicenses;

            if (Array.isArray(projectLicenses)) {
                for (let i = projectLicenses.length - 1; i >= 0; i -= 1) {
                    const license = projectLicenses[i];
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

        if (project) {
            addLicensesToReportData(
                declaredLicensesFromAnalyzer,
                project.declared_licenses
            );
            addLicensesToReportData(
                detectedLicensesFromScanner,
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
        const proj = project;
        const projectId = proj.id;
        const projectFromScanner = packagesFromScanner[projectId] || false;

        if (projectId && projectFromScanner) {
            proj.results = projectFromScanner;

            proj.packages.licenses.findings = projectFromScanner.reduce(
                (accumulator, scanResult) => accumulator.concat(
                    scanResult.summary.license_findings
                ),
                []
            );

            proj.packages.licenses.detected = removeDuplicatesInArray(
                proj.packages.licenses.findings.map(finding => finding.license)
            );
        }

        return proj;
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
    /* Helper function to recursive traverse over the packages
     * found by the Analyzer so they can be transformed
     * into a format that suitable for use in the WebApp
     */
    const recursivePackageAnalyzer = (
        projectIndex, pkg, childIndex, parentTreeId = '', dependencyPathFromRoot = [], scp = '', delivered
    ) => {
        const treeId = parentTreeId === '' ? `${childIndex}` : `${parentTreeId}-${childIndex}`;
        const children = Object.entries(pkg).reduce((accumulator, [key, value]) => {
            // Only recursively traverse objects which can hold packages
            if (key === 'dependencies' && value.length !== 0) {
                const depsChildren = value.map((dep, index) => recursivePackageAnalyzer(
                    projectIndex,
                    dep,
                    `${index}`,
                    treeId,
                    [...dependencyPathFromRoot, pkg.id || pkg.name],
                    scp, delivered
                ));
                accumulator.push(...depsChildren);
            }

            if (key === 'scopes' && value.length !== 0) {
                const scopeChildren = value
                    // Filter out scopes with no dependencies
                    .filter(scope => scope.dependencies.length !== 0)
                    // Loop over recursively over dependencies
                    // defined for each scope
                    .map((scope, scopeIndex) => scope.dependencies.map((dep, index) => recursivePackageAnalyzer(
                        projectIndex,
                        dep,
                        `${scopeIndex}-${index}`,
                        treeId,
                        [...dependencyPathFromRoot, pkg.name || pkg.id],
                        scope.name,
                        scope.delivered
                    )))
                    // Flatten array of arrays into a single array
                    // as each scope is on the same level
                    .reduce((acc, scopeDeps) => [...acc, ...scopeDeps], []);

                accumulator.push(...scopeChildren);
            }

            return accumulator;
        }, []);
        let pkgObj = {
            id: pkg.id || pkg.name,
            key: treeId,
            projectKey: projectIndex,
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
        } else {
            const project = projects[projectIndex];
            pkgObj.definition_file_path = project.definition_file_path;
        }

        pkgObj = addErrorsToPackage(projectIndex, pkgObj, pkg.errors || []);

        // Project list is merges multiple occurrances of the same package
        // into same listing modifying pkgObj therefore we make a copy
        addPackageToProjectList(projectIndex, new Proxy({ ...pkgObj }, packageProxyHandler));

        addPackageToProjectPackagesTreeNodes(projectIndex, pkgObj);

        return new Proxy(pkgObj, packageProxyHandler);
    };

    // Traverse over projects
    for (let i = projectsFromAnalyzer.length - 1; i >= 0; i -= 1) {
        const project = projectsFromAnalyzer[i];
        const projectIndex = i;
        let projectFile = project.definition_file_path;

        // Add ./ so we never have empty string
        projectFile = `./${projectFile}`;
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
                    tree: [],
                    treeNodes: []
                },
                vcs: project.vcs,
                vcs_processed: project.vcs_processed
            });
        }

        projects[projectIndex].packages.tree = recursivePackageAnalyzer(projectIndex, project, i);
        // Sort the tree nodes so parent comes before children in array
        // so tree search will return results in the right order
        projects[projectIndex].packages.treeNodes.sort((a, b) => {
            const keysA = a.key.split('-');
            const keysB = b.key.split('-');
            const keysLength = keysA.length < keysB.length ? keysA.length : keysB.length;

            for (let j = 0; j < keysLength; j++) {
                if (keysA[j] !== keysB[j]) {
                    return keysA[j] - keysB[j];
                }
            }

            if (keysA.length < keysB.length) {
                return -1;
            }

            if (keysA.length > keysB.length) {
                return 1;
            }

            return 0;
        });

        addProjectLicensesToReportData(projectIndex);
        addProjectLevelsToReportDataReportData(projectIndex);
        addProjectScopesToReportDataReportData(projectIndex);

        // As packages are added recursively to get an array
        // with the right order we need to reverse it
        projects[projectIndex].packages.list = Object.values(
            projects[projectIndex].packages.list
        ).reverse();

        yield delay(50);
        yield put({
            type: 'APP::LOADING_CONVERTING_REPORT',
            index: projectsFromAnalyzer.length - i,
            total: projectsFromAnalyzer.length
        });
    }

    const convertedData = {
        hasErrors: reportData.has_errors || false,
        errors: {
            // Flatten errors into an array of errors
            addressed: addKeyToArrayItems(Object.values(reportDataAddressedErrors)) || [],
            open: addKeyToArrayItems(Object.values(reportDataOpenErrors)) || []
        },
        levels: reportDataLevels || new Set(),
        licenses: {
            declared: declaredLicensesFromAnalyzer,
            detected: detectedLicensesFromScanner
        },
        metadata: (reportData.data)
            ? { ...reportData.data.job_parameters, ...reportData.data.process_parameters } : {},
        packages: {
            analyzer: packagesFromAnalyzer || {},
            scanner: packagesFromScanner || {}
        },
        projects,
        scopes: reportDataScopes || new Set(),
        repository: reportData.repository || {},
        violations: {
            addressed: addKeyToArrayItems([]),
            open: (reportData.evaluator && reportData.evaluator.errors)
                ? addKeyToArrayItems(reportData.evaluator.errors) : []
        }
    };

    yield put({ type: 'APP::LOADING_CONVERTING_REPORT_DONE', payload: convertedData });
    yield delay(300);
    yield put({ type: 'APP::LOADING_DONE' });
    yield delay(300);
    yield put({ type: 'APP::SHOW_TABS' });

    return convertedData;
}

export default convertReportData;
