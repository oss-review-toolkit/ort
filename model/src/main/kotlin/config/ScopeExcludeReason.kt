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

package org.ossreviewtoolkit.model.config

/**
 * Possible reasons for excluding a scope.
 */
enum class ScopeExcludeReason {
    /**
     * The scope only contains packages used for building source code which are not included in distributed build
     * artifacts.
     */
    @Deprecated(
        message = "Use SPDX-2.2-style enum value instead.",
        replaceWith = ReplaceWith(
            expression = "ScopeExcludeReason.BUILD_DEPENDENCY_OF",
            imports = ["org.ossreviewtoolkit.model.config.ScopeExcludeReason"]
        )
    )
    BUILD_TOOL_OF,

    /**
     * The scope only contains packages used for building source code which are not included in distributed build.
     */
    BUILD_DEPENDENCY_OF,

    /**
     * The scope only contains packages used for development which are not included in distributed build.
     */
    DEV_DEPENDENCY_OF,

    /**
     * The scope only contains packages that have to be provided by the user of distributed build artifacts.
     */
    @Deprecated(
        message = "Use SPDX-2.2-style enum value instead.",
        replaceWith = ReplaceWith(
            expression = "ScopeExcludeReason.PROVIDED_DEPENDENCY_OF",
            imports = ["org.ossreviewtoolkit.model.config.ScopeExcludeReason"]
        )
    )
    PROVIDED_BY,

    /**
     * The scope only contains packages that have to be provided by the user of distributed build artifacts.
     */
    PROVIDED_DEPENDENCY_OF,

    /**
     * The scope only contains packages used for testing source code which are not included in distributed build
     * artifacts.
     */
    @Deprecated(
        message = "Use SPDX-2.2-style enum value instead.",
        replaceWith = ReplaceWith(
            expression = "ScopeExcludeReason.TEST_DEPENDENCY_OF",
            imports = ["org.ossreviewtoolkit.model.config.ScopeExcludeReason"]
        )
    )
    TEST_TOOL_OF,

    /**
     * The scope only contains packages used for testing which are not included in distributed build.
     */
    TEST_DEPENDENCY_OF,

    /**
     * The scope only contains packages that have to be provided by the user during the execution of the artifacts but
     * are not included in distributed build artifacts.
     */
    RUNTIME_DEPENDENCY_OF
}
