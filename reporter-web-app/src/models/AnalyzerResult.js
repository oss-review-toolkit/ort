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

import CuratedPackage from './CuratedPackage';
import OrtIssue from './OrtIssue';
import Project from './Project';

class AnalyzerResult {
    #projects = [];

    #packages = [];

    #errors = new Map();

    constructor(obj) {
        this.hasErrors = false;

        if (obj instanceof Object) {
            if (obj.projects) {
                this.projects = obj.projects;
            }

            if (obj.packages) {
                this.packages = obj.packages;
            }

            if (obj.errors) {
                this.errors = obj.errors;
            }

            if (obj.has_errors) {
                this.hasErrors = obj.has_errors;
            }

            if (obj.hasErrors) {
                this.hasErrors = obj.hasErrors;
            }
        }
    }

    get projects() {
        return this.#projects;
    }

    set projects(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#projects.push(new Project(val[i]));
        }
    }

    get packages() {
        return this.#packages;
    }

    set packages(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#packages.push(new CuratedPackage(val[i]));
        }
    }

    get errors() {
        return this.#errors;
    }

    set errors(obj) {
        Object.keys(obj).forEach((key) => {
            this.#errors.set(key, new OrtIssue(obj[key]));
        });
    }
}

export default AnalyzerResult;
