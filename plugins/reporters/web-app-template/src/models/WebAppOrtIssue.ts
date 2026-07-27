/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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
import type WebAppResolution from "@/models/WebAppResolution";
import type WebAppScanResult from "@/models/WebAppScanResult";
import type { EvaluatedModelIssue } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

class WebAppOrtIssue {
    #_id: number | undefined;

    #isExcluded: boolean = false;

    #howToFix: string | undefined;

    #message: string | undefined;

    #package: WebAppPackage | undefined;

    #packageIndex: number | undefined;

    #path: string | undefined;

    #scanResultIndex: number | undefined;

    #severity: string | undefined;

    #source: string | undefined;

    #timestamp: string | undefined;

    #type: string | undefined;

    #resolutionIndexes: Set<number> = new Set();

    #resolutionReasons: Set<string> | undefined;

    #resolutions: WebAppResolution[] | undefined;

    #webAppEvaluatedModel: WebAppEvaluatedModel | undefined;

    key: string | undefined;

    constructor(obj?: EvaluatedModelIssue, webAppEvaluatedModel?: WebAppEvaluatedModel) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            this.#isExcluded = obj.is_excluded === true || obj.isExcluded === true;

            if (obj.how_to_fix || obj.howToFix) {
                this.#howToFix = obj.how_to_fix || obj.howToFix;
            }

            if (obj.message) {
                this.#message = obj.message;
            }

            if (obj.path) {
                this.#path = obj.path;
            }

            if (Number.isInteger(obj.pkg)) {
                this.#packageIndex = obj.pkg;
            }

            if (Number.isInteger(obj.scan_result) || Number.isInteger(obj.scanResult)) {
                this.#scanResultIndex = obj.scan_result ?? obj.scanResult;
            }

            if (obj.severity) {
                this.#severity = obj.severity;
            }

            if (obj.source) {
                this.#source = obj.source;
            }

            if (obj.timestamp) {
                this.#timestamp = obj.timestamp;
            }

            if (obj.type) {
                this.#type = obj.type;
            }

            if (obj.resolutions) {
                this.#resolutionIndexes = new Set(obj.resolutions);
            }

            if (webAppEvaluatedModel) {
                this.#webAppEvaluatedModel = webAppEvaluatedModel;

                if (this.#packageIndex !== undefined) {
                    const webAppPackage = webAppEvaluatedModel.getPackageByIndex(this.#packageIndex);
                    if (webAppPackage) {
                        this.#package = webAppPackage;
                    }
                }
            }

            this.key = randomStringGenerator(20);
        }
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get howToFix(): string | undefined {
        return this.#howToFix;
    }

    get isExcluded(): boolean {
        return this.#isExcluded;
    }

    get isResolved(): boolean {
        return this.#resolutionIndexes.size > 0;
    }

    get message(): string | undefined {
        return this.#message;
    }

    get package(): WebAppPackage | undefined {
        return this.#package;
    }

    get packageIndex(): number | undefined {
        return this.#packageIndex;
    }

    get packageName(): string {
        return this.#package ? (this.#package.id ?? "") : "";
    }

    get path(): string | undefined {
        return this.#path;
    }

    // Resolve lazily from the model (matching WebAppFinding.scanResult) using the scan-result index, not
    // the package index: the model populates scan results asynchronously, so resolving eagerly in the
    // constructor always yielded null.
    get scanResult(): WebAppScanResult | null {
        if (this.#webAppEvaluatedModel && this.#scanResultIndex !== undefined) {
            return this.#webAppEvaluatedModel.getScanResultByIndex(this.#scanResultIndex);
        }
        return null;
    }

    get scanResultIndex(): number | undefined {
        return this.#scanResultIndex;
    }

    get severity(): string | undefined {
        return this.#severity;
    }

    get severityIndex(): number {
        if (this.isResolved) {
            return 3;
        }

        if (this.#severity === "ERROR") {
            return 0;
        }

        if (this.#severity === "WARNING") {
            return 1;
        }

        if (this.#severity === "HINT") {
            return 2;
        }

        return -1;
    }

    get source(): string | undefined {
        return this.#source;
    }

    get timestamp(): string | undefined {
        return this.#timestamp;
    }

    get type(): string | undefined {
        return this.#type;
    }

    get resolutionIndexes(): ReadonlySet<number> {
        return this.#resolutionIndexes;
    }

    get resolutions(): WebAppResolution[] | undefined {
        if (!this.#resolutions && this.#webAppEvaluatedModel) {
            this.#resolutions = [];
            this.#resolutionIndexes.forEach((index) => {
                const webAppResolution = this.#webAppEvaluatedModel?.getIssueResolutionByIndex(index) || null;
                if (webAppResolution) {
                    this.#resolutions?.push(webAppResolution);
                }
            });
        }

        return this.#resolutions;
    }

    get resolutionReasons(): Set<string> | undefined {
        if (!this.#resolutionReasons && this.#webAppEvaluatedModel) {
            this.#resolutionReasons = new Set();
            this.#resolutionIndexes.forEach((index) => {
                const webAppResolution = this.#webAppEvaluatedModel?.getIssueResolutionByIndex(index) || null;
                if (webAppResolution?.reason) {
                    this.#resolutionReasons?.add(webAppResolution.reason);
                }
            });
        }

        return this.#resolutionReasons;
    }

    hasHowToFix(): boolean {
        return !!this.#howToFix;
    }

    hasPackage(): boolean {
        return !!this.#package;
    }
}

export default WebAppOrtIssue;
