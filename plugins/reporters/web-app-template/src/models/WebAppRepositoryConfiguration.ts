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

import Excludes from "@/models/Excludes";
import WebAppIncludes from "@/models/Includes";
import RepositoryAnalyzerConfiguration from "@/models/RepositoryAnalyzerConfiguration";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppLicenseChoices from "@/models/WebAppLicenseChoices";
import WebAppPackageConfiguration from "@/models/WebAppPackageConfiguration";
import WebAppPackageCuration from "@/models/WebAppPackageCuration";
import WebAppResolution from "@/models/WebAppResolution";
import WebAppSnippetChoices from "@/models/WebAppSnippetChoices";
import type { EvaluatedModelRepositoryConfiguration } from "@/types/evaluatedModelData";

class WebAppRepositoryConfiguration {
    #analyzer: RepositoryAnalyzerConfiguration = new RepositoryAnalyzerConfiguration();

    #curations: WebAppPackageCuration[] = [];

    #excludes: Excludes = new Excludes();

    #includes: WebAppIncludes = new WebAppIncludes();

    #licenseChoices: WebAppLicenseChoices = new WebAppLicenseChoices();

    #packageConfigurations: WebAppPackageConfiguration[] = [];

    #resolutions: WebAppResolution[] = [];

    #snippetChoices: WebAppSnippetChoices[] = [];

    constructor(obj?: EvaluatedModelRepositoryConfiguration, _webAppEvaluatedModel?: WebAppEvaluatedModel) {
        if (obj) {
            if (obj.analyzer) {
                this.#analyzer = new RepositoryAnalyzerConfiguration(obj.analyzer);
            }

            if (obj.curations) {
                for (let i = 0, len = obj.curations.length; i < len; i++) {
                    const raw = obj.curations[i];
                    if (raw) {
                        this.#curations.push(new WebAppPackageCuration(raw));
                    }
                }
            }

            if (obj.excludes) {
                this.#excludes = new Excludes(obj.excludes);
            }

            if (obj.includes) {
                this.#includes = new WebAppIncludes(obj.includes);
            }

            if (obj.license_choices || obj.licenseChoices) {
                this.#licenseChoices = new WebAppLicenseChoices(obj.license_choices || obj.licenseChoices);
            }

            if (obj.package_configurations || obj.packageConfigurations) {
                const packageConfigurations = obj.package_configurations || obj.packageConfigurations || [];
                for (let i = 0, len = packageConfigurations.length; i < len; i++) {
                    const raw = packageConfigurations[i];
                    if (raw) {
                        this.#packageConfigurations.push(new WebAppPackageConfiguration(raw));
                    }
                }
            }

            if (obj.resolutions) {
                for (let i = 0, len = obj.resolutions.length; i < len; i++) {
                    const raw = obj.resolutions[i];
                    if (raw) {
                        this.#resolutions.push(new WebAppResolution(raw));
                    }
                }
            }

            if (obj.snippet_choices || obj.snippetChoices) {
                const snippetChoices = obj.snippet_choices || obj.snippetChoices || [];

                for (let i = 0, len = snippetChoices.length; i < len; i++) {
                    const raw = snippetChoices[i];
                    if (raw) {
                        this.#snippetChoices.push(new WebAppSnippetChoices(raw));
                    }
                }
            }
        }
    }

    get analyzer(): RepositoryAnalyzerConfiguration {
        return this.#analyzer;
    }

    get curations(): readonly WebAppPackageCuration[] {
        return this.#curations;
    }

    get excludes(): Excludes {
        return this.#excludes;
    }

    get includes(): WebAppIncludes {
        return this.#includes;
    }

    get licenseChoices(): WebAppLicenseChoices {
        return this.#licenseChoices;
    }

    get packageConfigurations(): readonly WebAppPackageConfiguration[] {
        return this.#packageConfigurations;
    }

    get resolutions(): readonly WebAppResolution[] {
        return this.#resolutions;
    }

    get snippetChoices(): readonly WebAppSnippetChoices[] {
        return this.#snippetChoices;
    }
}

export default WebAppRepositoryConfiguration;
