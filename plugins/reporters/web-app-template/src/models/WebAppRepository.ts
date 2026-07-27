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

import VcsInfo from "@/models/VcsInfo";
import type WebAppEvaluatedModel from "@/models/WebAppEvaluatedModel";
import WebAppRepositoryConfiguration from "@/models/WebAppRepositoryConfiguration";
import type { EvaluatedModelRepository } from "@/types/evaluatedModelData";

class WebAppRepository {
    #vcs: VcsInfo = new VcsInfo();

    #vcsProcessed: VcsInfo = new VcsInfo();

    #nestedRepositories = new Map<string, VcsInfo>();

    #config: WebAppRepositoryConfiguration = new WebAppRepositoryConfiguration();

    constructor(obj?: EvaluatedModelRepository, webAppEvaluatedModel?: WebAppEvaluatedModel) {
        if (obj) {
            if (obj.vcs) {
                this.#vcs = new VcsInfo(obj.vcs);
            }

            if (obj.vcs_processed || obj.vcsProcessed) {
                const vcsProcessed = obj.vcs_processed || obj.vcsProcessed;
                this.#vcsProcessed = new VcsInfo(vcsProcessed);
            }

            if (obj.nested_repositories || obj.nestedRepositories) {
                const nestedRepositories = obj.nested_repositories || obj.nestedRepositories;

                if (nestedRepositories) {
                    Object.entries(nestedRepositories).forEach(([key, vcsInfo]) => {
                        this.#nestedRepositories.set(key, new VcsInfo(vcsInfo));
                    });
                }
            }

            if (obj.config) {
                this.#config = new WebAppRepositoryConfiguration(obj.config, webAppEvaluatedModel);
            }
        }
    }

    get vcs(): VcsInfo {
        return this.#vcs;
    }

    get vcsProcessed(): VcsInfo {
        return this.#vcsProcessed;
    }

    get nestedRepositories(): ReadonlyMap<string, VcsInfo> {
        return this.#nestedRepositories;
    }

    get config(): WebAppRepositoryConfiguration {
        return this.#config;
    }
}

export default WebAppRepository;
