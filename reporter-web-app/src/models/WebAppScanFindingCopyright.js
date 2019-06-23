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

/* eslint constructor-super: 0 */
/* eslint class-methods-use-this: 0 */

import WebAppScanFinding from './WebAppScanFinding';

class WebAppScanFindingCopyright extends WebAppScanFinding {
    #statement = '';

    constructor(obj) {
        if (obj) {
            super(obj);

            if (obj.statement) {
                this.#statement = obj.statement;
            }
        }
    }

    get type() {
        return 'COPYRIGHT';
    }

    get statement() {
        return this.#statement;
    }

    get value() {
        return this.#statement;
    }
}

export default WebAppScanFindingCopyright;
