/*
 * Copyright (C) 2019-2020 HERE Europe B.V.
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

import VcsInfo from './VcsInfo';

class Repository {
    #vcs = new VcsInfo();

    #vcsProcessed = new VcsInfo();

    #nestedRepositories = new Map();

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.vcs) {
                this.#vcs = obj.vcs;
            }

            if (obj.vcs_processed || obj.vcsProcessed) {
                this.#vcsProcessed = obj.vcs_processed || obj.vcsProcessed;
            }

            if (obj.nested_repositories || obj.nestedRepositories) {
                this.#nestedRepositories = obj.nested_repositories || obj.nestedRepositories;
            }
        }
    }

    get vcs() {
        return this.#vcs;
    }

    get vcsProcessed() {
        return this.#vcsProcessed;
    }

    get nestedRepositories() {
        return this.#nestedRepositories;
    }
}

export default Repository;
