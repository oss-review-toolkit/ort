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

import Package from './Package';
import Scope from './Scope';
import RemoteArtifact from './RemoteArtifact';
import VcsInfo from './VcsInfo';

class Project {
    #id;

    #purl = '';

    #definitionFilePath = '';

    #declaredLicenses = [];

    #declaredLicensesProcessed = { spdxExpression: '' };

    #homepageUrl = '';

    #vcs = new VcsInfo();

    #vcsProcessed = new VcsInfo();

    #scopes = [];

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.id) {
                this.id = obj.id;
            }

            if (obj.purl) {
                this.purl = obj.purl;
            }

            if (obj.definition_file_path) {
                this.definitionFilePath = obj.definition_file_path;
            }

            if (obj.definitionFilePath) {
                this.definitionFilePath = obj.definitionFilePath;
            }

            if (obj.declared_licenses) {
                this.declaredLicenses = obj.declared_licenses;
            }

            if (obj.declaredLicenses) {
                this.declaredLicenses = obj.declaredLicenses;
            }

            if (obj.declared_licenses_processed) {
                this.declaredLicensesProcessed = obj.declared_licenses_processed;
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

            if (obj.vcs) {
                this.vcs = obj.vcs;
            }

            if (obj.vcs_processed) {
                this.vcsProcessed = obj.vcs_processed;
            }

            if (obj.vcsProcessed) {
                this.vcsProcessed = obj.vcsProcessed;
            }

            if (obj.scopes) {
                this.scopes = obj.scopes;
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

    get definitionFilePath() {
        return this.#definitionFilePath;
    }

    set definitionFilePath(val) {
        this.#definitionFilePath = val;
    }

    get declaredLicenses() {
        return this.#declaredLicenses;
    }

    set declaredLicenses(val) {
        this.#declaredLicenses = val;
    }

    get declaredLicensesProcessed() {
        return this.#declaredLicensesProcessed;
    }

    set declaredLicensesProcessed(val) {
        if (val.spdx_expression) {
            this.#declaredLicensesProcessed = {
                spdxExpression: val
            };
        }
    }

    get homepageUrl() {
        return this.#homepageUrl;
    }

    set homepageUrl(val) {
        this.#homepageUrl = val;
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

    get scopes() {
        return this.#scopes;
    }

    set scopes(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#scopes.push(new Scope(val[i]));
        }
    }

    toPackage() {
        return new Package({
            id: this.#id,
            declaredLicenses: this.#declaredLicenses,
            homepageUrl: this.#homepageUrl,
            binaryArtifact: new RemoteArtifact(),
            sourceArtifact: new RemoteArtifact(),
            vcs: this.#vcs,
            vcsProcessed: this.#vcsProcessed
        });
    }
}

export default Project;
