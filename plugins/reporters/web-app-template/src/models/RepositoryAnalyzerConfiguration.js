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

import PackageManagerConfiguration from './PackageManagerConfiguration';

class RepositoryAnalyzerConfiguration {
    #allowDynamicVersions = false;

    #disabledPackageManagers = [];

    #enabledPackageManagers = [];

    #packageManagers = new Map();

    #skipExcluded = false;

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.allow_dynamic_versions || obj.allowDynamicVersions) {
                this.#allowDynamicVersions = obj.allow_dynamic_versions || obj.allowDynamicVersions;
            }

            if (obj.disabled_package_managers || obj.disabledPackageManagers) {
                this.#disabledPackageManagers = obj.disabled_package_managers || obj.disabledPackageManagers;
            }

            if (obj.enabled_package_managers || obj.enabledPackageManagers) {
                this.#enabledPackageManagers = obj.enabled_package_managers || obj.enabledPackageManagers;
            }

            if (obj.packageManagers || obj.package_managers) {
                const packageManagers = obj.package_managers || obj.packageManagers;

                if (packageManagers !== null && packageManagers instanceof Object) {
                    Object.entries(packageManagers).forEach(
                        ([string, packageManagerConfiguration]) => this.#packageManagers.set(
                            string, new PackageManagerConfiguration(packageManagerConfiguration
                            ))
                    );
                }
            }

            if (obj.skip_excluded || obj.skipExcluded) {
                this.#skipExcluded = obj.skip_excluded || obj.skipExcluded;
            }
        }
    }

    get allowDynamicVersions() {
        return this.#allowDynamicVersions;
    }

    get disabledPackageManagers() {
        return this.#disabledPackageManagers;
    }

    get enabledPackageManagers() {
        return this.#enabledPackageManagers;
    }

    get packageManagers() {
        return this.#packageManagers;
    }

    get skipExcluded() {
        return this.#skipExcluded;
    }
}

export default RepositoryAnalyzerConfiguration;
