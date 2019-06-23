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

import PathExclude from './PathExclude';
import ProjectExclude from './ProjectExclude';
import ScopeExclude from './ScopeExclude';

class Excludes {
    #paths = [];

    #projects = [];

    #scopes = [];

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.paths) {
                this.#paths = obj.paths;
            }

            if (obj.projects) {
                this.#projects = obj.projects;
            }

            if (obj.scopes) {
                this.#scopes = obj.scopes;
            }
        }
    }

    get paths() {
        return this.#paths;
    }

    set paths(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#paths.push(new PathExclude(val[i]));
        }
    }

    get projects() {
        return this.#projects;
    }

    set projects(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#projects.push(new ProjectExclude(val[i]));
        }
    }

    get scopes() {
        return this.#scopes;
    }

    set scopes(val) {
        for (let i = 0, len = val.length; i < len; i++) {
            this.#scopes.push(new ScopeExclude(val[i]));
        }
    }
}

export default Excludes;
