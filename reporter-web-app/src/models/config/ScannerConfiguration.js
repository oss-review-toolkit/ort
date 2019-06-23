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

import ArtifactoryStorageConfiguration from './ArtifactoryStorageConfiguration';

class ScannerConfiguration {
    #artifactoryStorage = new ArtifactoryStorageConfiguration();

    #scanner = null;

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.artifactory_storage) {
                this.artifactoryStorage = obj.artifactory_storage;
            }

            if (obj.artifactoryStorage) {
                this.artifactoryStorage = obj.artifactoryStorage;
            }

            if (obj.scanner) {
                this.scanner = obj.scanner;
            }
        }
    }

    get artifactoryStorage() {
        return this.#artifactoryStorage;
    }

    set artifactoryStorage(val) {
        this.#artifactoryStorage = new ArtifactoryStorageConfiguration(val);
    }

    get scanner() {
        return this.#scanner;
    }

    set scanner(val) {
        this.#scanner = val;
    }
}

export default ScannerConfiguration;
