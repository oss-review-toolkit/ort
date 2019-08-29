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

import CopyrightFinding from './CopyrightFinding';
import LicenseFinding from './LicenseFinding';
import OrtIssue from './OrtIssue';

class ScanSummary {
    #startTime = '';

    #endTime = '';

    #errors = [];

    #fileCount = 0;

    #licenseFindings = [];

    #copyrightFindings = [];

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

            if (obj.licenses) {
                this.licenseFindings = obj.licenses;
            }

            if (obj.copyrights) {
                this.copyrightFindings = obj.copyrights;
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

    get copyrightFindings() {
        return this.#copyrightFindings;
    }

    set copyrightFindings(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#copyrightFindings.push(new CopyrightFinding(val[i]));
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

    get licenses() {
        if (!this.#licenses) {
            this.#licenses = new Set();
            this.#licenseFindings.forEach((finding) => {
                this.#licenses.add(finding.license);
            });
        }

        return this.#licenses;
    }
}

export default ScanSummary;
