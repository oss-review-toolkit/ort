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

import PackageManagerConfiguration from "@/models/PackageManagerConfiguration";
import type { EvaluatedModelRepositoryAnalyzerConfiguration } from "@/types/evaluatedModelData";

class RepositoryAnalyzerConfiguration {
    #allowDynamicVersions: boolean = false;

    #disabledPackageManagers: readonly string[] = [];

    #enabledPackageManagers: readonly string[] = [];

    #packageManagers = new Map<string, PackageManagerConfiguration>();

    #skipExcluded: boolean = false;

    constructor(obj?: EvaluatedModelRepositoryAnalyzerConfiguration) {
        if (obj) {
            if (obj.allow_dynamic_versions || obj.allowDynamicVersions) {
                this.#allowDynamicVersions = (obj.allow_dynamic_versions || obj.allowDynamicVersions) ?? false;
            }

            if (obj.disabled_package_managers || obj.disabledPackageManagers) {
                this.#disabledPackageManagers = obj.disabled_package_managers || obj.disabledPackageManagers || [];
            }

            if (obj.enabled_package_managers || obj.enabledPackageManagers) {
                this.#enabledPackageManagers = obj.enabled_package_managers || obj.enabledPackageManagers || [];
            }

            if (obj.packageManagers || obj.package_managers) {
                const packageManagers = obj.package_managers || obj.packageManagers;

                if (packageManagers) {
                    Object.entries(packageManagers).forEach(([key, packageManagerConfiguration]) => {
                        this.#packageManagers.set(key, new PackageManagerConfiguration(packageManagerConfiguration));
                    });
                }
            }

            if (obj.skip_excluded || obj.skipExcluded) {
                this.#skipExcluded = (obj.skip_excluded || obj.skipExcluded) ?? false;
            }
        }
    }

    get allowDynamicVersions(): boolean {
        return this.#allowDynamicVersions;
    }

    get disabledPackageManagers(): readonly string[] {
        return this.#disabledPackageManagers;
    }

    get enabledPackageManagers(): readonly string[] {
        return this.#enabledPackageManagers;
    }

    get packageManagers(): ReadonlyMap<string, PackageManagerConfiguration> {
        return this.#packageManagers;
    }

    get skipExcluded(): boolean {
        return this.#skipExcluded;
    }
}

export default RepositoryAnalyzerConfiguration;
