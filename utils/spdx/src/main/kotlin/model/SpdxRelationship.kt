/*
 * Copyright (C) 2020 HERE Europe B.V.
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

package org.ossreviewtoolkit.utils.spdx.model

import com.fasterxml.jackson.annotation.JsonInclude

/**
 * A relationship between two SPDX documents, packages or files.
 */
data class SpdxRelationship(
    /**
     * The source element of this directed relationship.
     */
    val spdxElementId: String,

    /**
     * The type of this relationship.
     */
    val relationshipType: Type,

    /**
     * The target element of this directed relationship.
     */
    val relatedSpdxElement: String,

    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = ""
) {
    enum class Type {
        /**
         * Is to be used when (current) SPDXRef-DOCUMENT amends the SPDX information in SPDXRef-B.
         */
        AMENDS,

        /**
         * Is to be used when SPDXRef-A is an ancestor (same lineage but pre-dates) of SPDXRef-B.
         */
        ANCESTOR_OF,

        /**
         * Is to be used when SPDXRef-A is a build dependency of SPDXRef-B.
         */
        BUILD_DEPENDENCY_OF,

        /**
         * Is to be used when SPDXRef-A is used to build SPDXRef-B.
         */
        BUILD_TOOL_OF,

        /**
         * Is to be used when SPDXRef-A is contained by SPDXRef-B.
         */
        CONTAINED_BY,

        /**
         * Is to be used when SPDXRef-A contains SPDXRef-B.
         */
        CONTAINS,

        /**
         * Is to be used when SPDXRef-A is an exact copy of SPDXRef-B.
         */
        COPY_OF,

        /**
         * Is to be used when SPDXRef-A is a data file used in SPDXRef-B.
         */
        DATA_FILE_OF,

        /**
         * Is to be used when SPDXRef-A is a manifest file that lists a set of dependencies for SPDXRef-B.
         */
        DEPENDENCY_MANIFEST_OF,

        /**
         * Is to be used when SPDXRef-A is dependency of SPDXRef-B.
         */
        DEPENDENCY_OF,

        /**
         * Is to be used when SPDXRef-A depends on SPDXRef-B.
         */
        DEPENDS_ON,

        /**
         * Is to be used when SPDXRef-A is a descendant of (same lineage but postdates) SPDXRef-B.
         */
        DESCENDANT_OF,

        /**
         * Is to be used when SPDXRef-A is described by SPDXREF-Document.
         */
        DESCRIBED_BY,

        /**
         * Is to be used when SPDXRef-DOCUMENT describes SPDXRef-A.
         */
        DESCRIBES,

        /**
         * Is to be used when SPDXRef-A is a development dependency of SPDXRef-B.
         */
        DEV_DEPENDENCY_OF,

        /**
         * Is to be used when SPDXRef-A is used as a development tool for SPDXRef-B.
         */
        DEV_TOOL_OF,

        /**
         * Is to be used when distributing SPDXRef-A requires that SPDXRef-B also be distributed.
         */
        DISTRIBUTION_ARTIFACT,

        /**
         * Is to be used when SPDXRef-A provides documentation of SPDXRef-B.
         */
        DOCUMENTATION_OF,

        /**
         * Is to be used when SPDXRef-A dynamically links to SPDXRef-B.
         */
        DYNAMIC_LINK,

        /**
         * Is to be used when SPDXRef-A is an example of SPDXRef-B.
         */
        EXAMPLE_OF,

        /**
         * Is to be used when SPDXRef-A is expanded from the archive SPDXRef-B.
         */
        EXPANDED_FROM_ARCHIVE,

        /**
         * Is to be used when SPDXRef-A is a file that was added to SPDXRef-B.
         */
        FILE_ADDED,

        /**
         * Is to be used when SPDXRef-A is a file that was deleted from SPDXRef-B.
         */
        FILE_DELETED,

        /**
         * Is to be used when SPDXRef-A is a file that was modified from SPDXRef-B.
         */
        FILE_MODIFIED,

        /**
         * Is to be used when SPDXRef-A was generated from SPDXRef-B.
         */
        GENERATED_FROM,

        /**
         * Is to be used when SPDXRef-A generates SPDXRef-B.
         */
        GENERATES,

        /**
         * Is to be used when SPDXRef-A has as a prerequisite SPDXRef-B.
         */
        HAS_PREREQUISITE,

        /**
         * Is to be used when SPDXRef-A is a metafile of SPDXRef-B.
         */
        METAFILE_OF,

        /**
         * Is to be used when SPDXRef-A is an optional component of SPDXRef-B.
         */
        OPTIONAL_COMPONENT_OF,

        /**
         * Is to be used when SPDXRef-A is an optional dependency of SPDXRef-B.
         */
        OPTIONAL_DEPENDENCY_OF,

        /**
         * Is to be used when SPDXRef-A is used as a package as part of SPDXRef-B.
         */
        PACKAGE_OF,

        /**
         * Is to be used when SPDXRef-A is a patch file that has been applied to SPDXRef-B.
         */
        PATCH_APPLIED,

        /**
         * Is to be used when SPDXRef-A is a patch file for (to be applied to) SPDXRef-B.
         */
        PATCH_FOR,

        /**
         * Is to be used when SPDXRef-A is a prerequisite for SPDXRef-B.
         */
        PREREQUISITE_FOR,

        /**
         * Is to be used when SPDXRef-A is a to be provided dependency of SPDXRef-B.
         */
        PROVIDED_DEPENDENCY_OF,

        /**
         * Is to be used when SPDXRef-A is a dependency required for the execution of SPDXRef-B.
         */
        RUNTIME_DEPENDENCY_OF,

        /**
         * Is to be used when SPDXRef-A statically links to SPDXRef-B.
         */
        STATIC_LINK,

        /**
         * Is to be used when SPDXRef-A is a test case used in testing SPDXRef-B.
         */
        TEST_CASE_OF,

        /**
         * Is to be used when SPDXRef-A is a test dependency of SPDXRef-B.
         */
        TEST_DEPENDENCY_OF,

        /**
         * Is to be used when SPDXRef-A is used for testing SPDXRef-B.
         */
        TEST_OF,

        /**
         * Is to be used when SPDXRef-A is used as a test tool for SPDXRef-B.
         */
        TEST_TOOL_OF,

        /**
         * Is to be used when SPDXRef-A is a variant of (same lineage but not clear which came first) SPDXRef-B.
         */
        VARIANT_OF;
    }

    init {
        require(spdxElementId.isNotBlank()) {
            "The SPDX element ID must not be blank."
        }

        require(relatedSpdxElement.isNotBlank()) {
            "The related SPDX element must not be blank."
        }
    }
}
