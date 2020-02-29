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

import { randomStringGenerator } from '../utils';

class WebAppResolution {
    #_id;

    #comment;

    #message;

    #reason;

    constructor(obj) {
        if (obj) {
            if (Number.isInteger(obj._id)) {
                this.#_id = obj._id;
            }

            if (obj.comment) {
                this.#comment = obj.comment;
            }

            if (obj.message) {
                this.#message = obj.message;
            }

            if (obj.reason) {
                this.#reason = obj.reason;
            }
        }

        this.key = randomStringGenerator(20);
    }

    get _id() {
        return this.#_id;
    }

    get comment() {
        return this.#comment;
    }

    get message() {
        return this.#message;
    }

    get reason() {
        return this.#reason;
    }
}

export default WebAppResolution;
