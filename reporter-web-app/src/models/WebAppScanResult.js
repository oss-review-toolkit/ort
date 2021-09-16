/*
 * Copyright (C) 2020-2021 HERE Europe B.V.
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

import Provenance from './Provenance';
import ScannerDetails from './ScannerDetails';

class WebAppScanResult {
    #_id;

    #endTime;

    #issues = [];

    #packageVerificationCode = '';

    #provenance;

    #scanner;

    #startTime;

    constructor(obj) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.end_time || obj.endTime) {
                this.#endTime = obj.end_time || obj.endTime;
            }

            if (obj.issues) {
                this.#issues = obj.issues;
            }

            if (obj.provenance) {
                this.#provenance = new Provenance(obj.provenance);
            }

            if (obj.package_verification_code || obj.packageVerificationCode) {
                this.#packageVerificationCode = obj.package_verification_code
                    || obj.packageVerificationCode;
            }

            if (obj.scanner) {
                this.#scanner = new ScannerDetails(obj.scanner);
            }

            if (obj.start_time || obj.startTime) {
                this.#startTime = obj.start_time || obj.startTime;
            }
        }
    }

    get _id() {
        return this.#_id;
    }

    get endTime() {
        return this.#endTime;
    }

    get issues() {
        return this.#issues;
    }

    get packageVerificationCode() {
        return this.#packageVerificationCode;
    }

    get provenance() {
        return this.#provenance;
    }

    get scanner() {
        return this.#scanner;
    }

    get startTime() {
        return this.#startTime;
    }
}

export default WebAppScanResult;
