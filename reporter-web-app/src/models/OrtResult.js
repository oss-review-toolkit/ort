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

import AnalyzerRun from './AnalyzerRun';
import EvaluatorRun from './EvaluatorRun';
import ScannerRun from './ScannerRun';
import Repository from './Repository';

class OrtResult {
    #repository = new Repository();

    #analyzer = new AnalyzerRun();

    #scanner = new ScannerRun();

    #evaluator = new EvaluatorRun();

    #data = new Map();

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.repository) {
                this.repository = obj.repository;
            }

            if (obj.analyzer) {
                this.analyzer = obj.analyzer;
            }

            if (obj.scanner) {
                this.scanner = obj.scanner;
            }

            if (obj.evaluator) {
                this.evaluator = obj.evaluator;
            }

            if (obj.data) {
                this.data = Object.entries(obj.data).reduce((accumulator, [key, value]) => {
                    if (key === 'job_parameters') {
                        accumulator.set('jobParameters', value);
                    }

                    if (key === 'process_parameters') {
                        accumulator.set('processParameters', value);
                    }

                    return accumulator;
                }, new Map());
            }
        }
    }

    get repository() {
        return this.#repository;
    }

    set repository(val) {
        this.#repository = new Repository(val);
    }

    get analyzer() {
        return this.#analyzer;
    }

    set analyzer(val) {
        this.#analyzer = new AnalyzerRun(val);
    }

    get scanner() {
        return this.#scanner;
    }

    set scanner(val) {
        this.#scanner = new ScannerRun(val);
    }

    get evaluator() {
        return this.#evaluator;
    }

    set evaluator(val) {
        this.#evaluator = new EvaluatorRun(val);
    }

    get data() {
        return this.#data;
    }

    set data(val) {
        this.#data = val;
    }
}

export default OrtResult;
