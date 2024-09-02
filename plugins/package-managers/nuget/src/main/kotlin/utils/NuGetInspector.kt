/*
 * Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.nuget.utils

import java.io.File

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.decodeFromStream

import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

private val json = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

internal object NuGetInspector : CommandLineTool {
    override fun command(workingDir: File?) = "nuget-inspector"

    /**
     * Run the nuget-inspector CLI tool on the project with the given [definitionFile]. The optional [nugetConfig] may
     * point to a configuration file that is not at the default location.
     */
    fun inspect(definitionFile: File, nugetConfig: File? = null): Result {
        val workingDir = definitionFile.parentFile
        val outputFile = createOrtTempFile(prefix = "nuget-inspector", suffix = ".json")

        val commandLineOptions = buildList<String> {
            add("--with-details")
            add("--verbose")
            add("--project-file")
            add(definitionFile.absolutePath)
            add("--json")
            add(outputFile.absolutePath)

            if (nugetConfig != null) {
                add("--nuget-config")
                if (nugetConfig.isAbsolute) {
                    add(nugetConfig.path)
                } else {
                    add(workingDir.resolve(nugetConfig).absolutePath)
                }
            }
        }

        return try {
            run(workingDir, *commandLineOptions.toTypedArray())
            outputFile.inputStream().use { json.decodeFromStream(it) }
        } finally {
            workingDir.resolve(".cache").safeDeleteRecursively()
            outputFile.parentFile.safeDeleteRecursively()
        }
    }

    @Serializable
    internal data class Result(
        val packages: List<PackageData>,
        val dependencies: List<PackageData>,
        val headers: List<Header>
    )

    @Serializable
    internal data class Header(
        val projectFramework: String,
        val errors: List<String>
    )

    @Serializable
    internal data class PackageData(
        val type: String,
        val namespace: String?,
        val name: String,
        val version: String?,
        val description: String,
        val parties: List<Party>,
        val homepageUrl: String?,
        val downloadUrl: String,
        val size: Long,
        val sha1: String?,
        val md5: String?,
        val sha256: String?,
        val sha512: String?,
        val codeViewUrl: String?,
        val vcsUrl: String?,
        val copyright: String?,
        val licenseExpression: String?,
        val declaredLicense: String?,
        val sourcePackages: List<String>,
        val repositoryHomepageUrl: String?,
        val repositoryDownloadUrl: String?,
        val apiDataUrl: String,
        val purl: String,
        val dependencies: List<PackageData>,
        val errors: List<String>,
        val warnings: List<String>
    )

    @Serializable
    internal class Party(
        val type: String,
        val role: String,
        val name: String?,
        val email: String?,
        val url: String?
    )
}
