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

import Environment from './Environment';

class Run {
    #environment;

    #startTime;

    #endTime;

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.end_time || obj.endTime) {
                this.#endTime = obj.end_time || obj.endTime;
            }

            if (obj.environment) {
                this.#environment = new Environment(obj.environment);
            }

            if (obj.start_time || obj.startTime) {
                this.#startTime = obj.start_time || obj.startTime;
            }
        }
    }

    get endTime() {
        return this.#endTime;
    }

    get environment() {
        return this.#environment;
    }

    get startTime() {
        return this.#startTime;
    }
}

export default Run;
