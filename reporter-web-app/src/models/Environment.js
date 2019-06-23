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

class Environment {
    #ortVersion = '';

    #javaVersion = '';

    #os = '';

    #variables = new Map();

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.ort_version) {
                this.ortVersion = obj.ort_version;
            }

            if (obj.ortVersion) {
                this.ortVersion = obj.ortVersion;
            }

            if (obj.java_version) {
                this.javaVersion = obj.java_version;
            }

            if (obj.javaVersion) {
                this.javaVersion = obj.javaVersion;
            }

            if (obj.os) {
                this.os = obj.os;
            }

            if (obj.variables) {
                this.variables = obj.variables;
            }
        }
    }

    get ortVersion() {
        return this.#ortVersion;
    }

    set ortVersion(val) {
        this.#ortVersion = val;
    }

    get javaVersion() {
        return this.#javaVersion;
    }

    set javaVersion(val) {
        this.#javaVersion = val;
    }

    get os() {
        return this.#os;
    }

    set os(val) {
        this.#os = val;
    }

    get variables() {
        return this.#variables;
    }

    set variables(val) {
        this.#variables = new Map(Object.entries(val));
    }
}

export default Environment;
