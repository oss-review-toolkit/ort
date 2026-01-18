/*
 * Copyright (C) 2021 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import Environment from './Environment';

class Metadata {
    #advisorEndTime;

    #advisorEnvironment;

    #advisorStartTime;

    #analyzerEndTime;

    #analyzerEnvironment;

    #analyzerStartTime;

    #evaluatorEndTime;

    #evaluatorEnvironment;

    #evaluatorStartTime;

    #scannerEndTime;

    #scannerEnvironment;

    #scannerStartTime;

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.advisor_end_time || obj.advisorEndTime) {
                this.#advisorEndTime = obj.advisor_end_time || obj.advisorEndTime;
            }

            if (obj.advisor_environment || obj.advisorEnvironment) {
                this.#advisorEnvironment = new Environment(
                    obj.advisor_environment || obj.advisorEnvironment
                );
            }

            if (obj.advisor_start_time || obj.advisorStartTime) {
                this.#advisorStartTime = obj.advisor_start_time || obj.advisorStartTime;
            }

            if (obj.analyzer_end_time || obj.analyzerEndTime) {
                this.#analyzerEndTime = obj.analyzer_end_time || obj.analyzerEndTime;
            }

            if (obj.analyzer_environment || obj.analyzerEnvironment) {
                this.#analyzerEnvironment = new Environment(
                    obj.analyzer_environment || obj.analyzerEnvironment
                );
            }

            if (obj.analyzer_start_time || obj.analyzerStartTime) {
                this.#analyzerStartTime = obj.analyzer_start_time || obj.analyzerStartTime;
            }

            if (obj.evaluator_end_time || obj.evaluatorEndTime) {
                this.#evaluatorEndTime = obj.evaluator_end_time || obj.evaluatorEndTime;
            }

            if (obj.evaluator_environment || obj.evaluatorEnvironment) {
                this.#evaluatorEnvironment = new Environment(
                    obj.evaluator_environment || obj.evaluatorEnvironment
                );
            }

            if (obj.evaluator_start_time || obj.evaluatorStartTime) {
                this.#evaluatorStartTime = obj.evaluator_start_time || obj.evaluatorStartTime;
            }

            if (obj.scanner_end_time || obj.scannerEndTime) {
                this.#scannerEndTime = obj.scanner_end_time || obj.scannerEndTime;
            }

            if (obj.scanner_environment || obj.scannerEnvironment) {
                this.#scannerEnvironment = new Environment(
                    obj.scanner_environment || obj.scannerEnvironment
                );
            }

            if (obj.scanner_start_time || obj.scannerStartTime) {
                this.#scannerStartTime = obj.scanner_start_time || obj.scannerStartTime;
            }
        }
    }

    get advisorEndTime() {
        return this.#advisorEndTime;
    }

    get advisorStartTime() {
        return this.#advisorStartTime;
    }

    get advisorEnvironment() {
        return this.#advisorEnvironment;
    }

    get analyzerEndTime() {
        return this.#analyzerEndTime;
    }

    get analyzerEnvironment() {
        return this.#analyzerEnvironment;
    }

    get analyzerStartTime() {
        return this.#analyzerStartTime;
    }

    get scannerEndTime() {
        return this.#scannerEndTime;
    }

    get scannerEnvironment() {
        return this.#scannerEnvironment;
    }

    get scannerStartTime() {
        return this.#scannerStartTime;
    }

    get evaluatorEndTime() {
        return this.#evaluatorEndTime;
    }

    get evaluatorEnvironment() {
        return this.#evaluatorEnvironment;
    }

    get evaluatorStartTime() {
        return this.#evaluatorStartTime;
    }
}

export default Metadata;
