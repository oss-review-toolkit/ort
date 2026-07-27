/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
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

import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import type WebAppPackage from "@/models/WebAppPackage";
import WebAppPath from "@/models/WebAppPath";
import type WebAppScope from "@/models/WebAppScope";
import type { EvaluatedModelPath, EvaluatedModelTreeNode } from "@/types/evaluatedModelData";

export type WebAppTreeNodeCallback = (node: WebAppTreeNode) => void;

class WebAppTreeNode {
    #children: WebAppTreeNode[] = [];

    #isExcluded: boolean | undefined;

    #package: WebAppPackage | undefined;

    #packageIndex: number | undefined;

    #parent: WebAppTreeNode | undefined;

    #pathExcludes: Set<number> = new Set();

    #scope: WebAppScope | undefined;

    #scopeIndex: number | undefined;

    #scopeExcludes: Set<number> = new Set();

    #title: string | undefined;

    #webAppEvaluatedModel: WebAppEvaluatedModel | undefined;

    #webAppPath: WebAppPath | undefined;

    key: string | undefined;

    className: string | undefined;

    constructor(
        obj?: EvaluatedModelTreeNode,
        webAppEvaluatedModel?: WebAppEvaluatedModel,
        callback?: WebAppTreeNodeCallback,
        parent?: WebAppTreeNode,
    ) {
        const className = new Set<string>();

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

            if (this.#pathExcludes.size > 0 || this.#scopeExcludes.size > 0) {
                this.#isExcluded = true;
            } else if (this.#parent) {
                this.#isExcluded = this.#parent.isExcluded;
            }

            if (this.#isExcluded) {
                className.add("ort-excluded");
            }

            if (className.size > 0) {
                this.className = Array.from(className).join(" ");
            }

            if (webAppEvaluatedModel) {
                this.#webAppEvaluatedModel = webAppEvaluatedModel;

                if (Number.isInteger(obj.pkg)) {
                    this.#packageIndex = obj.pkg;
                    if (obj.pkg !== undefined) {
                        this.#package = webAppEvaluatedModel.packages[obj.pkg];
                        this.#title = this.#package?.id;
                    }
                } else if (Number.isInteger(obj.scope)) {
                    this.#scopeIndex = obj.scope ?? obj.scope_index ?? obj.scopeIndex;
                    if (obj.scope !== undefined) {
                        this.#scope = webAppEvaluatedModel.scopes[obj.scope];
                        this.#title = this.#scope?.name;
                    }
                }
            }

            if (obj.children) {
                // Build children on deferred tasks (via the model's scheduler) so a deep or wide tree does not
                // block the main thread. The scheduler counts these tasks so the view can wait for the whole
                // tree to finish instead of rendering it half-built. Fall back to synchronous construction when
                // no model is available (the scheduler lives on it).
                const { children } = obj;
                for (let i = 0, len = children.length; i < len; i++) {
                    const build = (): void => {
                        const raw = children[i];
                        if (raw) {
                            this.#children.push(new WebAppTreeNode(raw, webAppEvaluatedModel, callback, this));
                        }
                    };

                    if (webAppEvaluatedModel) {
                        webAppEvaluatedModel.scheduleTreeBuild(build);
                    } else {
                        build();
                    }
                }
            }

            if (Number.isInteger(this.#packageIndex) && callback) {
                callback(this);
            }
        }
    }

    get children(): readonly WebAppTreeNode[] {
        return this.#children;
    }

    get isExcluded(): boolean | undefined {
        return this.#isExcluded;
    }

    get isProject(): boolean {
        if (!Number.isInteger(this.#packageIndex)) {
            return false;
        }

        if (!this.#parent) {
            return true;
        }

        return false;
    }

    get isScope(): boolean {
        return !!this.#scope;
    }

    get packageIndex(): number | undefined {
        return this.#packageIndex;
    }

    get package(): WebAppPackage | undefined {
        return this.#package;
    }

    get packageName(): string {
        return this.package ? (this.package.id ?? "") : "";
    }

    get parent(): WebAppTreeNode | undefined {
        return this.#parent;
    }

    get pathExcludes(): ReadonlySet<number> {
        return this.#pathExcludes;
    }

    get scope(): WebAppScope | undefined {
        return this.#scope;
    }

    get scopeExcludes(): ReadonlySet<number> {
        return this.#scopeExcludes;
    }

    get scopeIndex(): number | undefined {
        return this.#scopeIndex;
    }

    get title(): string | undefined {
        return this.#title;
    }

    get webAppPath(): WebAppPath | undefined {
        if (!this.#webAppPath && this.#webAppEvaluatedModel) {
            let project: number | undefined;
            let parent = this.#parent;
            const path: number[] = [];
            let scope: number | undefined;

            while (parent) {
                const treeNode = parent;

                if (treeNode.isProject) {
                    project = treeNode.packageIndex;
                }

                if (treeNode.isScope && treeNode.title) {
                    const webAppScope = this.#webAppEvaluatedModel.getScopeByName(treeNode.title);

                    if (webAppScope) {
                        scope = webAppScope.id;
                    }
                }

                if (!treeNode.isProject && !treeNode.isScope && treeNode.packageIndex !== undefined) {
                    path.unshift(treeNode.packageIndex);
                }

                parent = treeNode.parent;
            }

            if (Number.isInteger(project) && Number.isInteger(scope) && this.key) {
                const pathArg: EvaluatedModelPath = {
                    _id: Number(this.key),
                    path,
                    ...(this.packageIndex !== undefined ? { pkg: this.packageIndex } : {}),
                    ...(project !== undefined ? { project } : {}),
                    ...(scope !== undefined ? { scope } : {}),
                };
                this.#webAppPath = new WebAppPath(pathArg, this.#webAppEvaluatedModel);
            }
        }

        return this.#webAppPath;
    }

    hasWebAppPath(): boolean {
        return !!this.webAppPath;
    }
}

export default WebAppTreeNode;
