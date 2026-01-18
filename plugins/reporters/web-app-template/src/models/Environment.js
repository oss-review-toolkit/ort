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

class Environment {
    #buildJdk;

    #javaVersion;

    #maxMemory;

    #os;

    #ortVersion;

    #processors;

    #variables = new Map();

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.buildJdk || obj.build_jdk) {
                this.#buildJdk = obj.buildJdk || obj.build_jdk;
            }

            if (obj.javaVersion || obj.java_version) {
                this.#javaVersion = obj.javaVersion || obj.java_version;
            }

            if (obj.maxMemory || obj.max_memory) {
                this.#maxMemory = obj.maxMemory || obj.max_memory;
            }

            if (obj.os) {
                this.#os = obj.os;
            }

            if (obj.ortVersion || obj.ort_version) {
                this.#ortVersion = obj.ortVersion || obj.ort_version;
            }

            if (obj.processors) {
                this.#processors = obj.processors;
            }

            if (obj.variables) {
                Object.entries(obj.variables).forEach(
                    ([key, value]) => this.#variables.set(key, value)
                );
            }
        }
    }

    get buildJdk() {
        return this.#buildJdk;
    }

    get javaVersion() {
        return this.#javaVersion;
    }

    get maxMemory() {
        return this.#maxMemory;
    }

    get os() {
        return this.#os;
    }

    get ortVersion() {
        return this.#ortVersion;
    }

    get processors() {
        return this.#processors;
    }

    get variables() {
        return this.#variables;
    }
}

export default Environment;
