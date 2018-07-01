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

// FIXME This data conversion should for performance reasons be done in the Kotlin code
// Converts data format used by ORT Reporter
// into a form that projectTable can render
export function convertToProjectTableFormat(reportData) {
    var declaredLicenses = {},
        detectedLicenses = {},
        errors = {},
        key = 0,
        levels = {},
        packagesFromAnalyzerById = {},
        packagesByDefinitionFilePath = {},
        packagesByDefinitionFilePathById = {},
        packagesFromScannerById = {},
        projectsFromAnalyzer = reportData.analyzer_result.projects,
        projectsFromAnalyzerLength = projectsFromAnalyzer.length,
        scopesOfPackagesByDefinitionFilePath = {};

    // Transform Analyer results to be indexed by package Id for faster lookups
    (function traversePackagesFromAnalyzer(dataArr) {
        var arrLength = dataArr.length;
        for (var i = 0; i < arrLength; i++) {
            packagesFromAnalyzerById[dataArr[i].package.id] = dataArr[i].package;
        }
    })(reportData.analyzer_result.packages);

    // Transform Scanner results to be indexed by package Id for faster lookups
    (function traversePackagesFromScanner(dataArr) {
        var arrLength = dataArr.length;
        for (let i = 0; i < arrLength; i++) {
            packagesFromScannerById[dataArr[i].id] = dataArr[i].results;
        }
    })(reportData.scan_results);

    // Function to recursively loop Analyzer results
    // and transform them into the format ReactTable accepts
    function traverseObjectsFromAnalyzer(definitionFilePath, dataObj, dependencyPathFromRoot) {
        var declaredLicense,
            detectedLicense,
            dependencyPaths = [],
            packageId,
            packageAnalyzerErrors = reportData.analyzer_result.errors,
            packageDeclaredLicenses = [],
            packageDeclaredLicensesLength = 0,
            packageDetectedLicenses = [],
            packageDetectedLicensesLength = 0,
            packageDependencies,
            packageDependenciesLength,
            packageErrors = {
                analyzer: [],
                scanner: [],
                total: 0
            },
            packageLevels = [],
            packageScanResults,
            packageScanResultsLength,
            packageScannerErrors = [],
            packageScopes = [],
            packageScopesLength,
            tmp;

        if (!dependencyPathFromRoot) {
            dependencyPathFromRoot = [];
        } else {
            dependencyPaths = [dependencyPathFromRoot];
        }

        if (!scopesOfPackagesByDefinitionFilePath[definitionFilePath]) {
            scopesOfPackagesByDefinitionFilePath[definitionFilePath] = {};
        }

        if (!levels[definitionFilePath]) {
            levels[definitionFilePath] = 0;
        }

        if (typeof dataObj === 'object') {
            // Only packages have id attribute
            if (dataObj['id']) {
                packageId = dataObj['id'];

                // Only root packages have definition file attribute
                if (dataObj.hasOwnProperty('definition_file_path')) {
                    // FIXME Discuss with Martin if below is intended or a bug
                    // All projects are missing from analyzer_result.packages
                    // Fixing this by copying over missing entries
                    if (!packagesFromAnalyzerById[packageId]) {
                        packagesFromAnalyzerById[packageId] = {
                            "declared_licenses": dataObj.declared_licenses,
                            "aliases": dataObj.aliases,
                            "vcs": dataObj.vcs,
                            "vcs_processed": dataObj.vcs_processed,
                            "homepage_url": dataObj.homepage_url
                        };
                    }
                }

                // Parse scopes information within Analyzer results
                // and for those package that have scopes
                // store it in scopesOfPackagesByDefinitionFilePath 
                // with tuple <definitionFilePath, packageId>
                // e.g. <'pom.xml', 'Maven:com.google.protobuf:protobuf-java:3.4.0'>
                if (dataObj['scopes'] && dataObj['scopes'].length > 0) {
                    packageScopes = dataObj['scopes'];

                    packageScopesLength = packageScopes.length;
                    for (let x = 0; x < packageScopesLength; x++) {
                        packageDependencies = packageScopes[x].dependencies
                        if (packageDependencies && packageScopes[x].name) {
                            packageDependencies = packageScopes[x].dependencies;
                            packageDependenciesLength = packageDependencies.length;
                            for (let y = 0; y < packageDependenciesLength; y++) {
                                if (!scopesOfPackagesByDefinitionFilePath[definitionFilePath][packageId] && packageDependencies[y].id && packageScopes[x].name) {
                                    scopesOfPackagesByDefinitionFilePath[definitionFilePath][packageDependencies[y].id] = packageScopes[x].name;
                                }
                            }
                        }
                    }

                    // Reset so we can re-use variable later
                    packageScopes = [];
                }

                if (packagesFromAnalyzerById[packageId]) {
                    packageDeclaredLicenses = packagesFromAnalyzerById[packageId].declared_licenses;
                }

                if (packagesFromScannerById[packageId]) {
                    packageScanResults = packagesFromScannerById[packageId];
                    packageScanResultsLength = packageScanResults.length;

                    // Loop over results from various license scanners
                    for (let x = 0; x < packageScanResultsLength; x++) {
                        // Merge license results into a single array
                        packageDetectedLicenses = [...packageDetectedLicenses, ...packageScanResults[x].summary.licenses];

                        // Merge scan errors into a single array
                        packageScannerErrors = [...packageScannerErrors, ...packageScanResults[x].summary.errors];
                    }
                }

                // Ensure each license only appears once
                packageDetectedLicenses = removeDuplicatesInArray(packageDetectedLicenses);

                // Set value for when no declared licenses are found
                if (packageDeclaredLicenses && packageDeclaredLicenses.length === 0) {
                    packageDeclaredLicenses = ["NONE"];
                }

                // Set value for when no detected licenses are found
                if (packageDetectedLicenses && packageDetectedLicenses.length === 0) {
                    packageDetectedLicenses = ['NONE'];
                }

                if (!packagesByDefinitionFilePathById[definitionFilePath]) {
                    packagesByDefinitionFilePathById[definitionFilePath] = {};
                }

                if (packageAnalyzerErrors[packageId]) {
                    packageErrors.analyzer = packageAnalyzerErrors[packageId];
                }
                
                packageErrors.scanner = packageScannerErrors;
                packageErrors.total = packageErrors.analyzer.length + packageErrors.scanner.length;

                if (!packagesByDefinitionFilePathById[definitionFilePath][packageId]) {
                    if (scopesOfPackagesByDefinitionFilePath[definitionFilePath][packageId]) {
                        packageScopes = [scopesOfPackagesByDefinitionFilePath[definitionFilePath][packageId]];
                        // As id of package was not yet registered it must be a child of root package
                        // therefore setting level to 1
                        packageLevels.push(1);
                        levels[definitionFilePath] = 1;
                    } else {
                        for (let x = 0; x < dependencyPaths.length; x++) {
                            if (dependencyPaths[x].length > 1 && dependencyPaths[x][1]) {
                                packageLevels.push(dependencyPaths[x].length);
                                packageScopes = scopesOfPackagesByDefinitionFilePath[definitionFilePath][(dependencyPaths[x][1])]

                                if (packageScopes) {
                                    packageScopes = [packageScopes];
                                } else {
                                    packageScopes = [];
                                }
                            }
                        }
                    }

                    // Add for root packages level information
                    // which was not set above as root packages
                    // do not have scopes or dependency paths > 1
                    if (packageLevels.length === 0) {
                        packageLevels.push(0);
                    }

                    key = key + 1;

                    tmp = packagesByDefinitionFilePath[definitionFilePath].push({
                        'key': key,
                        'id': packageId,
                        'scopes': packageScopes,
                        'levels': packageLevels,
                        'declaredLicenses': packageDeclaredLicenses,
                        'detectedLicenses': packageDetectedLicenses,
                        'dependencyPaths': dependencyPaths,
                        'errors': packageErrors
                    });

                    // Store index of where package was inserted
                    packagesByDefinitionFilePathById[definitionFilePath][packageId] = tmp - 1;

                    // Add found declared licenses for a package to returned output
                    packageDeclaredLicensesLength = packageDeclaredLicenses.length;
                    for (let x = 0; x < packageDeclaredLicensesLength; x++) {
                        tmp = declaredLicenses[definitionFilePath];

                        if (!tmp) {
                            tmp = declaredLicenses[definitionFilePath] = {};
                        }

                        declaredLicense = packageDeclaredLicenses[x];
                        
                        if (!tmp[declaredLicense]) {
                            tmp[declaredLicense] = [];
                        }
                        
                        tmp[declaredLicense].push(packageId);
                    }

                    // Add found detected licenses for a package to returned output
                    packageDetectedLicensesLength = packageDetectedLicenses.length;
                    for (let x = 0; x < packageDetectedLicensesLength; x++) {
                        tmp = detectedLicenses[definitionFilePath];

                        if (!tmp) {
                            tmp = detectedLicenses[definitionFilePath] = {};
                        }

                        detectedLicense = packageDetectedLicenses[x];
                        
                        if (!tmp[detectedLicense]) {
                            tmp[detectedLicense] = [];
                        }
                        
                        tmp[detectedLicense].push(packageId);
                    }

                    // If errors occurred for package add these to returned output
                    if (packageErrors.total !== 0) {
                        tmp = errors[definitionFilePath];

                        if (!tmp) {
                            tmp = errors[definitionFilePath] = [];
                        }
                        
                        tmp.push(packageId);
                    }
                } else {
                    // PackageId occurs more than once in tree
                    tmp = packagesByDefinitionFilePath[definitionFilePath][packagesByDefinitionFilePathById[definitionFilePath][packageId]];

                    for (let y = 0; y < dependencyPaths.length; y++) {
                        if (dependencyPaths[y].length > 1 && dependencyPaths[y][1]) {
                            packageScopes = scopesOfPackagesByDefinitionFilePath[definitionFilePath][(dependencyPaths[y][1])]

                            tmp.levels.push(dependencyPaths[y].length);
                            tmp.levels = removeDuplicatesInArray(tmp.levels);
                        
                            if (packageScopes) {
                                tmp.scopes.push(packageScopes);
                                tmp.scopes = removeDuplicatesInArray(tmp.scopes);
                            }
                        }
                    }

                    tmp.dependencyPaths.push(dependencyPaths[0]);
                }

                dependencyPathFromRoot.push(packageId);
                
                // Level equals the size of path from root package to current package
                if (dependencyPathFromRoot.length > levels[definitionFilePath]) {
                    levels[definitionFilePath] = dependencyPathFromRoot.length;
                }
                /*
                levels[definitionFilePath] = [];

                for (let y = 0; y < dependencyPaths[x].length; y++) {
                    levels[definitionFilePath].push(y);
                }
                */
            }

            Object.entries(dataObj).forEach(([key, value]) => {
                    // Only recursively traverse objects which can hold packages
                    if ((key === 'dependencies' || key === 'scopes' || isNumeric(key))) {
                        traverseObjectsFromAnalyzer(definitionFilePath, value, JSON.parse(JSON.stringify(dependencyPathFromRoot)));
                    }
            });
        }
    }

    for (let i = 0; i < projectsFromAnalyzerLength ; i++) {
        let filePath = projectsFromAnalyzer[i].definition_file_path,
                tmp = [];
        
        packagesByDefinitionFilePath[filePath] = [];
        traverseObjectsFromAnalyzer(filePath, projectsFromAnalyzer[i], []);

        // Convert levels from recording highest level into array with level values
        for (let j = 0; j < levels[filePath]; j++) {
            tmp.push(j);
        }

        levels[filePath] = tmp;
    }

    return {
        declaredLicenses: declaredLicenses,
        detectedLicenses: detectedLicenses,
        errors: errors,
        levels: levels,
        original: reportData,
        projects: packagesByDefinitionFilePath,
        packagesMetaInfo: packagesFromAnalyzerById
    };
}

// Utility function to remove duplicates from Array
// https://codehandbook.org/how-to-remove-duplicates-from-javascript-array/
export function removeDuplicatesInArray(arr) {
    let uniqueArr = Array.from(new Set(arr));
    return uniqueArr;
}