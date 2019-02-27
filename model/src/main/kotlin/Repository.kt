/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package com.here.ort.model

import com.fasterxml.jackson.annotation.JsonInclude

import com.here.ort.model.config.RepositoryConfiguration

/**
 * A description of the source code repository that was used as input for ORT.
 */
data class Repository(
        /**
         * The [VcsInfo] of the repository.
         */
        val vcs: VcsInfo,

        /**
         * The [VcsInfo] of the repository.
         */
        val vcsProcessed: VcsInfo,

        /**
         * A map of nested repositories, for example Git submodules or Git-Repo modules. The key is the path to the
         * nested repository relative to the root of the main repository.
         */
        @JsonInclude(JsonInclude.Include.NON_EMPTY)
        val nestedRepositories: Map<String, VcsInfo> = emptyMap(),

        /**
         * The configuration of the repository, parsed from the ".ort.yml" file.
         */
        val config: RepositoryConfiguration
)
