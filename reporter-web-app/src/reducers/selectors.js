/*
 * Copyright (C) 2017-2021 HERE Europe B.V.
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

// ---- SummaryView selectors ----

export const getSummaryColumns = (state) => state.summary.columns;
export const getSummaryDeclaredLicensesProcessed = memoizeOne(
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

export const getSummaryCharts = (state) => {
    const declaredLicensesProcessed = getSummaryDeclaredLicensesProcessed(state);
    const detectedLicensesProcessed = getSummaryDetectedLicensesProcessed(state);
    let charts = state.summary.charts;

    if (state.summary.charts.declaredLicensesProcessed.length === 0 && declaredLicensesProcessed.length !== 0) {
        charts.declaredLicensesProcessed = declaredLicensesProcessed;
    }

    if (state.summary.charts.detectedLicensesProcessed.length === 0 && detectedLicensesProcessed.length !== 0) {
        charts.detectedLicensesProcessed = detectedLicensesProcessed;
    }

    return charts;
};

export const getSummaryDetectedLicensesProcessed = memoizeOne(
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

export const getSummaryStats = (state) => {
    return {
        declaredLicensesProcessed: getSummaryDeclaredLicensesProcessed(state),
        detectedLicensesProcessed: getSummaryDetectedLicensesProcessed(state)
    };
};

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
export const getTableViewDeclaredLicensesProcessedSelections = memoizeOne(
    (state) => {
        const webAppOrtResult = getOrtResult(state);
        const { declaredLicensesProcessed } = webAppOrtResult;
        return declaredLicensesProcessed
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
            .sort((a, b) => a.id.localeCompare(b.id))
            .map(
                (webAppPackage) => {
                    const text = webAppPackage.definitionFilePath
                        ? webAppPackage.definitionFilePath : webAppPackage.id;

                    if (webAppOrtResult.hasPathExcludes()) {
                        if (webAppPackage.isExcluded) {
                            return {
                                text: (
                                    <span>
                                        <FileExcelOutlined
                                            className="ort-excluded"
                                        />
                                        {' '}
                                        {text}
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
                                    {text}
                                </span>
                            ),
                            value: webAppPackage.packageIndex
                        };
                    }

                    return {
                        text,
                        value: webAppPackage.packageIndex
                    };
                }
            );
    },
    hasOrtResultChanged
);
export const getTableViewScopeFilterSelections = memoizeOne(
    (state) => {
        const webAppOrtResult = getOrtResult(state);
        const { scopes } = webAppOrtResult;
        return scopes
            .sort((a, b) => a.name.localeCompare(b.name))
            .map(
                (webAppScope) => {
                    if (webAppOrtResult.hasScopeExcludes()) {
                        if (webAppScope.isExcluded) {
                            return {
                                text: (
                                    <span>
                                        <FileExcelOutlined
                                            className="ort-excluded"
                                        />
                                        {' '}
                                        {
                                            webAppScope.name
                                        }
                                    </span>
                                ),
                                value: webAppScope.id
                            };
                        }

                        return {
                            text: (
                                <span>
                                    <FileAddOutlined />
                                    {' '}
                                    {
                                        webAppScope.name
                                    }
                                </span>
                            ),
                            value: webAppScope.id
                        };
                    }

                    return {
                        text: webAppScope.name,
                        value: webAppScope.id
                    };
                }
            );
    },
    hasOrtResultChanged
);

// ---- TreeView selectors ----

export const getTreeView = (state) => state.tree;
export const getTreeViewShouldComponentUpdate = (state) => state.tree.shouldComponentUpdate;
