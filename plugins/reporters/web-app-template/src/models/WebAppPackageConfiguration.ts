/*
 * Copyright (C) 2025 The ORT Project Copyright Holders <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>
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

import type { EvaluatedModelVcsMatcher, EvaluatedModelWebAppPackageConfiguration } from "@/types/evaluatedModelData";
import { randomStringGenerator } from "@/utils";

class VcsMatcher {
    #revision: string | undefined;

    #type: string | undefined;

    #url: string | undefined;

    constructor(obj: EvaluatedModelVcsMatcher) {
        if (obj.revision) {
            this.#revision = obj.revision;
        }

        if (obj.type) {
            this.#type = obj.type;
        }

        if (obj.url) {
            this.#url = obj.url;
        }
    }

    get revision(): string | undefined {
        return this.#revision;
    }

    get type(): string | undefined {
        return this.#type;
    }

    get url(): string | undefined {
        return this.#url;
    }
}

class WebAppPackageConfiguration {
    #id: string | undefined;

    #licenseFindingCurationIndexes: readonly number[] = [];

    #pathExcludeIndexes: readonly number[] = [];

    #sourceArtifactUrl: string | undefined;

    #sourceCodeOrigin: "ARTIFACT" | "VCS" | undefined;

    #vcs: VcsMatcher | undefined;

    key: string;

    constructor(obj: EvaluatedModelWebAppPackageConfiguration) {
        if (obj.id) {
            this.#id = obj.id;
        }

        if (obj.license_finding_curations || obj.licenseFindingCurations) {
            this.#licenseFindingCurationIndexes = obj.license_finding_curations || obj.licenseFindingCurations || [];
        }

        if (obj.path_excludes || obj.pathExcludes) {
            this.#pathExcludeIndexes = obj.path_excludes || obj.pathExcludes || [];
        }

        if (obj.source_artifact_url || obj.sourceArtifactUrl) {
            this.#sourceArtifactUrl = obj.source_artifact_url || obj.sourceArtifactUrl;
        }

        if (obj.source_code_origin || obj.sourceCodeOrigin) {
            const sourceCodeOrigin = obj.source_code_origin || obj.sourceCodeOrigin;

            if (sourceCodeOrigin === "ARTIFACT") {
                this.#sourceCodeOrigin = "ARTIFACT";
            }

            if (sourceCodeOrigin === "VCS") {
                this.#sourceCodeOrigin = "VCS";
            }
        }

        if (obj.vcs) {
            this.#vcs = new VcsMatcher(obj.vcs);
        }

        this.key = randomStringGenerator(20);
    }

    get id(): string | undefined {
        return this.#id;
    }

    get licenseFindingCurationIndexes(): readonly number[] {
        return this.#licenseFindingCurationIndexes;
    }

    get pathExcludeIndexes(): readonly number[] {
        return this.#pathExcludeIndexes;
    }

    get sourceArtifactUrl(): string | undefined {
        return this.#sourceArtifactUrl;
    }

    get sourceCodeOrigin(): "ARTIFACT" | "VCS" | undefined {
        return this.#sourceCodeOrigin;
    }

    get vcs(): VcsMatcher | undefined {
        return this.#vcs;
    }
}

export default WebAppPackageConfiguration;
