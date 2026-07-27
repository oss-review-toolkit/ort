/*
 * Copyright (C) 2019 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import type { EvaluatedModelEnvironment } from "@/types/evaluatedModelData";

class Environment {
    #buildJdk: string | undefined;

    #javaVersion: string | undefined;

    #maxMemory: number | undefined;

    #os: string | undefined;

    #ortVersion: string | undefined;

    #processors: number | undefined;

    #variables = new Map<string, string>();

    constructor(obj?: EvaluatedModelEnvironment) {
        if (obj) {
            if (obj.buildJdk || obj.build_jdk) {
                this.#buildJdk = obj.buildJdk || obj.build_jdk;
            }

            if (obj.javaVersion || obj.java_version) {
                this.#javaVersion = obj.javaVersion || obj.java_version;
            }

            if (Number.isInteger(obj.maxMemory) || Number.isInteger(obj.max_memory)) {
                this.#maxMemory = obj.maxMemory ?? obj.max_memory;
            }

            if (obj.os) {
                this.#os = obj.os;
            }

            if (obj.ortVersion || obj.ort_version) {
                this.#ortVersion = obj.ortVersion || obj.ort_version;
            }

            if (Number.isInteger(obj.processors)) {
                this.#processors = obj.processors;
            }

            if (obj.variables) {
                Object.entries(obj.variables).forEach(([key, value]) => {
                    this.#variables.set(key, value);
                });
            }
        }
    }

    get buildJdk(): string | undefined {
        return this.#buildJdk;
    }

    get javaVersion(): string | undefined {
        return this.#javaVersion;
    }

    get maxMemory(): number | undefined {
        return this.#maxMemory;
    }

    get os(): string | undefined {
        return this.#os;
    }

    get ortVersion(): string | undefined {
        return this.#ortVersion;
    }

    get processors(): number | undefined {
        return this.#processors;
    }

    get variables(): ReadonlyMap<string, string> {
        return this.#variables;
    }
}

export default Environment;
