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
import type WebAppPathExclude from "@/models/WebAppPathExclude";
import type WebAppScanResult from "@/models/WebAppScanResult";
import type { EvaluatedModelFinding } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

class WebAppFinding {
    #copyright: number | undefined;

    #endLine: number | undefined;

    #isExcluded: boolean = false;

    #license: number | undefined;

    #path: string | undefined;

    #pathExcludes: WebAppPathExclude[] | undefined;

    #pathExcludeIndexes: Set<number> = new Set();

    #pathExcludeReasons: Set<string> | undefined;

    #startLine: number | undefined;

    #scanResult: number | undefined;

    #type: string | undefined;

    #webAppEvaluatedModel: WebAppEvaluatedModel | undefined;

    key: string | undefined;

    constructor(obj?: EvaluatedModelFinding, webAppEvaluatedModel?: WebAppEvaluatedModel) {
        if (obj) {
            if (Number.isInteger(obj.copyright)) {
                this.#copyright = obj.copyright;
            }

            if (Number.isInteger(obj.end_line) || Number.isInteger(obj.endLine)) {
                this.#endLine = obj.end_line ?? obj.endLine;
            }

            if (Number.isInteger(obj.license)) {
                this.#license = obj.license;
            }

            if (obj.path !== null && obj.path !== undefined) {
                this.#path = obj.path;
            }

            const pathExcludes = obj.path_excludes || obj.pathExcludes;
            if (Array.isArray(pathExcludes) && pathExcludes.length > 0) {
                this.#pathExcludeIndexes = new Set(pathExcludes);
                this.#isExcluded = true;
            }

            if (Number.isInteger(obj.start_line) || Number.isInteger(obj.startLine)) {
                this.#startLine = obj.start_line ?? obj.startLine;
            }

            if (Number.isInteger(obj.scan_result) || Number.isInteger(obj.scanResult)) {
                this.#scanResult = obj.scan_result ?? obj.scanResult;
            }

            if (obj.type !== null && obj.type !== undefined) {
                this.#type = obj.type;
            }

            if (webAppEvaluatedModel) {
                this.#webAppEvaluatedModel = webAppEvaluatedModel;
            }

            this.key = randomStringGenerator(20);
        }
    }

    get copyright(): string | null {
        if (this.#webAppEvaluatedModel && this.#copyright !== undefined) {
            const webAppFinding = this.#webAppEvaluatedModel.getCopyrightByIndex(this.#copyright);
            if (webAppFinding) {
                return webAppFinding.statement ?? null;
            }
        }

        return null;
    }

    get endLine(): number | undefined {
        return this.#endLine;
    }

    get isExcluded(): boolean {
        return this.#isExcluded;
    }

    get license(): string | null {
        if (this.#webAppEvaluatedModel && this.#license !== undefined) {
            const webAppFinding = this.#webAppEvaluatedModel.getLicenseByIndex(this.#license);
            if (webAppFinding) {
                return webAppFinding.id ?? null;
            }
        }

        return null;
    }

    get path(): string | undefined {
        return this.#path;
    }

    get pathExcludes(): WebAppPathExclude[] | undefined {
        if (!this.#pathExcludes && this.#webAppEvaluatedModel) {
            this.#pathExcludes = [];
            this.#pathExcludeIndexes.forEach((index) => {
                const webAppPathExclude = this.#webAppEvaluatedModel?.getPathExcludeByIndex(index) || null;
                if (webAppPathExclude) {
                    this.#pathExcludes?.push(webAppPathExclude);
                }
            });
        }

        return this.#pathExcludes;
    }

    get pathExcludeIndexes(): ReadonlySet<number> {
        return this.#pathExcludeIndexes;
    }

    get pathExcludeReasons(): Set<string> | undefined {
        if (!this.#pathExcludeReasons && this.#webAppEvaluatedModel) {
            this.#pathExcludeReasons = new Set();

            this.#pathExcludeIndexes.forEach((index) => {
                const webAppPathExclude = this.#webAppEvaluatedModel?.getPathExcludeByIndex(index) || null;
                if (webAppPathExclude?.reason) {
                    this.#pathExcludeReasons?.add(webAppPathExclude.reason);
                }
            });
        }

        return this.#pathExcludeReasons;
    }

    get startLine(): number | undefined {
        return this.#startLine;
    }

    get scanResult(): WebAppScanResult | null {
        if (this.#webAppEvaluatedModel && this.#scanResult !== undefined) {
            return this.#webAppEvaluatedModel.getScanResultByIndex(this.#scanResult);
        }

        return null;
    }

    get type(): string | undefined {
        return this.#type;
    }

    get value(): string | null {
        if (this.#type === "COPYRIGHT") {
            return this.copyright;
        }

        return this.license;
    }
}

export default WebAppFinding;
