/*
 * Copyright (C) 2020 HERE Europe B.V.
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

import { UNIQUE_COLORS } from '../data/colors';

class WebAppLicense {
    #_id;

    #id;

    constructor(obj) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.id) {
                this.#id = obj.id;
            }

            const { data: colors } = UNIQUE_COLORS;
            this.color = colors[this.#_id % colors.length];
        }
    }

    get _id() {
        return this.#_id;
    }

    get id() {
        return this.#id;
    }
}

export default WebAppLicense;
