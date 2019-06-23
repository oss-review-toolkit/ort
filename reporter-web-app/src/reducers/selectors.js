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

const hasOrtResultChanged = (newArgs, oldArgs) => newArgs.length !== oldArgs.length
    || newArgs[0].data.reportLastUpdate !== oldArgs[0].data.reportLastUpdate;

// ---- App selectors ----

export const getAppView = state => state.app;
export const getAppViewoading = state => state.app.loading;
export const getAppViewShowKey = state => state.app.showKey;

// ---- Data selectors ----

export const getOrtResult = state => state.data.ortResult;

// ---- SummaryView selectors ----

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
const getLicensesWithNrPackages = (state, licensesProp) => {
    const webAppOrtResult = getOrtResult(state);
    const packages = webAppOrtResult.getPackages();
    const nrPackagesByLicense = {};

    if (packages) {
        for (let i = packages.length - 1; i >= 0; i -= 1) {
            const pkg = packages[i];
            const licenses = pkg[licensesProp];

            for (let j = licenses.length - 1; j >= 0; j -= 1) {
                const license = licenses[j];
                if (!nrPackagesByLicense[license]) {
                    nrPackagesByLicense[license] = 0;
                }

                nrPackagesByLicense[license] += 1;
            }
        }

        return nrPackagesByLicense;
    }

    return {};
};
export const getSummaryDeclaredLicenses = memoizeOne(
    state => getLicensesWithColors(getLicensesWithNrPackages(state, 'declaredLicenses')),
    hasOrtResultChanged
);
export const getSummaryDeclaredLicensesChart = (state) => {
    const declaredLicenses = getSummaryDeclaredLicenses(state);

    if (state.summary.licenses.declaredChart.length === 0 && declaredLicenses.length !== 0) {
        return declaredLicenses;
    }

    return state.summary.licenses.declaredChart;
};
export const getSummaryDeclaredLicensesFilter = state => state.summary.licenses.declaredFilter;
export const getSummaryDeclaredLicensesTotal = memoizeOne(
    (state) => {
        const webAppOrtResult = getOrtResult(state);
        const { declaredLicenses } = webAppOrtResult;

        return declaredLicenses.length;
    },
    hasOrtResultChanged
);

export const getSummaryDetectedLicenses = memoizeOne(
    state => getLicensesWithColors(getLicensesWithNrPackages(state, 'detectedLicenses')),
    hasOrtResultChanged
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
    (state) => {
        const webAppOrtResult = getOrtResult(state);
        const { detectedLicenses } = webAppOrtResult;

        return detectedLicenses.length;
    },
    hasOrtResultChanged
);

export const getSummaryView = state => state.summary;
export const getSummaryViewShouldComponentUpdate = state => state.summary.shouldComponentUpdate;

// ---- TableView selectors ----

export const getTableView = state => state.table;
export const getTableViewShouldComponentUpdate = state => state.table.shouldComponentUpdate;

// ---- TreeView selectors ----

export const getTreeView = state => state.tree;
export const getTreeViewShouldComponentUpdate = state => state.tree.shouldComponentUpdate;
