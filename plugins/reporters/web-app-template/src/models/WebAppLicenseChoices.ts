/*
 * Copyright (C) 2026 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import PackageLicenseChoice from "@/models/PackageLicenseChoice";
import SpdxLicenseChoice from "@/models/SpdxLicenseChoice";
import type { EvaluatedModelWebAppLicenseChoices } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

class WebAppLicenseChoices {
    #_id: number | undefined;

    #packageLicenseChoices: PackageLicenseChoice[] = [];

    #repositoryLicenseChoices: SpdxLicenseChoice[] = [];

    key: string;

    constructor(obj?: EvaluatedModelWebAppLicenseChoices) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            const packageLicenseChoices = obj.package_license_choices || obj.packageLicenseChoices;
            if (packageLicenseChoices) {
                for (let i = 0, len = packageLicenseChoices.length; i < len; i++) {
                    const raw = packageLicenseChoices[i];
                    if (raw) {
                        this.#packageLicenseChoices.push(new PackageLicenseChoice(raw));
                    }
                }
            }

            const repositoryLicenseChoices = obj.repository_license_choices || obj.repositoryLicenseChoices;
            if (repositoryLicenseChoices) {
                for (let i = 0, len = repositoryLicenseChoices.length; i < len; i++) {
                    const raw = repositoryLicenseChoices[i];
                    if (raw) {
                        this.#repositoryLicenseChoices.push(new SpdxLicenseChoice(raw));
                    }
                }
            }
        }

        this.key = randomStringGenerator(20);
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get packageLicenseChoices(): readonly PackageLicenseChoice[] {
        return this.#packageLicenseChoices;
    }

    get repositoryLicenseChoices(): readonly SpdxLicenseChoice[] {
        return this.#repositoryLicenseChoices;
    }
}

export default WebAppLicenseChoices;
