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
import type WebAppLicense from "@/models/WebAppLicense";
import type WebAppPackage from "@/models/WebAppPackage";
import type WebAppResolution from "@/models/WebAppResolution";
import type { EvaluatedModelRuleViolation } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

class WebAppRuleViolation {
    #_id: number | undefined;

    #howToFix: string | undefined;

    #isResolved: boolean = false;

    #license: WebAppLicense | undefined;

    #licenseIndex: number | undefined;

    #licenseSource: string | undefined;

    #message: string | undefined;

    #package: WebAppPackage | undefined;

    #packageIndex: number = -1;

    #severity: string | undefined;

    #severityIndex: number = 4;

    #resolutionIndexes: Set<number> = new Set();

    #resolutionReasons: Set<string> | undefined;

    #resolutions: WebAppResolution[] | undefined;

    #rule: string | undefined;

    #webAppEvaluatedModel: WebAppEvaluatedModel | undefined;

    key: string | undefined;

    constructor(obj?: EvaluatedModelRuleViolation, webAppEvaluatedModel?: WebAppEvaluatedModel) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.how_to_fix || obj.howToFix) {
                this.#howToFix = obj.how_to_fix || obj.howToFix;
            }

            if (obj.license !== undefined && obj.license !== null) {
                this.#licenseIndex = obj.license;
            }

            if (obj.license_source || obj.licenseSource) {
                this.#licenseSource = obj.license_source || obj.licenseSource;
            }

            if (obj.message) {
                this.#message = obj.message;
            }

            if (Number.isInteger(obj.pkg)) {
                this.#packageIndex = obj.pkg ?? -1;
            } else {
                this.#packageIndex = -1;
            }

            if (obj.severity) {
                this.#severity = obj.severity;
            }

            switch (this.#severity) {
                case "ERROR":
                    this.#severityIndex = 0;
                    break;
                case "WARNING":
                    this.#severityIndex = 1;
                    break;
                case "HINT":
                    this.#severityIndex = 3;
                    break;
                default:
                    this.#severityIndex = 4;
            }

            if (obj.resolutions) {
                this.#resolutionIndexes = new Set(obj.resolutions);
            }

            if (obj.rule) {
                this.#rule = obj.rule;
            }

            if (webAppEvaluatedModel) {
                this.#webAppEvaluatedModel = webAppEvaluatedModel;

                const webAppPackage = webAppEvaluatedModel.getPackageByIndex(this.#packageIndex);
                if (webAppPackage) {
                    this.#package = webAppPackage;
                }

                if (this.#licenseIndex !== undefined) {
                    const webAppLicense = webAppEvaluatedModel.getLicenseByIndex(this.#licenseIndex);
                    if (webAppLicense) {
                        this.#license = webAppLicense;
                    }
                }
            }

            this.#isResolved = this.#resolutionIndexes.size > 0;

            if (this.#isResolved) {
                this.#severityIndex = this.#severityIndex + 10;
            }

            this.key = randomStringGenerator(20);
        }
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get isResolved(): boolean {
        return this.#isResolved;
    }

    get howToFix(): string | undefined {
        return this.#howToFix;
    }

    get license(): WebAppLicense | undefined {
        return this.#license;
    }

    get licenseName(): string | undefined {
        return this.#license?.id;
    }

    get licenseSource(): string | undefined {
        return this.#licenseSource;
    }

    get message(): string | undefined {
        return this.#message;
    }

    get package(): WebAppPackage | undefined {
        return this.#package;
    }

    get packageIndex(): number {
        return this.#packageIndex;
    }

    get packageName(): string {
        return this.#package ? (this.#package.id ?? "") : "";
    }

    get resolutionIndexes(): ReadonlySet<number> {
        return this.#resolutionIndexes;
    }

    get resolutions(): WebAppResolution[] | undefined {
        if (!this.#resolutions && this.#webAppEvaluatedModel) {
            this.#resolutions = [];
            this.#resolutionIndexes.forEach((index) => {
                const webAppResolution = this.#webAppEvaluatedModel?.getRuleViolationResolutionByIndex(index) || null;
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
                const webAppResolution = this.#webAppEvaluatedModel?.getRuleViolationResolutionByIndex(index) || null;
                if (webAppResolution?.reason) {
                    this.#resolutionReasons?.add(webAppResolution.reason);
                }
            });
        }

        return this.#resolutionReasons;
    }

    get rule(): string | undefined {
        return this.#rule;
    }

    get severity(): string | undefined {
        return this.#severity;
    }

    get severityIndex(): number {
        return this.#severityIndex;
    }

    hasHowToFix(): boolean {
        return !!this.#howToFix;
    }

    hasPackage(): boolean {
        return !!this.#package;
    }
}

export default WebAppRuleViolation;
