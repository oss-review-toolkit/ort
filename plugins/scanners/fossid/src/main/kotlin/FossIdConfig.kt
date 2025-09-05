/*
 * Copyright (C) 2021 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.scanners.fossid

import org.ossreviewtoolkit.plugins.api.OrtPluginOption
import org.ossreviewtoolkit.plugins.api.Secret

/** A data class that holds the configuration options supported by the [FossId] scanner. */
data class FossIdConfig(
    /** The URL where the FossID service is running. */
    val serverUrl: String,

    /** The user to authenticate against the server. */
    val user: Secret,

    /** The API key to access the FossID server. */
    val apiKey: Secret,

    /** The name of the FossID project. If `null`, the name will be determined from the repository URL. */
    val projectName: String?,

    /** The pattern for scan names when scans are created on the FossID instance. If null, a default pattern is used. */
    val namingScanPattern: String?,

    /**
     * If true, the repository to scan will be cloned by ORT and an archive will be sent to FossID for scanning. In this
     * mode, no credentials are passed to FossID, and FossID does not access the repository directly.
     */
    @OrtPluginOption(defaultValue = "false")
    val isArchiveMode: Boolean,

    /**
     * When set to false, ORT does not wait for repositories to be downloaded nor scans to be completed. As a
     * consequence, scan results won't be available in the ORT result.
     */
    @OrtPluginOption(defaultValue = "true")
    val waitForResult: Boolean,

    /** Flag whether failed scans should be kept. */
    @OrtPluginOption(defaultValue = "false")
    val keepFailedScans: Boolean,

    /** If set, ORT will create delta scans. When only changes in a repository need to be scanned, delta scans reuse the
     *  identifications of the latest scan on this repository to reduce the number of findings. If *deltaScans* is set
     *  and no scan exists yet, an initial scan called "origin" scan will be created.
     */
    @OrtPluginOption(defaultValue = "false")
    val deltaScans: Boolean,

    /**
     * This setting can be used to limit the number of delta scans to keep for a given repository. So if another delta
     * scan is created, older delta scans are deleted until this number is reached. If unspecified, no limit is enforced
     * on the number of delta scans to keep. This property is evaluated only if delta scans are enabled.
     */
    @OrtPluginOption(defaultValue = Int.MAX_VALUE.toString())
    val deltaScanLimit: Int,

    /**
     * Configure to automatically detect license declarations. Uses the `auto_identification_detect_copyright` setting.
     */
    @OrtPluginOption(defaultValue = "false")
    val detectLicenseDeclarations: Boolean,

    /** Configure to detect copyright statements. Uses the `auto_identification_detect_copyright` setting. */
    @OrtPluginOption(defaultValue = "false")
    val detectCopyrightStatements: Boolean,

    /** Timeout in minutes for communication with FossID. */
    @OrtPluginOption(defaultValue = "60")
    val timeout: Int,

    /** Whether matched lines of snippets are to be fetched. */
    @OrtPluginOption(defaultValue = "false")
    val fetchSnippetMatchedLines: Boolean,

    /** A limit on the amount of snippets to fetch. **/
    @OrtPluginOption(defaultValue = "500")
    val snippetsLimit: Int,

    /** The sensitivity of the scan. */
    @OrtPluginOption(defaultValue = "10")
    val sensitivity: Int,

    /**
     * If true, the FossID scanner will log all requests and responses, omitting sensitive information such as
     * credentials. This is useful for debugging mapping errors.
     */
    @OrtPluginOption(defaultValue = "false")
    val logRequests: Boolean,

    /**
     * A comma-separated list of URL mappings that allow transforming the VCS URLs of repositories before they are
     * passed to the FossID service. This may be necessary if FossID uses a different mechanism to clone a repository,
     * e.g., via SSH instead of HTTP. Their values define the mapping to be applied consisting of two parts separated by
     * the string " -> ":
     * * A regular expression to match the repository URL.
     * * The replacement to be used for this repository URL. It can access the capture groups defined by the regular
     *   expression, so that rather flexible transformations can be achieved. In addition, it can contain the variables
     *  "#user" and "#password" that are replaced by the credentials known for the target host.
     *
     * The example
     *
     * `mapExampleRepo = https://my-repo.example.org(?<repoPath>.*) -> ssh://my-mapped-repo.example.org${repoPath}`
     *
     * would change the scheme from "https" to "ssh" and the host name for all repositories hosted on
     * "my-repo.example.org". With
     *
     * `mapAddCredentials =
     *   (?<scheme>)://(?<host>)(?<port>:\\d+)?(?<repoPath>.*) -> ${scheme}://#user:#password@${host}${port}${repoPath}`
     *
     * every repository URL would be added credentials. Mappings are applied in the order they are defined.
     */
    val urlMappings: String?,

    /** Whether to write scan results to the storage. */
    @OrtPluginOption(defaultValue = "true")
    val writeToStorage: Boolean,

    /** Treat pending identifications as errors instead of hints. */
    @OrtPluginOption(defaultValue = "false")
    val treatPendingIdentificationsAsError: Boolean,

    /**
     * Whether to delete uploaded content after scan completion. When set to false, archives remain on the
     * FossID server, which can help diagnose archive upload issues.
     */
    @OrtPluginOption(defaultValue = "true")
    val deleteUploadedArchiveAfterScan: Boolean
) {
    init {
        require(deltaScanLimit > 0) {
            "deltaScanLimit must be > 0, current value is $deltaScanLimit."
        }

        require(sensitivity in 0..20) {
            "Sensitivity must be between 0 and 20, current value is $sensitivity."
        }
    }

    /**
     * Create a [FossIdNamingProvider] helper object based on the configuration stored in this object.
     */
    fun createNamingProvider() = FossIdNamingProvider(namingScanPattern, projectName)

    /**
     * Create a [FossIdUrlProvider] helper object based on the configuration stored in this object.
     */
    fun createUrlProvider() = FossIdUrlProvider.create(urlMappings?.split(',').orEmpty())
}
