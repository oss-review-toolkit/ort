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

import Excludes from './Excludes';
import Includes from './Includes';
import RepositoryAnalyzerConfiguration from './RepositoryAnalyzerConfiguration';
import WebAppLicenseChoices from './WebAppLicenseChoices';
import WebAppResolution from './WebAppResolution';
import WebAppSnippetChoices from './WebAppSnippetChoices';

class RepositoryConfiguration {
    #analyzer = {};

    #curations = [];

    #excludes = {};

    #includes = {};

    #licenseChoices = {};

    #packageConfigurations = [];

    #resolutions = [];

    #snippetChoices = [];

    constructor(obj, webAppOrtResult) {
        if (obj instanceof Object) {
            if (obj.analyzer) {
                this.#analyzer = new RepositoryAnalyzerConfiguration(obj.analyzer);
            }

            if (obj.curations) {
                this.#curations = obj.curations;
            }

            if (obj.excludes) {
                this.#excludes = new Excludes(obj.excludes);
            }

            if (obj.includes) {
                this.#includes = new Includes(obj.includes);
            }

            if (obj.license_choices || obj.licenseChoices) {
                this.#licenseChoices = new WebAppLicenseChoices(obj.license_choices || obj.licenseChoices);
            }

            if (obj.package_configurations || obj.packageConfigurations) {
                this.#packageConfigurations = obj.package_configurations || obj.packageConfigurations;
            }

            if (obj.resolutions) {
                for (let i = 0, len = obj.resolutions.length; i < len; i++) {
                    this.#resolutions.push(new WebAppResolution(obj.resolutions[i]));
                }
            }

            if (obj.snippet_choices || obj.snippetChoices) {
                const snippetChoices = obj.snippet_choices || obj.snippetChoices;

                for (let i = 0, len = snippetChoices.length; i < len; i++) {
                    this.#snippetChoices.push(new WebAppSnippetChoices(snippetChoices[i]));
                }
            }
        }
    }

    get analyzer() {
        return this.#analyzer;
    }

    get curations() {
        return this.#curations;
    }

    get excludes() {
        return this.#excludes;
    }

    get includes() {
        return this.#includes;
    }

    get licenseChoices() {
        return this.#licenseChoices;
    }

    get packageConfigurations() {
        return this.#packageConfigurations;
    }

    get resolutions() {
        return this.#resolutions;
    }

    get snippetChoices() {
        return this.#snippetChoices;
    }
}

export default RepositoryConfiguration;
