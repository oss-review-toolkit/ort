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

import ErrorResolution from './ErrorResolution';
import RuleViolationResolution from './RuleViolationResolution';

class Resolutions {
    #errors = [];

    #resolutions = [];

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.errors) {
                this.errors = obj.errors;
            }

            if (obj.resolutions) {
                this.resolutions = obj.resolutions;
            }
        }
    }

    get errors() {
        return this.#errors;
    }

    set errors(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#errors.push(new ErrorResolution(val[i]));
        }
    }

    get resolutions() {
        return this.#resolutions;
    }

    set resolutions(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#resolutions.push(new RuleViolationResolution(val[i]));
        }
    }
}

export default Resolutions;
