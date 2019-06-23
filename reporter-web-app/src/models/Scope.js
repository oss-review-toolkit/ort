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

import PackageReference from './PackageReference';

class Scope {
    #name = '';

    #dependencies = [];

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.name) {
                this.name = obj.name;
            }

            if (obj.dependencies) {
                this.dependencies = obj.dependencies;
            }
        }
    }

    get name() {
        return this.#name;
    }

    set name(name) {
        this.#name = name;
    }

    get dependencies() {
        return this.#dependencies;
    }

    set dependencies(deps) {
        for (let i = 0, len = deps.length; i < len; i++) {
            this.#dependencies.push(new PackageReference(deps[i]));
        }
    }
}

export default Scope;
