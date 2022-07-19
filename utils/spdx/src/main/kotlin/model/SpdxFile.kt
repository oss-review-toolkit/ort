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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonInclude
import com.fasterxml.jackson.annotation.JsonProperty

import org.ossreviewtoolkit.utils.spdx.SpdxConstants
import org.ossreviewtoolkit.utils.spdx.SpdxConstants.REF_PREFIX
import org.ossreviewtoolkit.utils.spdx.isSpdxExpressionOrNotPresent

/**
 * Provides important metadata about a particular file of a software package.
 */
@JsonIgnoreProperties("ranges") // TODO: Implement ranges which is broken in the specification examples.
data class SpdxFile(
    /**
     * A unique identifies this [SpdxFile] within a SPDX document.
     */
    @JsonProperty("SPDXID")
    val spdxId: String,

    /**
     * The [SpdxAnnotation]s for the file.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val annotations: List<SpdxAnnotation> = emptyList(),

    /**
     * Checksums of the file, must contain at least one entry using [SpdxChecksum.Algorithm.SHA1].
     */
    val checksums: List<SpdxChecksum>,

    /**
     * A general comment about the file.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val comment: String = "",

    /**
     * A text relating to a copyright notice, even if not complete. To represent a not present value
     * [SpdxConstants.NONE] or [SpdxConstants.NOASSERTION] must be used.
     */
    val copyrightText: String,

    /**
     * The list of contributors which contributed to the file. Contributors could include names of copyright holders
     * and/or authors who may not be copyright holders, yet contributed to the file content.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val fileContributors: List<String> = emptyList(),

    /**
     * This field is deprecated since SPDX 2.0 in favor of [SpdxDocument.relationships].
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val fileDependencies: List<String> = emptyList(),

    /**
     * The name of the file.
     */
    @JsonProperty("fileName")
    val filename: String,

    /**
     * The types of the file.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val fileTypes: List<Type> = emptyList(),

    /**
     * Any relevant background references or analysis that went in to arriving at the concluded License for the file.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val licenseComments: String = "",

    /**
     * The concluded license for the file as SPDX expression. To represent a not present value [SpdxConstants.NONE] or
     * [SpdxConstants.NOASSERTION] must be used.
     */
    val licenseConcluded: String,

    /**
     * The license information found in this file. To represent a not present value [SpdxConstants.NONE] or
     * [SpdxConstants.NOASSERTION] must be used.
     */
    val licenseInfoInFiles: List<String>,

    /**
     * License notices or other such related notices found in the file. This may include copyright statements.
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    val noticeText: String = ""
) {
    enum class Type {
        /**
         * The file is associated with a specific application type (MIME type of application e.g. .exe).
         */
        APPLICATION,

        /**
         * Indicates the file is an archive file.
         */
        ARCHIVE,

        /**
         * The file is associated with an audio file (MIME type of audio, e.g. .mp3).
         */
        AUDIO,

        /**
         * Indicates the file is not a text file.
         */
        BINARY,

        /**
         * The file serves as documentation.
         */
        DOCUMENTATION,

        /**
         * The file is associated with a picture image file (MIME type of image, e.g. .jpg, .gif).
         */
        IMAGE,

        /**
         * Indicates the file is not a source, archive or binary file.
         */
        OTHER,

        /**
         * Indicates the file is a source code file.
         */
        SOURCE,

        /**
         * The file is an SPDX document.
         */
        SPDX,

        /**
         * The file is a human-readable text file (MIME type of text).
         */
        TEXT,

        /**
         * The file is associated with a video file (MIME type of video, e.g. .avi, .mkv, .mp4)
         */
        VIDEO;
    }

    init {
        require(spdxId.startsWith(REF_PREFIX)) {
            "The SPDX ID '$spdxId' has to start with '$REF_PREFIX'."
        }

        require(checksums.any { it.algorithm == SpdxChecksum.Algorithm.SHA1 }) {
            "At least one SHA1 checksum must be provided."
        }

        require(copyrightText.isNotBlank()) { "The copyright text must not be blank." }

        require(filename.isNotBlank()) { "The filename must not be blank." }

        require(licenseConcluded.isSpdxExpressionOrNotPresent()) {
            "The license concluded must be either an SpdxExpression, 'NONE' or 'NOASSERTION', but was " +
                "$licenseConcluded."
        }

        // TODO: The check for [licenseInfoInFiles] can be made more strict, but the SPDX specification is not exact
        //       enough yet to do this safely.
        licenseInfoInFiles.filterNot { it.isSpdxExpressionOrNotPresent() }.let {
            require(it.isEmpty()) {
                "The entries in licenseInfoInFiles must each be either an SpdxExpression, 'NONE' or 'NOASSERTION', " +
                    "but found ${it.joinToString()}."
            }
        }
    }
}
