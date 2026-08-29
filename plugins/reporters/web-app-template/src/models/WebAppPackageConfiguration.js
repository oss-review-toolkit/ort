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

class WebAppPackageConfiguration {
    #id;

    #licenseFindingCurations;

    #licenseFindingCurationIndexes = [];

    #pathExcludes;

    #pathExcludeIndexes = [];

    #sourceArtifactUrl;

    #sourceCodeOrigin;

    #vcs;

    #webAppOrtResult;

    constructor(obj, webAppOrtResult) {
        if (obj.id) {
            this.#id = obj.id;
        }

        if (obj.license_finding_curations || obj.licenseFindingCurations) {
            this.#licenseFindingCurationIndexes = obj.license_finding_curations || obj.licenseFindingCurations;
        }

        if (obj.path_excludes || obj.pathExcludes) {
            this.#pathExcludeIndexes = obj.path_excludes || obj.pathExcludes;
        }

        if (obj.source_artifact_url || obj.sourceArtifactUrl) {
            this.#sourceArtifactUrl = obj.source_artifact_url || obj.sourceArtifactUrl;
        }

        if (obj.source_code_origin || obj.sourceCodeOrigin) {
            const sourceCodeOrigin = obj.source_code_origin || obj.sourceCodeOrigin;

            if (sourceCodeOrigin === 'ARTIFACT') {
                this.#sourceCodeOrigin = 'ARTIFACT';
            }

            if (sourceCodeOrigin === 'VCS') {
                this.#sourceCodeOrigin = 'VCS';
            }
        }

        if (obj.vcs) {
            this.#vcs = new VcsMatcher(obj.vcs);
        }

        if (webAppOrtResult) {
            this.#webAppOrtResult = webAppOrtResult;
        }

        this.key = randomStringGenerator(20);
    }

    get id() {
        return this.#id;
    }

    get licenseFindingCurations() {
        if (!this.#licenseFindingCurations && this.#webAppOrtResult) {
            this.#licenseFindingCurations = [];
            this.#licenseFindingCurationIndexes.forEach((index) => {
                const WebAppLicenseFindingCuration = this.#webAppOrtResult.getLicenseFindingCurationByIndex(index)
                    || null;
                if (WebAppLicenseFindingCuration) {
                    this.#licenseFindingCurations.push(WebAppLicenseFindingCuration);
                }
            });
        }

        return this.#licenseFindingCurations;
    }

    get licenseFindingCurationIndexes() {
        return this.#licenseFindingCurationIndexes;
    }

    get pathExcludes() {
        if (!this.#pathExcludes && this.#webAppOrtResult) {
            this.#pathExcludes = [];
            this.#pathExcludeIndexes.forEach((index) => {
                const webAppPathExclude = this.#webAppOrtResult.getPathExcludeByIndex(index) || null;
                if (webAppPathExclude) {
                    this.#pathExcludes.push(webAppPathExclude);
                }
            });
        }

        return this.#pathExcludes;
    }

    get pathExcludeIndexes() {
        return this.#pathExcludeIndexes;
    }

    get sourceArtifactUrl() {
        return this.#sourceArtifactUrl;
    }

    get sourceCodeOrigin() {
        return this.#sourceCodeOrigin;
    }

    get vcs() {
        return this.#vcs;
    }
}

class VcsMatcher {
    #revision;

    #type

    #url;

    constructor(obj) {
        if (obj.revision) {
            this.#revision = obj.revision;
        }

        if (obj.type) {
            this.#type = obj.type;
        }

        if (obj.url) {
            this.#url = obj.url;
        }
    }

    get revision() {
        return this.#revision;
    }

    get type() {
        return this.#type;
    }

    get url() {
        return this.#url;
    }
}

export default WebAppPackageConfiguration;
