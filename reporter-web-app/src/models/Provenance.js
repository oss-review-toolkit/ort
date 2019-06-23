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

import RemoteArtifact from './RemoteArtifact';
import VcsInfo from './VcsInfo';

class Provenance {
    #downloadTime;

    #sourceArtifact = new RemoteArtifact();

    #vcsInfo = new VcsInfo();

    #originalVcsInfo = new VcsInfo();

    constructor(obj) {
        if (obj instanceof Object) {
            if (obj.download_time) {
                this.downloadTime = obj.download_time;
            }

            if (obj.downloadTime) {
                this.downloadTime = obj.downloadTime;
            }

            if (obj.source_artifact) {
                this.sourceArtifact = obj.source_artifact;
            }

            if (obj.sourceArtifact) {
                this.sourceArtifact = obj.sourceArtifact;
            }

            if (obj.vcs_info) {
                this.vcsInfo = obj.vcs_info;
            }

            if (obj.vcsInfo) {
                this.vcsInfo = obj.vcsInfo;
            }

            if (obj.original_vcs_info) {
                this.originalVcsInfo = obj.original_vcs_info;
            }

            if (obj.originalVcsInfo) {
                this.originalVcsInfo = obj.originalVcsInfo;
            }
        }
    }

    get downloadTime() {
        return this.#downloadTime;
    }

    set downloadTime(val) {
        this.#downloadTime = val;
    }

    get sourceArtifact() {
        return this.#sourceArtifact;
    }

    set sourceArtifact(val) {
        this.#sourceArtifact = new RemoteArtifact(val);
    }

    get vcsInfo() {
        return this.#vcsInfo;
    }

    set vcsInfo(val) {
        this.#vcsInfo = new VcsInfo(val);
    }

    get originalVcsInfo() {
        return this.#originalVcsInfo;
    }

    set originalVcsInfo(val) {
        this.#originalVcsInfo = new VcsInfo(val);
    }
}

export default Provenance;
