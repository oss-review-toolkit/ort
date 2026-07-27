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

import SpdxLicenseChoice from "@/models/SpdxLicenseChoice";
import type { EvaluatedModelPackageLicenseChoice } from "@/types/evaluatedModelData";

class PackageLicenseChoice {
    #licenseChoices: SpdxLicenseChoice[] = [];

    #packageId: string | undefined;

    constructor(obj: EvaluatedModelPackageLicenseChoice) {
        const licenseChoices = obj.license_choices || obj.licenseChoices;
        if (licenseChoices) {
            for (let i = 0, len = licenseChoices.length; i < len; i++) {
                const choice = licenseChoices[i];
                if (choice) {
                    this.#licenseChoices.push(new SpdxLicenseChoice(choice));
                }
            }
        }

        if (obj.package_id || obj.packageId) {
            this.#packageId = obj.package_id || obj.packageId;
        }
    }

    get licenseChoices(): readonly SpdxLicenseChoice[] {
        return this.#licenseChoices;
    }

    get packageId(): string | undefined {
        return this.#packageId;
    }
}

export default PackageLicenseChoice;
