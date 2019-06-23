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

import Excludes from './Excludes';
import Resolutions from './Resolutions';

class RepositoryConfiguration {
    #excludes = new Excludes({});

    #resolutions = new Resolutions({});

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.excludes) {
                this.excludes = obj.excludes;
            }

            if (obj.resolutions) {
                this.resolutions = obj.resolutions;
            }
        }
    }

    get excludes() {
        return this.#excludes;
    }

    set excludes(val) {
        this.#excludes = new Excludes(val);
    }

    get resolutions() {
        return this.#resolutions;
    }

    set resolutions(val) {
        this.#resolutions = new Resolutions(val);
    }
}

export default RepositoryConfiguration;
