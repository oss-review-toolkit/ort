/*
 * Copyright (C) 2017-2019 HERE Europe B.V.
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

package org.ossreviewtoolkit.model

/**
 * A class to denote the linkage type between two packages.
 */
enum class PackageLinkage {
    /**
     * A dynamically linked package whose source code is not directly defined in the project itself, but which is
     * retrieved as an external artifact.
     */
    DYNAMIC,

    /**
     * A statically linked package whose source code is not directly defined in the project itself, but which is
     * retrieved as an external artifact.
     */
    STATIC,

    /**
     * A dynamically linked package whose source code is part of the project itself, e.g. a subproject of a
     * multi-project.
     */
    PROJECT_DYNAMIC,

    /**
     * A statically linked package whose source code is part of the project itself, e.g. a subproject of a
     * multi-project.
     */
    PROJECT_STATIC;

    companion object {
        /**
         * A set of linkage types that all refer to a subproject in a multi-project.
         */
        val PROJECT_LINKAGE = setOf(PROJECT_DYNAMIC, PROJECT_STATIC)
    }
}
