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

import OrtIssue from './OrtIssue';

class PackageReference {
    #id = '';

    #linkage = '';

    #dependencies = [];

    #errors = [];

    constructor(obj) {
        if (obj instanceof Object) {
            Object.keys(obj).forEach((key) => {
                if (obj[key] !== undefined) {
                    this[key] = obj[key];
                }
            });
        }
    }

    get id() {
        return this.#id;
    }

    set id(val) {
        this.#id = val;
    }

    get linkage() {
        return this.#linkage;
    }

    set linkage(val) {
        this.#linkage = val;
    }

    get dependencies() {
        return this.#dependencies;
    }

    set dependencies(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#dependencies.push(new PackageReference(val[i]));
        }
    }

    get errors() {
        return this.#errors;
    }

    set errors(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#errors.push(new OrtIssue(val[i]));
        }
    }
}

export default PackageReference;
