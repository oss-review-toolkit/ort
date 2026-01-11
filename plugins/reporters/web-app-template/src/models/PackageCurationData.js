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

import RemoteArtifact from './RemoteArtifact';
import VcsInfo from './VcsInfo';

class PackageCurationData {
    #authors;

    #binaryArtifact;

    #cpe;

    #comment;

    #concludedLicense;

    #declaredLicenseMapping = new Map();

    #description;

    #homepageUrl;

    #isMetadataOnly;

    #isModified;

    #labels = new Map();

    #purl;

    #sourceArtifact;

    #sourceCodeOrigins;

    #vcs;

    constructor(obj) {
        if (obj) {
            if (obj.authors) {
                this.#authors = obj.authors;
            }

            if (obj.binary_artifact || obj.binaryArtifact) {
                const binaryArtifact = obj.binary_artifact || obj.binaryArtifact;
                this.#binaryArtifact = new RemoteArtifact(binaryArtifact);
            }

            if (obj.cpe) {
                this.#cpe = obj.cpe;
            }

            if (obj.comment) {
                this.#comment = obj.comment;
            }

            if (obj.concluded_license || obj.concludedLicense) {
                this.#concludedLicense = obj.concluded_license || obj.concludedLicense;
            }

            if (obj.labels !== null && obj.labels instanceof Object) {
                Object.entries(obj.labels).forEach(
                    ([string, spdxExpression]) => this.#labels.set(string, spdxExpression)
                );
            }

            if (obj.description) {
                this.#description = obj.description;
            }

            if (obj.homepage_url || obj.homepageUrl) {
                this.#homepageUrl = obj.homepage_url || obj.homepageUrl;
            }

            if (obj.is_metadata_only || obj.isMetadataOnly) {
                this.#isMetadataOnly = obj.is_metadata_only || obj.isMetadataOnly;
            }

            if (obj.is_modified || obj.isModified) {
                this.#isModified = obj.is_modified || obj.isModified;
            }

            if (obj.labels !== null && obj.labels instanceof Object) {
                Object.entries(obj.labels).forEach(
                    ([key, value]) => this.#labels.set(key, value)
                );
            }

            if (obj.purl) {
                this.#purl = obj.purl;
            }

            if (obj.source_artifact || obj.sourceArtifact) {
                const sourceArtifact = obj.source_artifact || obj.sourceArtifact;
                this.#sourceArtifact = new RemoteArtifact(sourceArtifact);
            }

            if (obj.source_code_origins || obj.sourceCodeOrigins) {
                this.#sourceCodeOrigins = obj.source_code_origins || obj.sourceCodeOrigins;
            }

            if (obj.vcs) {
                this.#vcs = new VcsInfo(obj.vcs);
            }
        }
    }

    get authors() {
        return this.#authors;
    }

    get binaryArtifact() {
        return this.#binaryArtifact;
    }

    get cpe() {
        return this.#cpe;
    }

    get comment() {
        return this.#comment;
    }

    get concludedLicense() {
        return this.#concludedLicense;
    }

    get declaredLicenseMapping() {
        return this.#declaredLicenseMapping;
    }

    get description() {
        return this.#description;
    }

    get homepageUrl() {
        return this.#homepageUrl;
    }

    get isMetadataOnly() {
        return this.#isMetadataOnly;
    }

    get isModified() {
        return this.#isModified;
    }

    get labels() {
        return this.#labels;
    }

    get purl() {
        return this.#purl;
    }

    get sourceArtifact() {
        return this.#sourceArtifact;
    }

    get sourceCodeOrigins() {
        return this.#sourceCodeOrigins;
    }

    get vcs() {
        return this.#vcs;
    }
}

export default PackageCurationData;
