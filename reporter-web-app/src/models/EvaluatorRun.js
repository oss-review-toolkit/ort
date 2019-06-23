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

import RuleViolation from './RuleViolation';

class EvaluatorRun {
    #startTime = '';

    #endTime = '';

    #violations = [];

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.start_time) {
                this.startTime = obj.start_time;
            }

            if (obj.startTime) {
                this.startTime = obj.startTime;
            }

            if (obj.end_time) {
                this.endTime = obj.end_time;
            }

            if (obj.endTime) {
                this.endTime = obj.endTime;
            }

            if (obj.violations) {
                this.violations = obj.violations;
            }
        }
    }

    get startTime() {
        return this.#startTime;
    }

    set startTime(val) {
        this.#startTime = val;
    }

    get endTime() {
        return this.#endTime;
    }

    set endTime(val) {
        this.#endTime = val;
    }

    get violations() {
        return this.#violations;
    }

    set violations(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#violations.push(new RuleViolation(val[i]));
        }
    }
}

export default EvaluatorRun;
