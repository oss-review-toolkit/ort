/*
 * Copyright (C) 2019 HERE Europe B.V.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
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
import VcsInfoCuration from './VcsInfoCuration';

class PackageCurationData {
    #declaredLicenses = [];

    #concludedLicense = '';

    #description = '';

    #homepageUrl = '';

    #binaryArtifact = new RemoteArtifact();

    #sourceArtifact = new RemoteArtifact();

    #vcs = new VcsInfoCuration();

    #comment = '';

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.declared_licenses) {
                this.declaredLicenses = obj.declared_licenses;
            }

            if (obj.declaredLicenses) {
                this.declaredLicenses = obj.declaredLicenses;
            }

            if (obj.concluded_license) {
                this.concludedLicense = obj.concluded_license;
            }

            if (obj.concludedLicense) {
                this.concludedLicense = obj.concludedLicense;
            }

            if (obj.description) {
                this.description = obj.description;
            }

            if (obj.homepage_url) {
                this.homepageUrl = obj.homepage_url;
            }

            if (obj.homepageUrl) {
                this.homepageUrl = obj.homepageUrl;
            }

            if (obj.binary_artifact) {
                this.binaryArtifact = obj.binary_artifact;
            }

            if (obj.binaryArtifact) {
                this.binaryArtifact = obj.binaryArtifact;
            }

            if (obj.source_artifact) {
                this.sourceArtifact = obj.source_artifact;
            }

            if (obj.sourceArtifact) {
                this.sourceArtifact = obj.sourceArtifact;
            }

            if (obj.vcs) {
                this.vcs = obj.vcs;
            }

            if (obj.comment) {
                this.comment = obj.comment;
            }
        }
    }

    get declaredLicenses() {
        return this.#declaredLicenses;
    }

    set declaredLicenses(val) {
        this.#declaredLicenses = val;
    }

    get concludedLicense() {
        return this.#concludedLicense;
    }

    set concludedLicense(val) {
        this.#concludedLicense = val;
    }

    get description() {
        return this.#description;
    }

    set description(val) {
        this.#description = val;
    }

    get homepageUrl() {
        return this.#homepageUrl;
    }

    set homepageUrl(val) {
        this.#homepageUrl = val;
    }

    get binaryArtifact() {
        return this.#binaryArtifact;
    }

    set binaryArtifact(val) {
        this.#binaryArtifact = new RemoteArtifact(val);
    }

    get sourceArtifact() {
        return this.#sourceArtifact;
    }

    set sourceArtifact(val) {
        this.#sourceArtifact = new RemoteArtifact(val);
    }

    get vcs() {
        return this.#vcs;
    }

    set vcs(val) {
        this.#vcs = new VcsInfoCuration(val);
    }

    get comment() {
        return this.#comment;
    }

    set comment(val) {
        this.#comment = val;
    }
}

export default PackageCurationData;
