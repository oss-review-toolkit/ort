/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

import WebAppPath from './WebAppPath';

class WebAppTreeNode {
    #anchestors;

    #children = [];

    #isExcluded;

    #package;

    #packageIndex;

    #parent;

    #path;

    #pathExcludes = new Set([]);

    #scope;

    #scopeIndex;

    #scopeExcludes = new Set([]);

    #title;

    #webAppOrtResult;

    #webAppPath;

    constructor(obj, webAppOrtResult, callback, parent) {
        const that = this;
        const className = new Set();

        if (obj) {
            if (Number.isInteger(obj.key)) {
                this.key = `${obj.key}`;
                className.add(`ort-tree-node-${obj.key}`);
            }

            if (obj.path_excludes || obj.pathExcludes) {
                const pathExcludes = obj.path_excludes || obj.pathExcludes;
                this.#pathExcludes = new Set(pathExcludes);
            }

            if (obj.scope_excludes || obj.scopeExcludes) {
                const scopeExcludes = obj.scope_excludes || obj.scopeExcludes;
                this.#scopeExcludes = new Set(scopeExcludes);
            }

            if (parent) {
                this.#parent = parent;
            }

            if ((this.#pathExcludes && this.#pathExcludes.size > 0)
                || (this.#scopeExcludes && this.#scopeExcludes.size > 0)) {
                this.#isExcluded = true;
            } else if (this.#parent) {
                this.#isExcluded = this.#parent.isExcluded;
            }

            if (this.#isExcluded) {
                className.add('ort-excluded');
            }

            if (className.size > 0) {
                this.className = Array.from(className).join(' ');
            }

            if (webAppOrtResult) {
                this.#webAppOrtResult = webAppOrtResult;

                if (Number.isInteger(obj.pkg)) {
                    this.#packageIndex = obj.pkg;
                    this.#package = webAppOrtResult.packages[obj.pkg];
                    this.title = this.#package.id;
                } else if (Number.isInteger(obj.scope)) {
                    this.#scopeIndex = obj.pkg;
                    this.#scope = webAppOrtResult.scopes[obj.scope];
                    this.title = this.#scope.name;
                }
            }

            if (obj.children) {
                const { children } = obj;
                for (let i = 0, len = children.length; i < len; i++) {
                    setTimeout(() => {
                        this.#children.push(
                            new WebAppTreeNode(
                                children[i],
                                webAppOrtResult,
                                callback,
                                that
                            )
                        );
                    }, 0);
                }
            }

            if (Number.isInteger(this.#packageIndex)) {
                callback(that);
            }
        }
    }

    get children() {
        return this.#children;
    }

    get isExcluded() {
        return this.#isExcluded;
    }

    get isProject() {
        if (!Number.isInteger(this.#packageIndex)) {
            return false;
        }

        if (!this.#parent) {
            return true;
        }

        return false;
    }

    get isScope() {
        return !!this.#scope;
    }

    get packageIndex() {
        return this.#packageIndex;
    }

    get package() {
        return this.#package;
    }

    get packageName() {
        return this.package ? this.package.id : '';
    }

    get parent() {
        return this.#parent;
    }

    get pathExcludes() {
        return this.#pathExcludes;
    }

    get scope() {
        return this.#scope;
    }

    get scopeExcludes() {
        return this.#scopeExcludes;
    }

    get scopeIndex() {
        return this.#scopeIndex;
    }

    get webAppPath() {
        if (!this.#webAppPath && this.#webAppOrtResult) {
            let project;
            let parent = this.#parent;
            const path = [];
            let scope;

            while (parent) {
                const treeNode = parent;

                if (treeNode.isProject) {
                    project = treeNode.packageIndex;
                }

                if (treeNode.isScope) {
                    const webAppScope = this.#webAppOrtResult.getScopeByName(treeNode.title);

                    if (webAppScope) {
                        scope = webAppScope.id;
                    }
                }

                if (!treeNode.isProject && !treeNode.isScope) {
                    path.unshift(treeNode.packageIndex);
                }

                parent = treeNode.parent;
            }

            if (Number.isInteger(project) && Number.isInteger(scope)) {
                this.#webAppPath = new WebAppPath(
                    {
                        _id: Number(this.key),
                        path,
                        pkg: this.packageIndex,
                        project,
                        scope
                    },
                    this.#webAppOrtResult
                );
            }
        }

        return this.#webAppPath;
    }

    hasWebAppPath() {
        return !!this.webAppPath;
    }
}

export default WebAppTreeNode;
