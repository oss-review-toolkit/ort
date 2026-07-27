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

import RemoteArtifact from "@/models/RemoteArtifact";
import VcsInfo from "@/models/VcsInfo";
import type { EvaluatedModelProvenance } from "@/types/evaluatedModelData";

class Provenance {
    #sourceArtifact: RemoteArtifact = new RemoteArtifact();

    #vcsInfo: VcsInfo = new VcsInfo();

    constructor(obj?: EvaluatedModelProvenance) {
        if (obj) {
            const sourceArtifact = obj.source_artifact ?? obj.sourceArtifact;
            if (sourceArtifact) {
                this.#sourceArtifact = new RemoteArtifact(sourceArtifact);
            }

            const vcsInfo = obj.vcs_info ?? obj.vcsInfo;
            if (vcsInfo) {
                this.#vcsInfo = new VcsInfo(vcsInfo);
            }
        }
    }

    get sourceArtifact(): RemoteArtifact {
        return this.#sourceArtifact;
    }

    get vcsInfo(): VcsInfo {
        return this.#vcsInfo;
    }
}

export default Provenance;
