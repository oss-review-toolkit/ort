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
import TextLocation from './TextLocation';

class LicenseFinding {
    #license = '';

    #locations = [];

    #copyrights = [];

    constructor(obj) {
        if (obj instanceof Object) {
            Object.keys(obj).forEach((key) => {
                if (obj[key] !== undefined) {
                    this[key] = obj[key];
                }
            });
        }
    }

    get license() {
        return this.#license;
    }

    set license(val) {
        this.#license = val;
    }

    get locations() {
        return this.#locations;
    }

    set locations(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#locations.push(new TextLocation(val[i]));
        }
    }

    get copyrights() {
        return this.#copyrights;
    }

    set copyrights(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#copyrights.push(new CopyrightFinding(val[i]));
        }
    }
}

export default LicenseFinding;
