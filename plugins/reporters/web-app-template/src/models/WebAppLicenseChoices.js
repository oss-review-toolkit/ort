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

import { randomStringGenerator } from '../utils';

import PackageLicenseChoice from './PackageLicenseChoice';
import SpdxLicenseChoice from './SpdxLicenseChoice';

class WebAppLicenseChoices {
    #_id;

    #packageLicenseChoices = [];

    #repositoryLicenseChoices = [];

    constructor(obj) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.package_license_choices || obj.packageLicenseChoices) {
                const packageLicenseChoices = obj.package_license_choices || obj.packageLicenseChoices;

                for (let i = 0, len = packageLicenseChoices.length; i < len; i++) {
                    this.#packageLicenseChoices.push(new PackageLicenseChoice(packageLicenseChoices[i]));
                }
            }

            if (obj.repository_license_choices || obj.repositoryLicenseChoices) {
                const repositoryLicenseChoices = obj.repository_license_choices || obj.repositoryLicenseChoices;

                for (let i = 0, len = repositoryLicenseChoices.length; i < len; i++) {
                    this.#repositoryLicenseChoices.push(new SpdxLicenseChoice(repositoryLicenseChoices[i]));
                }
            }
        }

        this.key = randomStringGenerator(20);
    }

    get _id() {
        return this.#_id;
    }

    get packageLicenseChoices() {
        return this.#packageLicenseChoices;
    }

    get repositoryLicenseChoices() {
        return this.#repositoryLicenseChoices;
    }
}

export default WebAppLicenseChoices;
