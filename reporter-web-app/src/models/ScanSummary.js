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

import LicenseFinding from './LicenseFinding';
import OrtIssue from './OrtIssue';

class ScanSummary {
    #startTime = '';

    #endTime = '';

    #errors = [];

    #fileCount = 0;

    #licenseFindings = [];

    #licenseFindingsMap;

    #licenses;

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.start_time) {
                this.#startTime = obj.start_time;
            }

            if (obj.startTime) {
                this.#startTime = obj.startTime;
            }

            if (obj.end_time) {
                this.#endTime = obj.end_time;
            }

            if (obj.endTime) {
                this.#endTime = obj.endTime;
            }

            if (obj.file_count) {
                this.#fileCount = obj.file_count;
            }

            if (obj.fileCount) {
                this.#fileCount = obj.fileCount;
            }

            if (obj.license_findings) {
                this.licenseFindings = obj.license_findings;
            }

            if (obj.licenseFindings) {
                this.licenseFindings = obj.licenseFindings;
            }

            if (obj.errors) {
                this.errors = obj.errors;
            }
        }
    }

    get startTime() {
        return this.#startTime;
    }

    get endTime() {
        return this.#endTime;
    }

    get fileCount() {
        return this.#fileCount;
    }

    get licenseFindings() {
        return this.#licenseFindings;
    }

    set licenseFindings(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#licenseFindings.push(new LicenseFinding(val[i]));
        }
    }

    get errors() {
        return this.#errors;
    }

    set errors(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#errors.push(new OrtIssue(val[i]));
        }
    }

    get licenseFindingsMap() {
        if (!this.#licenseFindingsMap) {
            this.#licenseFindingsMap = new Set();
            this.licenseFindings.forEach((finding) => {
                this.#licenseFindingsMap.add(
                    finding.license,
                    finding.copyrights
                );
            });
        }

        return this.#licenseFindingsMap;
    }

    get licenses() {
        if (!this.#licenses) {
            this.#licenses = new Set(this.licenseFindingsMap.keys());
        }

        return this.#licenses;
    }
}

export default ScanSummary;
