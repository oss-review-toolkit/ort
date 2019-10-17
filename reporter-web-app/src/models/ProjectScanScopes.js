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

class ProjectScanScopes {
    #id = '';

    #scannedScopes = [];

    #ignoredScopes = [];

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.id) {
                this.id = obj.id;
            }

            if (obj.scopes) {
                this.scannedScopes = obj.scopes;
            }

            if (obj.scanned_scopes) {
                this.scannedScopes = obj.scanned_scopes;
            }

            if (obj.scannedScopes) {
                this.scannedScopes = obj.scannedScopes;
            }

            if (obj.ignored_scopes) {
                this.ignoredScopes = obj.ignored_scopes;
            }

            if (obj.ignoredScopes) {
                this.ignoredScopes = obj.ignoredScopes;
            }
        }
    }

    get id() {
        return this.#id;
    }

    set id(val) {
        this.#id = val;
    }

    get scannedScopes() {
        return this.#scannedScopes;
    }

    set scannedScopes(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#scannedScopes.push(val[i]);
        }
    }

    get ignoredScopes() {
        return this.#ignoredScopes;
    }

    set ignoredScopes(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#ignoredScopes.push(val[i]);
        }
    }
}

export default ProjectScanScopes;
