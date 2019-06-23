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
import { getOrtResult } from '../reducers/selectors';
import WebAppOrtIssueAnalyzer from '../models/WebAppOrtIssueAnalyzer';
import WebAppOrtResult from '../models/WebAppOrtResult';
import WebAppPackage from '../models/WebAppPackage';
import WebAppPath from '../models/WebAppPath';

/* Helper function to recursive traverse over the packages
 * found by the Analyzer so they can be transformed
 * into a format that suitable for use in the WebApp
 */
function recursiveAnalyzerResultProcessor(
    webAppOrtResult,
    projectIndex,
    pkg,
    childIndex,
    parentTreeId = '',
    path = [],
    scp = '',
    delivered
) {
    const treeId = parentTreeId === '' ? `${childIndex}` : `${parentTreeId}-${childIndex}`;
    const children = [];
    const { dependencies, errors, scopes } = pkg;
    const id = pkg.id || pkg.name;
    const level = path.length;
    const scanResultContainer = webAppOrtResult.getScanResultContainerForPackageId(id);
    let webAppPkg = null;
    let webAppPkgTreeNode = null;

    // Only recursively traverse objects which can hold packages
    if (dependencies && dependencies.length !== 0) {
        const depsChildren = dependencies.map((dep, index) => recursiveAnalyzerResultProcessor(
            webAppOrtResult,
            projectIndex,
            dep,
            `${index}`,
            treeId,
            [
                ...path,
                new WebAppPath({ pkg: id, scope: scp })
            ],
            scp,
            delivered
        ));

        children.push(...depsChildren);
    }

    if (scopes && scopes.length !== 0) {
        const scopeChildren = scopes
            // Filter out scopes with no dependencies
            .filter(scope => scope.dependencies.length !== 0)
            // Loop over recursively over dependencies defined for each scope
            .map((scope, scopeIndex) => scope.dependencies.map((dep, index) => recursiveAnalyzerResultProcessor(
                webAppOrtResult,
                projectIndex,
                dep,
                `${scopeIndex}-${index}`,
                treeId,
                [
                    ...path,
                    new WebAppPath({ pkg: id, scope: scope.name })
                ],
                scope.name,
                scope.delivered
            )))
            // Flatten array of arrays into a single array
            // as each scope is on the same level
            .reduce((acc, scopeDeps) => [...acc, ...scopeDeps], []);

        children.push(...scopeChildren);
    }


    if (path.length === 0 && !webAppOrtResult.hasPackageId(id)) {
        const webAppProjectPkg = new WebAppPackage(webAppOrtResult.getProjectById(id).toPackage());
        webAppOrtResult.addPackage(id, webAppProjectPkg);
    }

    if (!webAppOrtResult.hasPackageId(id)) {
        webAppOrtResult.addPackage(id, new WebAppPackage({ id }));
    }

    webAppPkg = webAppOrtResult.getPackageById(id);
    webAppPkg.addLevel(level);
    webAppPkg.addPath(path);
    webAppPkg.addProjectIndex(projectIndex);
    webAppPkg.addScope(scp);

    webAppOrtResult.addLevel(level);

    // If scan results are available add detected licenses to package
    if (scanResultContainer && webAppPkg.detectedLicenses.length === 0) {
        webAppPkg.detectedLicenses = scanResultContainer.getAllDetectedLicenses();
    }

    webAppPkgTreeNode = new WebAppPackage(webAppPkg);
    webAppPkgTreeNode.children = children;
    webAppPkgTreeNode.delivered = delivered || false;
    webAppPkgTreeNode.key = treeId;
    webAppPkgTreeNode.projectIndexes = [projectIndex];
    webAppPkgTreeNode.levels = new Set([level]);
    webAppPkgTreeNode.paths = [path];
    if (scp) {
        webAppPkgTreeNode.scopes = new Set([scp]);
    }

    // Create a copy of webAppPkgTreeNode without children
    // to prevent duplicate key error in React
    const webAppPkgTreeNodeWithNoChildren = new WebAppPackage(webAppPkgTreeNode);
    webAppPkgTreeNodeWithNoChildren.children = [];
    webAppOrtResult.addPackageToPackagesTreeFlatArray(webAppPkgTreeNodeWithNoChildren, true);

    if (errors && errors.length !== 0) {
        for (let i = 0, len = errors.length; i < len; i++) {
            const error = errors[i];
            const webAppOrtIssueAnalyzer = new WebAppOrtIssueAnalyzer(error);

            webAppOrtIssueAnalyzer.pkg = id;

            webAppOrtResult.addError(webAppOrtIssueAnalyzer);
        }
    }

    webAppOrtResult.addPackageToTreeNodesArray(
        {
            id: webAppPkgTreeNode.id,
            key: webAppPkgTreeNode.key
        },
        true
    );

    return webAppPkgTreeNode;
}

function* processOrtResultData() {
    const ortResultData = yield select(getOrtResult);
    yield delay(200);

    const webAppOrtResult = new WebAppOrtResult(ortResultData);

    yield delay(100);

    const analyzerResultProjects = webAppOrtResult.getProjects();
    // Traverse over projects
    for (let i = analyzerResultProjects.length - 1; i >= 0; i -= 1) {
        const project = analyzerResultProjects[i];
        const projectIndex = i;
        let projectDefinitionFilePath = project.definitionFilePath;

        // Add `./` so we never have empty string
        projectDefinitionFilePath = `./${projectDefinitionFilePath}`;
        project.definitionFilePath = projectDefinitionFilePath;

        // Create dependency tree in a format that can be rendered by Ant Design's Tree
        webAppOrtResult.packagesTreeArray.unshift(
            recursiveAnalyzerResultProcessor(webAppOrtResult, projectIndex, project, i)
        );

        yield delay(150);
        yield put({
            type: 'APP::LOADING_PROCESS_ORT_RESULT_PROJECT_DATA',
            index: analyzerResultProjects.length - i,
            total: analyzerResultProjects.length
        });
    }

    const { packagesTreeNodesArray } = webAppOrtResult;

    // Sort the tree nodes so parent comes before children in array
    // so tree search will return results in the right order
    packagesTreeNodesArray.sort((a, b) => {
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

    webAppOrtResult.packagesTreeNodesArray = packagesTreeNodesArray;

    yield put({ type: 'APP::LOADING_PROCESS_ORT_RESULT_DATA_DONE', payload: webAppOrtResult });
    yield delay(50);
    yield put({ type: 'APP::LOADING_DONE' });
    yield delay(50);
    yield put({ type: 'APP::SHOW_TABS' });

    // Make webAppOrtResult inspectable via Browser's console
    window.ORT = webAppOrtResult;

    return webAppOrtResult;
}

export default processOrtResultData;
