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

import memoizeOne from 'memoize-one';
import { UNIQUE_COLORS } from '../data/colors';

const COLORS = UNIQUE_COLORS.data;
const licenseColors = new Map();
const getLicenseColor = (license) => {
    if (licenseColors.has(license)) {
        return licenseColors.get(license);
    }

    // License has no assigned color
    const nrColors = COLORS.length;
    const color = COLORS[licenseColors.size % nrColors];
    licenseColors.set(license, color);

    return color;
};
const getLicensesWithColors = licenses => Object.entries(licenses)
    .reduce((accumulator, [key, value]) => {
        accumulator.push({
            name: key,
            value,
            color: getLicenseColor(key)
        });

        return accumulator;
    }, []);
const getLicensesWithNrPackages = projectsLicenses => Object.values(projectsLicenses)
    .reduce((accumulator, projectLicenses) => {
        const keys = Object.keys(projectLicenses);
        for (let i = keys.length - 1; i >= 0; i -= 1) {
            const license = keys[i];
            const licenseMap = projectLicenses[license];

            if (!accumulator[license]) {
                accumulator[license] = 0;
            }

            accumulator[license] += licenseMap.size;
        }

        return accumulator;
    }, {});
const getTotalNrLicenses = (projectsLicenses) => {
    const licensesSet = new Set([]);

    return Object.values(projectsLicenses).reduce((accumulator, projectLicenses) => {
        const keys = Object.keys(projectLicenses);
        for (let i = keys.length - 1; i >= 0; i -= 1) {
            const license = keys[i];
            accumulator.add(license);
        }

        return accumulator;
    }, licensesSet).size;
};
const hasReportDataChanged = (newArg, lastArg) => newArg.data.reportLastUpdate === lastArg.data.reportLastUpdate;


// ---- App selectors ----

export const getAppView = state => state.app;
export const getAppViewoading = state => state.app.loading;
export const getAppViewShowKey = state => state.app.showKey;

// ---- Data selectors ----

export const getReportData = state => state.data.report;
export const getReportMetaData = state => state.data.report.metadata;
export const getReportErrorsAdressed = state => state.data.report.errors.addressed;
export const getReportErrorsAdressedTotal = memoizeOne(
    state => state.data.report.errors.addressed.length || 0,
    hasReportDataChanged
);
export const getReportErrorsOpen = state => state.data.report.errors.open;
export const getReportErrorsOpenTotal = memoizeOne(
    state => state.data.report.errors.open.length || 0,
    hasReportDataChanged
);
export const getReportViolationsAdressed = state => state.data.report.violations.addressed;
export const getReportViolationsAdressedTotal = memoizeOne(
    state => state.data.report.violations.addressed.length || 0,
    hasReportDataChanged
);
export const getReportViolationsOpen = state => state.data.report.violations.open;
export const getReportViolationsOpenTotal = memoizeOne(
    state => state.data.report.violations.open.length || 0,
    hasReportDataChanged
);
export const getReportLevels = state => state.data.report.levels;
export const getReportLevelsTotal = memoizeOne(
    (state) => {
        const levels = getReportLevels(state);
        return levels && levels.size ? levels.size : 0;
    },
    hasReportDataChanged
);
export const getReportPackages = state => state.data.report.packages;
export const getReportPackagesTotal = memoizeOne(
    (state) => {
        const { analyzer: packagesFromAnalyzer } = getReportPackages(state);
        return packagesFromAnalyzer
            && typeof packagesFromAnalyzer === 'object' ? Object.keys(packagesFromAnalyzer).length : 0;
    },
);
export const getReportProjects = state => state.data.report.projects;
export const getReportProjectsTotal = memoizeOne(
    (state) => {
        const projects = getReportProjects(state);
        return projects && typeof projects === 'object' ? Object.keys(projects).length : 0;
    },
);
export const getReportScopes = state => state.data.report.scopes;
export const getReportScopesTotal = memoizeOne(
    (state) => {
        const scopes = getReportScopes(state);
        return scopes && scopes.size ? scopes.size : 0;
    },
    hasReportDataChanged
);

// ---- SummaryView selectors ----

export const getSummaryDeclaredLicenses = memoizeOne(
    state => getLicensesWithColors(getLicensesWithNrPackages(state.data.report.licenses.declared)),
    hasReportDataChanged
);
export const getSummaryDeclaredLicensesFilter = state => state.summary.licenses.declaredFilter;
export const getSummaryDeclaredLicensesChart = (state) => {
    const declaredLicenses = getSummaryDeclaredLicenses(state);

    if (state.summary.licenses.declaredChart.length === 0 && declaredLicenses.length !== 0) {
        return declaredLicenses;
    }

    return state.summary.licenses.declaredChart;
};
export const getSummaryDeclaredLicensesTotal = memoizeOne(
    state => getTotalNrLicenses(state.data.report.licenses.declared),
    hasReportDataChanged
);
export const getSummaryDetectedLicenses = memoizeOne(
    state => getLicensesWithColors(getLicensesWithNrPackages(state.data.report.licenses.detected)),
    hasReportDataChanged
);
export const getSummaryDetectedLicensesChart = (state) => {
    const detectedLicenses = getSummaryDetectedLicenses(state);

    if (state.summary.licenses.detectedChart.length === 0 && detectedLicenses.length !== 0) {
        return detectedLicenses;
    }

    return state.summary.licenses.detectedChart;
};
export const getSummaryDetectedLicensesFilter = state => state.summary.licenses.detectedFilter;
export const getSummaryDetectedLicensesTotal = memoizeOne(
    state => getTotalNrLicenses(state.data.report.licenses.detected),
    hasReportDataChanged
);
export const getSummaryRepository = (state) => {
    const { data: { report: { repository: { vcs, vcs_processed: vcsProcessed } } } } = state;

    return {
        type: vcsProcessed.type || vcs.type || 'n/a',
        revision: vcsProcessed.revision || vcs.revision || 'n/a',
        url: vcsProcessed.url || vcs.url || 'n/a'
    };
};
export const getSummaryView = state => state.summary;
export const getSummaryViewShouldComponentUpdate = state => state.summary.shouldComponentUpdate;

// ---- TableView selectors ----

export const getSingleTable = memoizeOne(
    (state) => {
        const table = {
            columns: {
                levelsFilter: [],
                licensesDeclaredFilter: [],
                licensesDetectedFilter: [],
                projectsFilter: [],
                scopesFilter: []
            },
            data: []
        };
        const reportData = getReportData(state);

        if (reportData) {
            const {
                levels,
                licenses,
                projects,
                scopes
            } = reportData;

            if (projects) {
                table.data = Object.values(reportData.projects).reduce((accumulator, project) => {
                    if (project.packages && project.packages.list) {
                        accumulator.push(...project.packages.list);
                    }

                    return accumulator;
                }, []);

                table.columns.projectsFilter = Object.values(reportData.projects)
                    .reduce((accumulator, project) => {
                        if (project.definition_file_path) {
                            accumulator.push(project.definition_file_path);
                        }

                        return accumulator;
                    }, [])
                    .map(
                        (project, index) => ({
                            text: project,
                            value: index
                        })
                    );
            }

            if (levels) {
                table.columns.levelsFilter = Array.from(levels)
                    .sort().map(
                        level => ({
                            text: level,
                            value: level
                        })
                    );
            }

            if (licenses) {
                const { declared, detected } = licenses;
                table.columns.licensesDeclaredFilter = Object.keys(getLicensesWithNrPackages(declared))
                    .sort().map(
                        license => ({
                            text: license,
                            value: license
                        })
                    );
                table.columns.licensesDetectedFilter = Object.keys(getLicensesWithNrPackages(detected))
                    .sort().map(
                        license => ({
                            text: license,
                            value: license
                        })
                    );
            }

            if (scopes) {
                table.columns.scopesFilter = Array.from(scopes)
                    .sort().map(
                        scope => ({
                            text: scope,
                            value: scope
                        })
                    );
            }

            return table;
        }

        return [];
    },
    hasReportDataChanged
);

export const getTableView = state => state.table;
export const getTableViewShouldComponentUpdate = state => state.table.shouldComponentUpdate;

// ---- TreeView selectors ----

export const getSingleTree = memoizeOne(
    (state) => {
        const reportData = getReportData(state);

        if (reportData && reportData.projects && reportData.projects) {
            return Object.values(reportData.projects).reduce((accumulator, project) => {
                if (project.packages && project.packages.tree) {
                    accumulator.push(project.packages.tree);
                }

                return accumulator;
            }, []);
        }

        return [];
    },
    hasReportDataChanged
);
export const getSingleTreeNodes = memoizeOne(
    (state) => {
        const reportData = getReportData(state);

        if (reportData && reportData.projects && reportData.projects) {
            return Object.values(reportData.projects).reduce((accumulator, project) => {
                if (project.packages && project.packages.treeNodes) {
                    return [...accumulator, ...project.packages.treeNodes];
                }

                return accumulator;
            }, []);
        }

        return [];
    },
    hasReportDataChanged
);
export const getTreeView = state => state.tree;
export const getTreeViewShouldComponentUpdate = state => state.tree.shouldComponentUpdate;
