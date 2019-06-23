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

import RepositoryConfiguration from './config/RepositoryConfiguration';
import VcsInfo from './VcsInfo';

class Repository {
    #vcs = new VcsInfo();

    #vcsProcessed = new VcsInfo();

    #nestedRepositories = new Map();

    #config = new RepositoryConfiguration();

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.vcs) {
                this.vcs = obj.vcs;
            }

            if (obj.vcs_processed) {
                this.vcsProcessed = obj.vcs_processed;
            }

            if (obj.vcsProcessed) {
                this.vcsProcessed = obj.vcsProcessed;
            }

            if (obj.nested_repositories) {
                this.nestedRepositories = obj.nested_repositories;
            }

            if (obj.nestedRepositories) {
                this.nestedRepositories = obj.nestedRepositories;
            }

            if (obj.config) {
                this.config = obj.config;
            }
        }
    }

    get vcs() {
        return this.#vcs;
    }

    set vcs(val) {
        this.#vcs = new VcsInfo(val);
    }

    get vcsProcessed() {
        return this.#vcsProcessed;
    }

    set vcsProcessed(val) {
        this.#vcsProcessed = new VcsInfo(val);
    }

    get nestedRepositories() {
        return this.#nestedRepositories;
    }

    set nestedRepositories(obj) {
        Object.keys(obj).forEach((key) => {
            this.#nestedRepositories.set(key, new VcsInfo(obj[key]));
        });
    }

    get config() {
        return this.#config;
    }

    set config(val) {
        this.#config = new RepositoryConfiguration(val);
    }
}

export default Repository;
