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

import AccessStatistics from './AccessStatistics';
import ProjectScanScopes from './ProjectScanScopes';
import ScanResultContainer from './ScanResultContainer';

class ScanRecord {
    #scannedScopes = [];

    #scanResults = [];

    #storageStats = new AccessStatistics();

    constructor(obj) {
        this.hasErrors = false;

        if (obj instanceof Object) {
            if (obj.scanned_scopes) {
                this.scannedScopes = obj.scanned_scopes;
            }

            if (obj.scannedScopes) {
                this.scannedScopes = obj.scannedScopes;
            }

            if (obj.scan_results) {
                this.scanResults = obj.scan_results;
            }

            if (obj.scanResults) {
                this.scanResults = obj.scanResults;
            }

            if (obj.storage_stats) {
                this.storageStats = obj.storage_stats;
            }

            if (obj.storageStats) {
                this.storageStats = obj.storageStats;
            }

            if (obj.has_errors) {
                this.hasErrors = obj.has_errors;
            }

            if (obj.hasErrors) {
                this.hasErrors = obj.hasErrors;
            }
        }
    }

    get scannedScopes() {
        return this.#scannedScopes;
    }

    set scannedScopes(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#scannedScopes.push(new ProjectScanScopes(val[i]));
        }
    }

    get scanResults() {
        return this.#scanResults;
    }

    set scanResults(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#scanResults.push(new ScanResultContainer(val[i]));
        }
    }

    get storageStats() {
        return this.#storageStats;
    }

    set storageStats(val) {
        this.#storageStats = val;
    }
}

export default ScanRecord;
