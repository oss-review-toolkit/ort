/*
 * Copyright (C) 2020 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import Provenance from "@/models/Provenance";
import ScannerDetails from "@/models/ScannerDetails";
import type { EvaluatedModelIssue, EvaluatedModelScanResult } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

class WebAppScanResult {
    #_id: number | undefined;

    #endTime: string | undefined;

    #issues: readonly EvaluatedModelIssue[] = [];

    #packageVerificationCode: string = "";

    #provenance: Provenance | undefined;

    #scanner: ScannerDetails | undefined;

    #startTime: string | undefined;

    key: string | undefined;

    constructor(obj?: EvaluatedModelScanResult) {
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
                this.#packageVerificationCode = (obj.package_verification_code || obj.packageVerificationCode) ?? "";
            }

            if (obj.scanner) {
                this.#scanner = new ScannerDetails(obj.scanner);
            }

            if (obj.start_time || obj.startTime) {
                this.#startTime = obj.start_time || obj.startTime;
            }

            this.key = randomStringGenerator(20);
        }
    }

    get _id(): number | undefined {
        return this.#_id;
    }

    get endTime(): string | undefined {
        return this.#endTime;
    }

    get issues(): readonly EvaluatedModelIssue[] {
        return this.#issues;
    }

    get packageVerificationCode(): string {
        return this.#packageVerificationCode;
    }

    get provenance(): Provenance | undefined {
        return this.#provenance;
    }

    get scanner(): ScannerDetails | undefined {
        return this.#scanner;
    }

    get startTime(): string | undefined {
        return this.#startTime;
    }
}

export default WebAppScanResult;
