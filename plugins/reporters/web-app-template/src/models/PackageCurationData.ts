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

import RemoteArtifact from "@/models/RemoteArtifact";
import VcsInfo from "@/models/VcsInfo";
import type { EvaluatedModelPackageCurationData } from "@/types/evaluatedModelData";

class PackageCurationData {
    #authors: readonly string[] | undefined;

    #binaryArtifact: RemoteArtifact | undefined;

    #cpe: string | undefined;

    #comment: string | undefined;

    #concludedLicense: string | undefined;

    #description: string | undefined;

    #homepageUrl: string | undefined;

    #isMetadataOnly: boolean | undefined;

    #isModified: boolean | undefined;

    #labels = new Map<string, string>();

    #purl: string | undefined;

    #sourceArtifact: RemoteArtifact | undefined;

    #sourceCodeOrigins: ReadonlyArray<"ARTIFACT" | "VCS"> | undefined;

    #vcs: VcsInfo | undefined;

    constructor(obj?: EvaluatedModelPackageCurationData) {
        if (obj) {
            if (obj.authors) {
                this.#authors = obj.authors;
            }

            if (obj.binary_artifact || obj.binaryArtifact) {
                const binaryArtifact = obj.binary_artifact || obj.binaryArtifact;
                this.#binaryArtifact = new RemoteArtifact(binaryArtifact);
            }

            if (obj.cpe) {
                this.#cpe = obj.cpe;
            }

            if (obj.comment) {
                this.#comment = obj.comment;
            }

            if (obj.concluded_license || obj.concludedLicense) {
                this.#concludedLicense = obj.concluded_license || obj.concludedLicense;
            }

            if (obj.labels) {
                Object.entries(obj.labels).forEach(([key, value]) => {
                    this.#labels.set(key, value);
                });
            }

            if (obj.description) {
                this.#description = obj.description;
            }

            if (obj.homepage_url || obj.homepageUrl) {
                this.#homepageUrl = obj.homepage_url || obj.homepageUrl;
            }

            if (obj.is_metadata_only || obj.isMetadataOnly) {
                this.#isMetadataOnly = obj.is_metadata_only || obj.isMetadataOnly;
            }

            if (obj.is_modified || obj.isModified) {
                this.#isModified = obj.is_modified || obj.isModified;
            }

            if (obj.purl) {
                this.#purl = obj.purl;
            }

            if (obj.source_artifact || obj.sourceArtifact) {
                const sourceArtifact = obj.source_artifact || obj.sourceArtifact;
                this.#sourceArtifact = new RemoteArtifact(sourceArtifact);
            }

            if (obj.source_code_origins || obj.sourceCodeOrigins) {
                this.#sourceCodeOrigins = obj.source_code_origins || obj.sourceCodeOrigins;
            }

            if (obj.vcs) {
                this.#vcs = new VcsInfo(obj.vcs);
            }
        }
    }

    get authors(): readonly string[] | undefined {
        return this.#authors;
    }

    get binaryArtifact(): RemoteArtifact | undefined {
        return this.#binaryArtifact;
    }

    get cpe(): string | undefined {
        return this.#cpe;
    }

    get comment(): string | undefined {
        return this.#comment;
    }

    get concludedLicense(): string | undefined {
        return this.#concludedLicense;
    }

    get description(): string | undefined {
        return this.#description;
    }

    get homepageUrl(): string | undefined {
        return this.#homepageUrl;
    }

    get isMetadataOnly(): boolean | undefined {
        return this.#isMetadataOnly;
    }

    get isModified(): boolean | undefined {
        return this.#isModified;
    }

    get labels(): ReadonlyMap<string, string> {
        return this.#labels;
    }

    get purl(): string | undefined {
        return this.#purl;
    }

    get sourceArtifact(): RemoteArtifact | undefined {
        return this.#sourceArtifact;
    }

    get sourceCodeOrigins(): ReadonlyArray<"ARTIFACT" | "VCS"> | undefined {
        return this.#sourceCodeOrigins;
    }

    get vcs(): VcsInfo | undefined {
        return this.#vcs;
    }
}

export default PackageCurationData;
