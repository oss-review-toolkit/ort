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
import VcsInfo from './VcsInfo';

class Package {
    #id;

    #purl = '';

    #declaredLicenses = [];

    #declaredLicensesProcessed = { spdxExpression: '' };

    #concludedLicense = '';

    #description = '';

    #homepageUrl = '';

    #binaryArtifact = new RemoteArtifact();

    #sourceArtifact = new RemoteArtifact();

    #vcs = new VcsInfo();

    #vcsProcessed = new VcsInfo();

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.id) {
                this.id = obj.id;
            }

            if (obj.purl) {
                this.purl = obj.purl;
            }

            if (obj.declared_licenses) {
                this.declaredLicenses = obj.declared_licenses;
            }

            if (obj.declaredLicenses) {
                this.declaredLicenses = obj.declaredLicenses;
            }

            if (obj.declared_licenses_processed) {
                if (obj.declared_licenses_processed.spdx_expression) {
                    this.declaredLicensesProcessed = {
                        spdxExpression: obj.declared_licenses_processed.spdx_expression
                    };
                }
            }

            if (obj.declaredLicensesProcessed) {
                this.declaredLicensesProcessed = obj.declaredLicensesProcessed;
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

            if (obj.vcs_processed) {
                this.vcsProcessed = obj.vcs_processed;
            }

            if (obj.vcsProcessed) {
                this.vcsProcessed = obj.vcsProcessed;
            }
        }
    }

    get id() {
        return this.#id;
    }

    set id(val) {
        this.#id = val;
    }

    get purl() {
        return this.#purl;
    }

    set purl(val) {
        this.#purl = val;
    }

    get declaredLicenses() {
        return this.#declaredLicenses;
    }

    set declaredLicenses(licenses) {
        this.#declaredLicenses = licenses;
    }

    get declaredLicensesProcessed() {
        return this.#declaredLicensesProcessed;
    }

    set declaredLicensesProcessed(licenses) {
        if (licenses.spdxExpression) {
            this.#declaredLicensesProcessed = {
                spdxExpression: licenses.spdxExpression
            };
        }
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
        this.#vcs = new VcsInfo(val);
    }

    get vcsProcessed() {
        return this.#vcsProcessed || new VcsInfo();
    }

    set vcsProcessed(val) {
        this.#vcsProcessed = new VcsInfo(val);
    }
}

export default Package;
