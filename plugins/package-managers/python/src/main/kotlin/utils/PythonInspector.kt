/*
 * Copyright (C) 2022 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
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

package org.ossreviewtoolkit.plugins.packagemanagers.python.utils

import java.io.File

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy
import kotlinx.serialization.json.decodeFromStream

import org.ossreviewtoolkit.utils.common.CommandLineTool
import org.ossreviewtoolkit.utils.common.safeDeleteRecursively
import org.ossreviewtoolkit.utils.ort.createOrtTempFile

import org.semver4j.RangesList
import org.semver4j.RangesListFactory

private val json = Json {
    ignoreUnknownKeys = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}

internal object PythonInspector : CommandLineTool {
    override fun command(workingDir: File?) = "python-inspector"

    override fun transformVersion(output: String) = output.removePrefix("Python-inspector version: ")

    override fun getVersionRequirement(): RangesList = RangesListFactory.create("[0.9.2,)")

    fun inspect(
        workingDir: File,
        definitionFile: File,
        pythonVersion: String,
        operatingSystem: String,
        analyzeSetupPyInsecurely: Boolean
    ): Result {
        val outputFile = createOrtTempFile(prefix = "python-inspector", suffix = ".json")

        val commandLineOptions = buildList {
            add("--python-version")
            add(pythonVersion)

            add("--operating-system")
            add(operatingSystem)

            add("--json-pdt")
            add(outputFile.absolutePath)

            if (analyzeSetupPyInsecurely) {
                add("--analyze-setup-py-insecurely")
            }

            if (definitionFile.name == "setup.py") {
                add("--setup-py")
            } else {
                add("--requirement")
            }

            add(definitionFile.absolutePath)

            if (definitionFile.name != "setup.py") {
                // If a setup.py file exists, add it to the analysis to capture additional project metadata.
                val setupFile = definitionFile.resolveSibling("setup.py")
                if (setupFile.isFile) {
                    add("--setup-py")
                    add(setupFile.absolutePath)
                }
            }

            add("--verbose")
        }

        return try {
            run(workingDir, *commandLineOptions.toTypedArray())
            outputFile.inputStream().use { json.decodeFromStream(it) }
        } finally {
            outputFile.parentFile.safeDeleteRecursively()
        }
    }

    @Serializable
    internal data class Result(
        @SerialName("files") val projects: List<Project>,
        val resolvedDependenciesGraph: List<ResolvedDependency>,
        val packages: List<Package>
    )

    @Serializable
    internal data class Project(
        val path: String,
        val packageData: List<PackageData>
    )

    @Serializable
    internal data class PackageData(
        val namespace: String?,
        val name: String?,
        val version: String?,
        val description: String?,
        val parties: List<Party>,
        val homepageUrl: String?,
        val declaredLicense: DeclaredLicense?
    )

    @Serializable
    internal data class DeclaredLicense(
        val license: String? = null,
        val classifiers: List<String> = emptyList()
    )

    @Serializable
    internal data class ResolvedDependency(
        val key: String,
        val packageName: String,
        val installedVersion: String,
        val dependencies: List<ResolvedDependency>
    )

    @Serializable
    internal data class Package(
        val type: String,
        val namespace: String?,
        val name: String,
        val version: String,
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
        val declaredLicense: DeclaredLicense?,
        val sourcePackages: List<String>,
        val repositoryHomepageUrl: String?,
        val repositoryDownloadUrl: String?,
        val apiDataUrl: String,
        val purl: String
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
