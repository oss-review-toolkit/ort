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

import WebAppPathExclude from './WebAppPathExclude';
import WebAppScopeExclude from './WebAppScopeExclude';

class Excludes {
    #paths = [];

    #scopes = [];

    constructor(obj, webAppOrtResult) {
        if (obj) {
            if (obj.paths) {
                for (let i = 0, len = obj.paths.length; i < len; i++) {
                    this.#paths.push(new WebAppPathExclude(obj.paths[i]));
                }
            }

            if (obj.scopes) {
                for (let i = 0, len = obj.scopes.length; i < len; i++) {
                    this.#scopes.push(new WebAppScopeExclude(obj.scopes[i]));
                }
            }
        }
    }

    get paths() {
        return this.#paths;
    }

    get scopes() {
        return this.#scopes;
    }
}

export default Excludes;
