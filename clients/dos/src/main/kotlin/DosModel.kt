/*
 * Copyright (C) 2024 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.clients.dos

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject

import org.ossreviewtoolkit.model.config.PathExcludeReason

/** The JSON (de-)serializer to use for the DOS model. */
val JSON = Json { ignoreUnknownKeys = true }

@Serializable
data class UploadUrlRequestBody(
    /** The desired key name for the file to upload to S3. */
    val key: String
)

@Serializable
data class UploadUrlResponseBody(
    /** A flag to indicate whether requesting the upload URL was successful. */
    val success: Boolean,

    /** The pre-signed upload URL, or null on failure. */
    val presignedUrl: String? = null,

    /** The success or error message from the server, if any. */
    val message: String? = null
)

@Serializable
data class PackageInfo(
    /** The purl for the package (includes the VCS information). */
    val purl: String,

    /**
     * The declared license for the package, if any. TODO: Use SpdxExpression type instead of String when the
     * SpdxExpression class has been migrated to use kotlinx-serialization.
     */
    val declaredLicenseExpressionSPDX: String?
)

@Serializable
data class ScanResultsRequestBody(
    /**
     * The list of packages to get scan results for. In case multiple packages are provided, it is assumed that they all
     * refer to the same provenance (like a monorepo). If only some of the packages exist in the DOS database, new purl
     * bookmarks will be added for the missing packages (hence the need for the declared license here).
     */
    val packages: List<PackageInfo>
)

@Serializable
data class ScanResultsResponseBody(
    /** The state of the scan job. */
    val state: State,

    /** The list of purls that was originally requested. */
    val purls: List<String>? = null,

    /** The raw result from the scanner. Currently only ScanCode is supported. */
    val results: JsonObject? = null
) {
    @Serializable
    data class State(
        /** Named status of the scan job. One of: "no-results", "pending", "ready". */
        val status: String,

        /** The ID of the scan job. Only non-null of the job is "pending". */
        val jobId: String? = null
    )
}

@Serializable
data class JobRequestBody(
    /** The key of the previously uploaded ZIP file to scan. */
    val zipFileKey: String,

    /** The list of packages whose source code is contained in the ZIP file. */
    val packages: List<PackageInfo>
)

@Serializable
data class JobResponseBody(
    /** The ID of the scan job that was created, or null on error. */
    val scannerJobId: String? = null,

    /** The success or error message from the server, if any. */
    val message: String? = null
)

@Serializable
data class JobStateResponseBody(
    /** The state of the scan job. */
    val state: State
) {
    @Serializable
    data class State(
        /**
         * Named status of the scan job. One of: "created", "processing", "queued", "waiting", "active", "delayed",
         * "paused", "stuck", "resumed", "stalled", "savingResults", "completed", "failed".
         */
        val status: String? = null,

        /** The success or error message from the server, if any. */
        val message: String? = null
    )
}

@Serializable
data class PackageConfigurationRequestBody(
    /** The purl of the package to request configuration for. */
    val purl: String
)

@Serializable
data class PackageConfigurationResponseBody(
    /** The license conclusions for the file(s) in the package(s). */
    val licenseConclusions: List<LicenseConclusion>,

    /** The path exclusions for the file(s) in the package(s). */
    val pathExclusions: List<PathExclusion>
) {
    @Serializable
    data class LicenseConclusion(
        /** The path to the file the conclusion belongs to. */
        val path: String,

        /** The SPDX expression for the detected license of the whole file. */
        val detectedLicenseExpressionSPDX: String? = null,

        /** The SPDX expression for the concluded license of the whole file. */
        val concludedLicenseExpressionSPDX: String,

        /** The optional comment for the license conclusion. */
        val comment: String? = null
    )

    @Serializable
    data class PathExclusion(
        /** The path / file pattern for the path exclusion. */
        val pattern: String,

        /** The reason pattern for the path exclusion. */
        val reason: PathExcludeReason,

        /** The optional comment for the path exclusion. */
        val comment: String? = null
    )
}
