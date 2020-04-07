/*
 * Copyright (C) 2017-2020 HERE Europe B.V.
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

import React from 'react';
import {
    FileAddOutlined,
    FileExcelOutlined
} from '@ant-design/icons';
import memoizeOne from 'memoize-one';

const hasOrtResultChanged = (newArgs, oldArgs) => newArgs.length !== oldArgs.length
    || newArgs[0].data.reportLastUpdate !== oldArgs[0].data.reportLastUpdate;

const sortTableColumnFilterSelectors = (a, b) => a.text.localeCompare(b.text);

// ---- App selectors ----

export const getAppView = (state) => state.app;
export const getAppViewoading = (state) => state.app.loading;
export const getAppViewShowKey = (state) => state.app.showKey;

// ---- Data selectors ----

export const getOrtResult = (state) => state.data.ortResult;

// ---- AboutModal selectors ----

export const getCustomDataAsFlatArray = memoizeOne(
    (state) => {
        const webAppOrtResult = getOrtResult(state);

        if (webAppOrtResult.customData) {
            return Object.entries(webAppOrtResult.customData)
                .reduce((acc, [key, value]) => {
                    if (typeof value === 'string') {
                        acc.push([key, value]);
                    } else if (Array.isArray(value)) {
                        acc.push([key, value.join(', ')]);
                    } else {
                        Object.entries(value).forEach(([entryKey, entryValue]) => {
                            acc.push([entryKey, entryValue]);
                        });
                    }

                    return acc;
                }, []);
        }

        return null;
    },
    hasOrtResultChanged
);

// ---- SummaryView selectors ----

export const getSummaryDeclaredLicenses = memoizeOne(
    (state) => {
        const licenses = [];
        const webAppOrtResult = getOrtResult(state);
        const {
            statistics: {
                licenses: {
                    declared
                }
            }
        } = webAppOrtResult;

        declared.forEach((value, name) => {
            const license = webAppOrtResult.getLicenseByName(name);

            if (license) {
                licenses.push({
                    name,
                    value,
                    color: license.color
                });
            }
        });

        return licenses;
    },
    hasOrtResultChanged
);
export const getSummaryDeclaredLicensesChart = (state) => {
    const declaredLicenses = getSummaryDeclaredLicenses(state);

    if (state.summary.declaredLicensesChart.length === 0 && declaredLicenses.length !== 0) {
        return declaredLicenses;
    }

    return state.summary.declaredLicensesChart;
};
export const getSummaryDeclaredLicensesFilter = (state) => state.summary.declaredLicensesFilter;
export const getSummaryDetectedLicenses = memoizeOne(
    (state) => {
        const licenses = [];
        const webAppOrtResult = getOrtResult(state);
        const {
            statistics: {
                licenses: {
                    detected
                }
            }
        } = webAppOrtResult;

        detected.forEach((value, name) => {
            const license = webAppOrtResult.getLicenseByName(name);

            if (license) {
                licenses.push({
                    name,
                    value,
                    color: license.color
                });
            }
        });

        return licenses;
    },
    hasOrtResultChanged
);
export const getSummaryDetectedLicensesChart = (state) => {
    const detectedLicenses = getSummaryDetectedLicenses(state);

    if (state.summary.detectedLicensesChart.length === 0 && detectedLicenses.length !== 0) {
        return detectedLicenses;
    }

    return state.summary.detectedLicensesChart;
};
export const getSummaryDetectedLicensesFilter = (state) => state.summary.detectedLicensesFilter;
export const getSummaryIssuesFilter = (state) => state.summary.issuesFilter;
export const getSummaryRuleViolationsFilter = (state) => state.summary.ruleViolationsFilter;

export const getSummaryView = (state) => state.summary;
export const getSummaryViewShouldComponentUpdate = (state) => state.summary.shouldComponentUpdate;

// ---- TableView selectors ----

export const getTableView = (state) => state.table;
export const getTableViewShouldComponentUpdate = (state) => state.table.shouldComponentUpdate;
export const getTableViewDeclaredLicensesSelections = memoizeOne(
    (state) => {
        const webAppOrtResult = getOrtResult(state);
        const { declaredLicenses } = webAppOrtResult;
        return declaredLicenses
            .map(
                (license) => (
                    {
                        text: license,
                        value: webAppOrtResult.getLicenseByName(license).id
                    }
                )
            )
            .sort(sortTableColumnFilterSelectors);
    },
    hasOrtResultChanged
);
export const getTableViewDetectedLicensesSelections = memoizeOne(
    (state) => {
        const webAppOrtResult = getOrtResult(state);
        const { detectedLicenses } = webAppOrtResult;
        return detectedLicenses
            .map(
                (license) => (
                    {
                        text: license,
                        value: webAppOrtResult.getLicenseByName(license).id
                    }
                )
            )
            .sort(sortTableColumnFilterSelectors);
    },
    hasOrtResultChanged
);
export const getTableViewLevelFilterSelections = memoizeOne(
    (state) => {
        const webAppOrtResult = getOrtResult(state);
        const { levels } = webAppOrtResult;
        return levels
            .map(
                (level) => ({ text: level, value: level })
            );
    },
    hasOrtResultChanged
);
export const getTableViewProjectFilterSelections = memoizeOne(
    (state) => {
        const webAppOrtResult = getOrtResult(state);
        const { projects } = webAppOrtResult;
        return projects
            .map(
                (webAppPackage) => {
                    if (webAppOrtResult.hasPathExcludes()) {
                        if (webAppPackage.isExcluded) {
                            return {
                                text: (
                                    <span>
                                        <FileExcelOutlined
                                            className="ort-excluded"
                                        />
                                        {' '}
                                        {
                                            webAppPackage.definitionFilePath
                                                ? webAppPackage.definitionFilePath : webAppPackage.id
                                        }
                                    </span>
                                ),
                                value: webAppPackage.packageIndex
                            };
                        }

                        return {
                            text: (
                                <span>
                                    <FileAddOutlined />
                                    {' '}
                                    {
                                        webAppPackage.definitionFilePath
                                            ? webAppPackage.definitionFilePath : webAppPackage.id
                                    }
                                </span>
                            ),
                            value: webAppPackage.packageIndex
                        };
                    }

                    return {
                        text: webAppPackage.definitionFilePath
                            ? webAppPackage.definitionFilePath : webAppPackage.id,
                        value: webAppPackage.packageIndex
                    };
                }
            )
            .sort(sortTableColumnFilterSelectors);
    },
    hasOrtResultChanged
);
export const getTableViewScopeFilterSelections = memoizeOne(
    (state) => {
        const webAppOrtResult = getOrtResult(state);
        const { scopes } = webAppOrtResult;
        return scopes
            .map(
                (scope) => ({ text: scope.name, value: scope.id })
            )
            .sort(sortTableColumnFilterSelectors);
    },
    hasOrtResultChanged
);

// ---- TreeView selectors ----

export const getTreeView = (state) => state.tree;
export const getTreeViewShouldComponentUpdate = (state) => state.tree.shouldComponentUpdate;
