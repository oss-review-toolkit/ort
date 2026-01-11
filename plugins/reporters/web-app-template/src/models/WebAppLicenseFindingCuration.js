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

class WebAppLicenseFindingCuration {
    #_id;

    #comment;

    #concludedLicense;

    #detectedLicense;

    #lineCount;

    #path;

    #reason;

    #startLines = [];

    constructor(obj, webAppOrtResult) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.comment) {
                this.#comment = obj.comment;
            }

            if (obj.concluded_license || obj.concludedLicense) {
                this.#concludedLicense = obj.concluded_license || obj.concludedLicense;
            }

            if (obj.detected_license || obj.detectedLicense) {
                this.#detectedLicense = obj.detected_license || obj.detectedLicense;
            }

            if (obj.line_count || obj.lineCount) {
                this.#lineCount = obj.line_count || obj.lineCount;
            }

            if (obj.path) {
                this.#path = obj.path;
            }

            if (obj.reason) {
                this.#reason = obj.reason;
            }

            if (obj.start_lines || obj.startLines) {
                this.#startLines = obj.start_lines || obj.startLines;
            }

            this.key = randomStringGenerator(20);
        }
    }

    get _id() {
        return this.#_id;
    }

    get comment() {
        return this.#comment;
    }

    get concludedLicense() {
        return this.#concludedLicense;
    }

    get detectedLicense() {
        return this.#detectedLicense;
    }

    get lineCount() {
        return this.#lineCount;
    }

    get path() {
        return this.#path;
    }

    get reason() {
        return this.#reason;
    }

    get startLines() {
        return this.#startLines;
    }
}

export default WebAppLicenseFindingCuration;
